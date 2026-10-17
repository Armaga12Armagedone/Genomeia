package io.github.some_example_name.old.systems.genomics.library

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import io.github.some_example_name.old.systems.genomics.genome.Genome
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage
import io.github.some_example_name.old.systems.genomics.genome.LegacyTypeIds
import io.github.some_example_name.old.systems.genomics.genome.LinkData

/**
 * Чтение генома в старом формате JSON — том, в котором геномы хранились до .genome
 * (коммит 01de851, июль 2026). Файлы в нём остались у игроков и в ассетах прошлых версий.
 *
 * ЗАЧЕМ ОБХОД ДЕРЕВА, А НЕ Json.fromJson
 * --------------------------------------
 * Старый код читал файл отражением libGDX в классы CreatureJsonRead и соседей. Отражение
 * зависит от имён полей наших классов, а их на Android переименовывает минификатор; на вебе
 * (TeaVM) отражения почти нет. Обход JsonValue от этого свободен и работает без Gdx, то есть
 * проверяется обычными тестами :core.
 *
 * ЧТО ВСТРЕЧАЕТСЯ В ФАЙЛАХ
 * ------------------------
 * Писатель за время жизни формата менялся, и в файлах лежат оба варианта:
 *  - цвет строкой "colorHsv": "rrggbbaa" (последний писатель) или объектом "color": {r,g,b,a}
 *    (Json libGDX писал Color как есть; так сохранён, например, «Burdock leaf»);
 *  - "class": "java.util.LinkedHashMap" внутри словарей — метка типа от libGDX, не ключ;
 *  - явные null у необязательных полей;
 *  - id строкой ("1"), у мутаций "-1".
 *
 * ПЕРЕВОД — ровно как в старом чтении (GenomeJsonRead.kt до 01de851):
 *  - длины связей и lengthDirected в JSON в пикселях старой сетки, в геноме — в клетках: / 40;
 *  - связь нейронная, если у неё есть directedNeuronLink (isNeuronal писался не всегда и не читался);
 *  - порядок клеток в стадии и связей в клетке — порядок в файле: от него зависит, в каком
 *    порядке клетки делятся в тике, а значит и индексы.
 *
 * ЧЕГО НЕТ В ГЕНОМЕ
 * -----------------
 *  - specialData.attachedKey (клавиша клетки управления): в .genome такого поля нет с момента
 *    перехода, клетка управления не работала уже тогда;
 *  - indexOfNew и startDirectionId: их не читал и старый код.
 *
 * Номера типов клеток и формул в JSON — те же старые номера, что и в .genome формата 1:
 * они переводятся в ключи по LegacyTypeIds. Типы 16 и 21 в JSON были неработающими
 * заглушками (NonWorkingCell1/2), теперь под ними Synapse и Fin.
 */
object LegacyGenomeJson {

    /** Версия, которой помечается геном из JSON. Её же ставил переходный код в 01de851. */
    const val VERSION = 23

    /** Сколько пикселей старой сетки в одной клетке: в JSON длины хранились в пикселях. */
    private const val PIXELS_PER_CELL = 40f

    /** Метка типа словаря, которую Json libGDX вписывал среди ключей. */
    private const val CLASS_TAG = "class"

