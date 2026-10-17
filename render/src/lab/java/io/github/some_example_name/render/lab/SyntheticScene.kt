package io.github.some_example_name.render.lab

import io.github.some_example_name.render.pack.CellInstanceBuffer
import io.github.some_example_name.render.pack.OverlayInstanceBuffer
import io.github.some_example_name.render.pack.PheromoneInstanceBuffer
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Random

/**
 * Сцены, собранные из ничего — чтобы стенд был полезен сразу, без дампа из игры.
 *
 * Их четыре, см. [Kind]. Выбираются свойством -Dgenomeia.synthetic=layers|body|dense|organism
 * или клавишей N в стенде.
 *
 * [Kind.LAYERS] показывает то, что в живом кадре ещё поди поймай:
 *  - ВСЕ слои TextureArray разом, по сетке — видно, какой тип чем рисуется;
 *  - развёртку радиуса по всему диапазону, который влезает в байт (0.05…0.75);
 *  - развёртку энергии — это радиус чёрной точки внутри клетки;
 *  - развёртку угла на полный оборот — видно, как шейдер крутит текстуру;
 *  - несколько пятен феромона.
 *
 * Числа тут подобраны так, чтобы всё было в кадре при [ZOOM], и ничего больше не значат.
 */
object SyntheticScene {

    enum class Kind {
        /** Все слои текстур и развёртки радиуса, энергии и угла — см. шапку. */
        LAYERS,

        /**
         * Небольшое тело из клеток вплотную плюс куча субстанций рядом — то, на чём видно
         * ностальгический режим: границы Вороного, ядра у клеток и их отсутствие у
         * не-клеток. Расстановка срисована с кадра первых версий игры.
         */
        BODY,

        /**
         * Нагрузочная: двадцать тысяч частиц решёткой внахлёст, экран закрыт целиком.
         * На ней замеряют стоимость режимов — и вершинную работу (все инстансы), и
         * фрагментную (каждый пиксель экрана под клеткой, с перекрытиями).
         */
        DENSE,

        /**
         * Организм для отладочного режима: клетки цветов своих типов (мышцы, хвосты, глаз,
         * нейроны, хамелеон), нейросвязи с сигналами всех знаков, стрелки направлений, луч
         * глаза и кольцо феромона — всё, что рисует оверлей. Цвета — defaultColor из :core,
         * переписанные числами: стенд о клетках ничего не знает.
         */
        ORGANISM
    }

    /** Сцена вместе с камерой, на которую она рассчитана. */
    class Scene(
        val cells: ByteBuffer,
        val pheromones: ByteBuffer,
        val centerX: Float,
        val centerY: Float,
        val zoom: Float,
        /** Оверлей отладочного режима; у сцен без него — пустой. */
        val overlay: ByteBuffer = emptyOverlay()
    )

    /** Совпадает с PheromonesManager.K/P в :core. Дублируется намеренно: см. RenderFrame. */
    const val PHEROMONE_K = 0.4f
    const val PHEROMONE_P = 0.01f

    private const val SPACING = 1.6f
    private const val SWEEP_COUNT = 12

    /** Видно примерно 24 мира по ширине окна 1280 px. */
    private const val LAYERS_ZOOM = 0.019f

    /**
     * [nonCellLayer] — слой текстуры для не-клеток: в игре это not_cell.png, последний
     * слой. У стенда порядок слоёв свой (по алфавиту), поэтому номер передаётся снаружи.
     */
    fun build(kind: Kind, layerCount: Int, nonCellLayer: Int): Scene = when (kind) {
        Kind.LAYERS -> layers(layerCount)
        Kind.BODY -> body(layerCount, nonCellLayer)
        Kind.DENSE -> dense(layerCount, nonCellLayer)
        Kind.ORGANISM -> organism(layerCount, nonCellLayer)
    }

