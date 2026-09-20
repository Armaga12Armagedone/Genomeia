package io.github.some_example_name.old.editor.baking

import com.badlogic.gdx.Gdx
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import java.util.Locale

/**
 * Выгрузка топологии выращенного организма в текстовый файл.
 *
 * ЗАЧЕМ
 * -----
 * Чтобы прогнать настоящее тело в отдельном стенде физики (демка XPBD + shape matching
 * в модуле lwjgl3) и увидеть на нём то, что на синтетической сетке не проверить:
 * сколько треугольников реально даёт смежность генома, во что складываются кости и мышцы,
 * как ведут себя рваные края и длинные отростки.
 *
 * ГДЕ ВЫЗЫВАЕТСЯ
 * --------------
 * В том же месте, что и запекание раскладки — при сохранении генома в редакторе. К этому
 * моменту тело уже выращено целиком и отработало последний тик, поэтому координаты здесь
 * это реальная поза покоя, а не то, что выведено из длин связей.
 *
 * ФОРМАТ
 * ------
 * Построчный текст, всё лишнее — комментарии с '#'. Идентификатор клетки это
 * cellGenomeId: он устойчив внутри генома, в отличие от индекса в сущностях.
 *
 *   P <genomeId> <x> <y> <radius> <cellType> <bone> <muscle>
 *   L <genomeIdA> <genomeIdB>
 *
 * bone и muscle — 0/1, продублированы из cellType намеренно: стенду не нужно знать
 * нумерацию типов клеток движка, а глазами файл читается сразу.
 *
 * ЧЕГО ЗДЕСЬ НЕТ И ПОЧЕМУ
 * -----------------------
 * Ни треугольников, ни кластеров, ни порядка слотов. Всё это ВЫВОДИМО из вершин и связей,
 * и выводить его должен стенд — иначе он проверял бы не топологию генома, а мою выгрузку.
 * Треугольники стенд соберёт той же логикой, что RCMSort.collectTriangles (тройка взаимно
 * связанных клеток), кластеры костей и мышц — как связные компоненты одноимённых клеток.
 */
class BodyExport(
    private val cellEntity: CellEntity,
    private val linkEntity: LinkEntity,
) {

    companion object {
        /** Идентификаторы типов клеток из CellListBuilder.instances. */
        private const val CELL_TYPE_BONE = 2
        private const val CELL_TYPE_MUSCLE = 5

        private const val FILE_NAME = "body-export.txt"
    }

    /**
     * Собирает выгрузку для организма [organIndex] (в редакторе он всегда 0).
     *
     * Возвращает содержимое файла, либо null если тела нет. Запись на диск делает
     * [exportToFile], чтобы текст можно было получить и без файловой системы.
     */
    fun build(organIndex: Int = 0): String? {
        val alive = cellEntity.aliveList
        val sb = StringBuilder(1 shl 16)

        var cells = 0
        var bones = 0
        var muscles = 0
        val body = StringBuilder(1 shl 16)

        for (i in 0 until alive.size) {
            val cellIndex = alive.getInt(i)
            if (cellEntity.organIndex[cellIndex] != organIndex) continue

            val genomeId = cellEntity.cellGenomeId[cellIndex]
            // Клетка без идентификатора не воспроизводима — по ней нельзя сослаться
            // из связи, поэтому она не попадает и в выгрузку. Тот же критерий, что
            // в RCMSort.collectCellGenomeIds.
            if (genomeId == -1) continue

            val type = cellEntity.cellType[cellIndex].toInt()
            val isBone = if (type == CELL_TYPE_BONE) 1 else 0
            val isMuscle = if (type == CELL_TYPE_MUSCLE) 1 else 0
            bones += isBone
            muscles += isMuscle
            cells++

            body.append(
                String.format(
                    Locale.ROOT,
                    "P %d %.4f %.4f %.4f %d %d %d%n",
                    genomeId,
                    cellEntity.getX(cellIndex),
                    cellEntity.getY(cellIndex),
                    cellEntity.getRadius(cellIndex),
                    type,
                    isBone,
                    isMuscle
                )
            )
        }

        if (cells == 0) return null

        // Связи собираются ПОСЛЕ вершин и с теми же проверками: обе клетки живы, обе
        // из этого организма, у обеих есть genomeId. Иначе в файл попадёт ссылка на
        // вершину, которой в нём нет.
        var links = 0
        val seen = HashSet<Long>()
        val linksText = StringBuilder(1 shl 16)
        val aliveLinks = linkEntity.aliveList

        for (i in 0 until aliveLinks.size) {
            val linkIndex = aliveLinks.getInt(i)
            val cellA = linkEntity.links1[linkIndex]
            val cellB = linkEntity.links2[linkIndex]
            if (cellA == -1 || cellB == -1) continue
            if (!cellEntity.isAlive[cellA] || !cellEntity.isAlive[cellB]) continue
            if (cellEntity.organIndex[cellA] != organIndex) continue
            if (cellEntity.organIndex[cellB] != organIndex) continue

            val idA = cellEntity.cellGenomeId[cellA]
            val idB = cellEntity.cellGenomeId[cellB]
            if (idA == -1 || idB == -1 || idA == idB) continue

            // Дубликаты между одной парой в движке встречаются (пружина плюс
            // дополнительная связь), а стенду нужен простой граф: две связи на одну пару
            // дали бы вдвое большую жёсткость на этом ребре и перекос материала.
            val lo = minOf(idA, idB).toLong()
            val hi = maxOf(idA, idB).toLong()
            if (!seen.add((lo shl 32) or hi)) continue

            linksText.append(String.format(Locale.ROOT, "L %d %d%n", idA, idB))
            links++
        }

        sb.append("# genomeia body export").append('\n')
        sb.append("# cells=").append(cells)
            .append(" links=").append(links)
            .append(" bone=").append(bones)
            .append(" muscle=").append(muscles).append('\n')
        sb.append("# P <genomeId> <x> <y> <radius> <cellType> <bone> <muscle>").append('\n')
        sb.append("# L <genomeIdA> <genomeIdB>").append('\n')
        sb.append(body)
        sb.append(linksText)
        return sb.toString()
    }

    /**
     * Пишет выгрузку в локальный файл и возвращает абсолютный путь, либо null.
     *
     * Local, а не Absolute: на десктопе это рабочая директория запуска, её видно сразу,
     * а на других платформах путь останется валидным.
     */
    fun exportToFile(organIndex: Int = 0): String? {
        val text = build(organIndex) ?: run {
            Gdx.app.log("BodyExport", "body is empty, nothing to export")
            return null
        }
        val handle = Gdx.files.local(FILE_NAME)
        handle.writeString(text, false, "UTF-8")
        val path = handle.file().absolutePath
        Gdx.app.log("BodyExport", "выгружено в $path\n" + text.lineSequence().take(2).joinToString("\n"))
        return path
    }
}