    /**
     * Геном из текста JSON. [fallbackName] — если в файле нет имени.
     *
     * @throws IllegalArgumentException это не геном в старом формате.
     */
    fun parse(text: String, fallbackName: String): Genome {
        val root = try {
            JsonReader().parse(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("не JSON: ${e.message}", e)
        }
        require(root != null && root.isObject) { "не JSON-объект" }

        val stages = root.get("genomeStageInstruction")
        require(stages != null && stages.isArray) { "нет genomeStageInstruction — это не геном" }
        require(stages.size > 0) { "в геноме нет ни одной стадии" }

        val stageInstruction = ArrayList<GenomeStage>(stages.size)
        for (stage in stages) {
            stageInstruction += GenomeStage(cellActions = readCellActions(stage.opt("cellActions")))
        }

        return Genome(
            version = VERSION,
            name = root.opt("name")?.asString()?.takeIf { it.isNotBlank() } ?: fallbackName,
            stageInstruction = stageInstruction
        )
    }

    /**
     * Похоже ли содержимое на JSON — для понятной ошибки, когда игрок выбрал не тот файл:
     * первым значащим символом идёт '{'. Пропускается BOM, его пишут редакторы Windows.
     */
    fun looksLikeJson(bytes: ByteArray): Boolean {
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c == '{'.code) return true
            if (c == ' '.code || c == '\t'.code || c == '\n'.code || c == '\r'.code) continue
            if (c == 0xEF || c == 0xBB || c == 0xBF) continue
            return false
        }
        return false
    }

    private fun readCellActions(map: JsonValue?): Map<Int, CellAction> {
        val result = LinkedHashMap<Int, CellAction>()
        if (map == null) return result
        for (entry in map) {
            if (entry.name == CLASS_TAG) continue
            if (entry.isNull) continue
            result[key(entry)] = CellAction(
                divide = readAction(entry.opt("divide")),
                mutate = readAction(entry.opt("mutate"))
            )
        }
        return result
    }

    private fun readAction(action: JsonValue?): Action? {
        if (action == null) return null
        return Action(
            id = action.opt("id")?.let(::readId) ?: -1,
            angle = action.float("angle"),
            cellType = action.int("cellType")?.let(LegacyTypeIds::cellType),
            physicalLink = readLinks(action.opt("physicalLink")),
            // Объект color у старого чтения шёл первым, colorHsv — запасным.
            color = readColor(action.opt("color")) ?: readColor(action.opt("colorHsv")),
            radius = action.float("radius"),
            angleDirected = action.float("angleDirected"),
            funActivation = action.int("funActivation")?.let(LegacyTypeIds::formula),
            a = action.float("a"),
            b = action.float("b"),
            c = action.float("c"),
            isSum = action.opt("isSum")?.asBoolean(),
            colorRecognition = action.int("colorRecognition"),
            lengthDirected = action.float("lengthDirected")?.div(PIXELS_PER_CELL),
            pheromoneType = action.int("pheromoneType")
        )
    }

    private fun readLinks(map: JsonValue?): Map<Int, LinkData?> {
        val result = LinkedHashMap<Int, LinkData?>()
        if (map == null) return result
        for (entry in map) {
            if (entry.name == CLASS_TAG) continue
            result[key(entry)] = if (entry.isNull) null else readLink(entry)
        }
        return result
    }

    private fun readLink(link: JsonValue): LinkData {
        val directedNeuronLink = link.int("directedNeuronLink")
        return LinkData(
            length = link.float("length")?.div(PIXELS_PER_CELL),
            isNeuronal = directedNeuronLink != null,
            color = readColor(link.opt("color")),
            weight = link.float("weight"),
            directedNeuronLink = directedNeuronLink
        )
    }

    /** Строка "rrggbbaa" (как писал Color.toString) или объект {r, g, b, a} (как писал Json libGDX). */
    private fun readColor(value: JsonValue?): Color? = when {
        value == null -> null
        value.isString -> Color.valueOf(value.asString())
        value.isObject -> Color(
            value.getFloat("r", 0f),
            value.getFloat("g", 0f),
            value.getFloat("b", 0f),
            value.getFloat("a", 1f)
        )
        else -> throw IllegalArgumentException("цвет ни строкой, ни объектом: $value")
    }

    /** id писался строкой, но руками правленные файлы бывают и с числом. */
    private fun readId(value: JsonValue): Int? =
        if (value.isNumber) value.asInt() else value.asString().trim().toIntOrNull()

    private fun key(entry: JsonValue): Int =
        entry.name?.trim()?.toIntOrNull()
            ?: throw IllegalArgumentException("ключ '${entry.name}' — не номер клетки")

    private fun JsonValue.opt(name: String): JsonValue? = get(name)?.takeUnless { it.isNull }

    private fun JsonValue.float(name: String): Float? = opt(name)?.asFloat()

    private fun JsonValue.int(name: String): Int? = opt(name)?.asInt()
}