    private fun layers(layerCount: Int): Scene {
        val cells = CellInstanceBuffer()
        cells.begin()

        val columns = ceil(sqrt(layerCount.toFloat())).toInt().coerceAtLeast(1)
        val rows = ceil(layerCount / columns.toFloat()).toInt()

        // ===== сетка типов: каждый слой TextureArray один раз =====
        for (layer in 0 until layerCount) {
            val column = layer % columns
            val row = layer / columns
            val angle = layer * 0.37f
            cells.putCell(
                x = column * SPACING,
                y = -row * SPACING,
                color = rgba(220, 200, 170),
                angleCos = cos(angle),
                angleSin = sin(angle),
                radius = 0.55f,
                energy = 0f,
                cellType = layer,
                noiseSeed = layer * 977
            )
        }

        val sweepY = -rows * SPACING - SPACING

        // ===== развёртка радиуса: от минимума до максимума, что влезает в байт =====
        repeat(SWEEP_COUNT) { i ->
            val t = i / (SWEEP_COUNT - 1f)
            cells.putCell(
                x = i * SPACING,
                y = sweepY,
                color = rgba(200, 120, 120),
                angleCos = 1f,
                angleSin = 0f,
                radius = CellInstanceBuffer.RADIUS_MIN + t * CellInstanceBuffer.RADIUS_SPAN,
                energy = 0f,
                cellType = 0,
                noiseSeed = 1000 + i
            )
        }

        // ===== развёртка энергии: радиус чёрной точки внутри клетки =====
        repeat(SWEEP_COUNT) { i ->
            val t = i / (SWEEP_COUNT - 1f)
            cells.putCell(
                x = i * SPACING,
                y = sweepY - SPACING,
                color = rgba(140, 190, 140),
                angleCos = 1f,
                angleSin = 0f,
                radius = 0.6f,
                energy = t * CellInstanceBuffer.ENERGY_SPAN,
                cellType = 0,
                noiseSeed = 2000 + i
            )
        }

        // ===== развёртка угла: полный оборот =====
        repeat(SWEEP_COUNT) { i ->
            val angle = (i / SWEEP_COUNT.toFloat()) * 2f * Math.PI.toFloat()
            cells.putCell(
                x = i * SPACING,
                y = sweepY - 2 * SPACING,
                color = rgba(150, 160, 220),
                angleCos = cos(angle),
                angleSin = sin(angle),
                radius = 0.6f,
                energy = 0f,
                cellType = 1.coerceAtMost(layerCount - 1),
                // Ключ шума один на всю строку: доворот от шума одинаковый, поэтому видно
                // именно вращение от угла, а не мешанину из двух источников сразу.
                noiseSeed = 4242
            )
        }

        // Ширину берём по САМОЙ широкой части, а не по сетке типов: развёртки длиннее её,
        // и если центрироваться по сетке, они уезжают за правый край экрана.
        val width = maxOf((columns - 1) * SPACING, (SWEEP_COUNT - 1) * SPACING)
        val height = sweepY - 2 * SPACING
        val centerX = width / 2f
        val centerY = height / 2f

        // ===== феромоны =====
        //
        // Пятна получаются большими и бледными, и это не подбор чисел, а свойство самой
        // формулы: радиус растёт как (a/P - 1)/K, а прозрачность в центре равна a. При
        // P = 0.01 радиус в пару миров означает a ≈ 0.026, то есть альфу 2% — на экране
        // ничего. Заметное пятно неизбежно широкое. Здесь взяты значения, при которых
        // видно и само пятно, и его спад.
        val pheromones = PheromoneInstanceBuffer()
        pheromones.begin()
        pheromones.put(x = centerX, y = centerY, a = 0.25f, color = rgba(90, 200, 255))
        pheromones.put(x = centerX + 7f, y = centerY - 3f, a = 0.5f, color = rgba(255, 140, 90))

        return Scene(
            cells = cells.end(),
            pheromones = pheromones.end(),
            centerX = centerX,
            centerY = centerY,
            zoom = LAYERS_ZOOM
        )
    }

    /**
     * Клетка тела: x, y, радиус, цвет и энергия.
     *
     * Энергия — это радиус ядра в ностальгическом режиме. У большинства клеток 5, полный
     * запас обычной клетки; у пары — меньше и больше, чтобы было видно, как ядро от неё
     * зависит.
     */
    private class BodyCell(
        val x: Float,
        val y: Float,
        val radius: Float,
        val color: Int,
        val energy: Float = 5f
    )

    private fun body(layerCount: Int, nonCellLayer: Int): Scene {
        val cells = CellInstanceBuffer()
        cells.begin()

        // Тело: две колонки крупных клеток по бокам и узкая колонка мелких посередине —
        // клетки стоят внахлёст, расстояние между центрами заметно меньше двух радиусов.
        val big = 0.45f
        val small = 0.33f
        val organism = listOf(
            BodyCell(0.00f, 0.90f, big, rgba(196, 0, 150), energy = 10f),
            BodyCell(-0.57f, 0.53f, big, rgba(255, 192, 210)),
            BodyCell(0.58f, 0.49f, big, rgba(255, 192, 210)),
            BodyCell(-0.01f, 0.23f, small, rgba(240, 105, 180)),
            BodyCell(-0.55f, -0.03f, big, rgba(0, 125, 0), energy = 2.5f),
            BodyCell(0.55f, -0.01f, big, rgba(40, 140, 90)),
            BodyCell(-0.01f, -0.38f, small, rgba(240, 105, 180)),
            BodyCell(-0.52f, -0.71f, big, rgba(40, 140, 40)),
            BodyCell(0.59f, -0.68f, big, rgba(55, 205, 55)),
            BodyCell(0.02f, -1.04f, big, rgba(40, 150, 50), energy = 0f)
        )
        organism.forEachIndexed { i, cell ->
            cells.putCell(
                x = cell.x,
                y = cell.y,
                color = cell.color,
                angleCos = 1f,
                angleSin = 0f,
                radius = cell.radius,
                energy = cell.energy,
                cellType = i % layerCount,
                noiseSeed = 100 + i
            )
        }

        // Субстанции: плотная куча слева, решёткой с разбросом — как еда у края экрана.
        val substance = intArrayOf(
            rgba(232, 130, 40), rgba(180, 92, 20), rgba(190, 40, 50),
            rgba(222, 152, 72), rgba(200, 170, 176), rgba(170, 70, 30)
        )
        val random = Random(7)
        var seed = 1000
        for (column in 0 until 4) {
            for (row in 0 until 12) {
                val x = -4.6f + column * 0.68f + (row % 2) * 0.34f + random.nextFloat() * 0.12f
                val y = -3.6f + row * 0.62f + random.nextFloat() * 0.12f
                cells.putCell(
                    x = x,
                    y = y,
                    color = substance[random.nextInt(substance.size)],
                    angleCos = 1f,
                    angleSin = 0f,
                    radius = 0.36f + random.nextFloat() * 0.1f,
                    energy = 0f,
                    cellType = nonCellLayer,
                    noiseSeed = seed++,
                    isCell = false
                )
            }
        }

        return Scene(
            cells = cells.end(),
            pheromones = emptyPheromones(),
            centerX = -1.4f,
            centerY = -0.1f,
            zoom = 0.0072f
        )
    }

    private const val DENSE_COLUMNS = 200
    private const val DENSE_ROWS = 100
    private const val DENSE_SPACING = 0.8f

    private fun dense(layerCount: Int, nonCellLayer: Int): Scene {
        val cells = CellInstanceBuffer()
        cells.begin(DENSE_COLUMNS * DENSE_ROWS)

        val random = Random(11)
        var seed = 0
        for (row in 0 until DENSE_ROWS) {
            for (column in 0 until DENSE_COLUMNS) {
                // Каждая пятая — не-клетка, как субстанции в живом мире.
                val isCell = random.nextInt(5) != 0
                val x = column * DENSE_SPACING + (row % 2) * DENSE_SPACING * 0.5f +
                    (random.nextFloat() - 0.5f) * 0.2f
                val y = row * DENSE_SPACING * 0.87f + (random.nextFloat() - 0.5f) * 0.2f
                val angle = random.nextFloat() * 2f * Math.PI.toFloat()
                cells.putCell(
                    x = x,
                    y = y,
                    color = rgba(
                        60 + random.nextInt(190),
                        60 + random.nextInt(190),
                        60 + random.nextInt(190)
                    ),
                    angleCos = cos(angle),
                    angleSin = sin(angle),
                    // Радиус больше половины шага: соседи заходят друг на друга, как в теле.
                    radius = 0.42f + random.nextFloat() * 0.18f,
                    energy = random.nextFloat() * CellInstanceBuffer.ENERGY_SPAN,
                    cellType = if (isCell) random.nextInt(layerCount) else nonCellLayer,
                    noiseSeed = seed++ and 0xFFFF,
                    isCell = isCell
                )
            }
        }

        // Камера смотрит в середину решётки: экран закрыт целиком, а примерно две трети
        // инстансов вне кадра — их вершины всё равно считаются, как в большом мире.
        return Scene(
            cells = cells.end(),
            pheromones = emptyPheromones(),
            centerX = DENSE_COLUMNS * DENSE_SPACING / 2f,
            centerY = DENSE_ROWS * DENSE_SPACING * 0.87f / 2f,
            zoom = 0.06f
        )
    }

    private fun emptyPheromones(): ByteBuffer =
        PheromoneInstanceBuffer().apply { begin() }.end()

    private fun emptyOverlay(): ByteBuffer =
        OverlayInstanceBuffer(initialCapacity = 1).apply { begin() }.end()

    // defaultColor клеток из :core (colors.kt) — числами.
    private val MUSCLE = rgba(205, 0, 0)          // redColors[3]
    private val TAIL = rgba(0, 0, 255)            // blueColors[0]
    private val EYE = rgba(173, 216, 230)         // skyBlueColors[2]
    private val NEURON = rgba(255, 192, 203)      // pinkColors[0]
    private val BONE = rgba(255, 255, 255)        // whiteColors[0]
    private val FAT = rgba(255, 215, 0)           // yellowColors[0]
    private val LEAF = rgba(34, 139, 34)          // leafColors[0]
    private val FIN = rgba(135, 206, 235)         // skyBlueColors[1]

    /** Клетка организма: место в гексагональной решётке, цвет, направление. */
    private class OrganismCell(
        val column: Int,
        val row: Int,
        val color: Int,
        val angle: Float = 0f,
        val spiral: Boolean = false,
        val energy: Float = 5f,
        val directed: Boolean = false
    )

    private const val ORGANISM_STEP = 0.82f
    private const val ORGANISM_RADIUS = 0.5f

    private fun organismX(column: Int, row: Int) =
        column * ORGANISM_STEP + (if (row % 2 != 0) ORGANISM_STEP * 0.5f else 0f)

    private fun organismY(row: Int) = row * ORGANISM_STEP * 0.866f

    /** Мышца, затемнённая сжатием [contraction] 0..1 — так её красит :core в отладке. */
    private fun contracted(contraction: Float): Int {
        val k = 1f - 0.55f * contraction
        return rgba((205 * k).toInt(), 0, 0)
    }

    private fun organism(layerCount: Int, nonCellLayer: Int): Scene {
        val pi = Math.PI.toFloat()
        val body = listOf(
            // Хвост сзади: стрелки смотрят назад.
            OrganismCell(0, 0, TAIL, angle = pi, directed = true),
            OrganismCell(0, 2, TAIL, angle = pi * 0.9f, directed = true),
            OrganismCell(0, 1, FIN, angle = pi * 1.1f, directed = true),
            // Мышцы по бокам, сжаты по-разному.
            OrganismCell(1, 0, contracted(0f)),
            OrganismCell(2, 0, contracted(0.5f)),
            OrganismCell(3, 0, contracted(1f)),
            OrganismCell(1, 2, contracted(0.2f)),
            OrganismCell(2, 2, contracted(0.8f)),
            OrganismCell(3, 2, MUSCLE),
            // Середина: нейроны, кость, жир, лист, хамелеоны.
            OrganismCell(1, 1, NEURON),
            OrganismCell(2, 1, NEURON, energy = 9f),
            OrganismCell(3, 1, rgba(70, 200, 130), angle = 0.6f, spiral = true),
            OrganismCell(4, 1, BONE, energy = 1f),
            OrganismCell(4, 0, FAT, energy = 10f),
            OrganismCell(4, 2, LEAF),
            OrganismCell(1, 3, NEURON),
            OrganismCell(2, 3, rgba(40, 40, 60), angle = -0.4f, spiral = true),
            OrganismCell(1, -1, LEAF, energy = 0f),
            // Глаз впереди.
            OrganismCell(5, 1, EYE, angle = 0.15f)
        )

        val cells = CellInstanceBuffer()
        cells.begin()
        val positions = HashMap<Pair<Int, Int>, Pair<Float, Float>>()
        body.forEachIndexed { i, cell ->
            val x = organismX(cell.column, cell.row)
            val y = organismY(cell.row)
            positions[cell.column to cell.row] = x to y
            cells.putCell(
                x = x,
                y = y,
                color = cell.color,
                angleCos = cos(cell.angle),
                angleSin = sin(cell.angle),
                radius = ORGANISM_RADIUS,
                energy = cell.energy,
                cellType = i % layerCount,
                noiseSeed = 300 + i,
                spiral = cell.spiral
            )
        }

        // Еда рядом — не-клетки своего цвета.
        val random = Random(5)
        repeat(9) { i ->
            cells.putCell(
                x = 7.2f + random.nextFloat() * 1.6f,
                y = -1.4f + random.nextFloat() * 1.2f,
                color = rgba(232, 130, 40),
                angleCos = 1f,
                angleSin = 0f,
                radius = 0.3f + random.nextFloat() * 0.08f,
                energy = 0f,
                cellType = nonCellLayer,
                noiseSeed = 900 + i,
                isCell = false
            )
        }

        fun at(column: Int, row: Int) = positions.getValue(column to row)

        val overlay = OverlayInstanceBuffer()
        overlay.begin()

        // Порядок записи — порядок отрисовки: кольца, лучи, стрелки, нейросвязи сверху.
        val (pheromoneX, pheromoneY) = at(2, 3)
        overlay.putRing(pheromoneX, pheromoneY, 2.2f, rgba(90, 200, 255))

        val (eyeX, eyeY) = at(5, 1)
        val eyeAngle = 0.15f
        overlay.putRay(
            eyeX + cos(eyeAngle) * ORGANISM_RADIUS, eyeY + sin(eyeAngle) * ORGANISM_RADIUS,
            eyeX + cos(eyeAngle) * 6f, eyeY + sin(eyeAngle) * 6f,
            EYE, signal = 0.8f
        )

        body.filter { it.directed }.forEach { cell ->
            val (x, y) = at(cell.column, cell.row)
            overlay.putArrow(
                x, y, x + cos(cell.angle) * 0.9f, y + sin(cell.angle) * 0.9f,
                rgba(255, 255, 255, 235)
            )
        }

        // Нейросвязи: глаз → нейрон → мышцы и хвост; одна тормозная, одна молчит.
        val cyan = rgba(0, 255, 255)
        val magenta = rgba(255, 64, 200)
        val yellow = rgba(255, 220, 60)
        val green = rgba(80, 230, 120)
        fun link(from: Pair<Int, Int>, to: Pair<Int, Int>, color: Int, signal: Float, seed: Int) {
            val (fx, fy) = at(from.first, from.second)
            val (tx, ty) = at(to.first, to.second)
            overlay.putNeuralLink(fx, fy, tx, ty, color, signal, seed)
        }
        link(5 to 1, 2 to 1, cyan, 0.8f, 1)
        link(2 to 1, 3 to 0, magenta, 1f, 2)
        link(2 to 1, 2 to 2, magenta, 0.35f, 3)
        link(2 to 1, 1 to 1, yellow, -0.7f, 4)
        link(1 to 1, 0 to 0, green, 0.15f, 5)
        link(1 to 1, 0 to 2, green, 0f, 6)
        link(1 to 3, 2 to 3, cyan, 0.5f, 7)

        val pheromones = PheromoneInstanceBuffer()
        pheromones.begin()
        pheromones.put(x = pheromoneX, y = pheromoneY, a = 0.3f, color = rgba(90, 200, 255))

        return Scene(
            cells = cells.end(),
            pheromones = pheromones.end(),
            centerX = 3.6f,
            centerY = 0.9f,
            zoom = 0.0095f,
            overlay = overlay.end()
        )
    }

    /**
     * Цвет в том виде, в каком его ждёт unpackRGBA8: младший байт — красный.
     * Это ровно раскладка libGDX Color.toIntBits(), которой пользуется игра.
     */
    private fun rgba(r: Int, g: Int, b: Int, a: Int = 255): Int =
        (a shl 24) or (b shl 16) or (g shl 8) or r
}
