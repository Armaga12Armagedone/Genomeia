package io.github.some_example_name.old.systems.render

import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.cells.Chameleon
import io.github.some_example_name.old.cells.Eye
import io.github.some_example_name.old.cells.Muscle
import io.github.some_example_name.render.pack.OverlayInstanceBuffer

/**
 * Как клетки выглядят в отладочном режиме — одно правило на симуляцию (RenderBufferManager)
 * и редактор генома (EditorRenderSystem), чтобы один и тот же организм в обоих местах
 * выглядел одинаково.
 *
 *  - Цвет клетки — defaultColor её типа, а не цвет из генома: по картинке сразу видно,
 *    где какие клетки. Исключения два: хамелеон остаётся своего текущего цвета (это и есть
 *    то, что он делает) и получает спираль, а мышца темнеет, когда сжата.
 *  - Клетки с направлением получают стрелку, глаз — луч до предела видимости.
 *
 * Таблицы строятся по списку клеток один раз: на каждой клетке кадра — только чтение
 * массива по типу, без cellList и без is-проверок.
 */
class DebugCellPalette(private val cellList: List<Cell>) {

    private val colorByType = IntArray(cellList.size) { cellList[it].defaultColor.toIntBits() }

    private val styleByType = ByteArray(cellList.size) { type ->
        when (cellList[type]) {
            is Chameleon -> STYLE_CHAMELEON
            is Muscle -> STYLE_MUSCLE
            else -> STYLE_PLAIN
        }
    }

    private val shapeByType = ByteArray(cellList.size) { type ->
        val cell = cellList[type]
        when {
            cell is Eye -> SHAPE_RAY
            cell.isDirected -> SHAPE_ARROW
            else -> SHAPE_NONE
        }
    }

    /** defaultColor типа в упаковке Color.toIntBits. */
    fun defaultColor(cellType: Int): Int = colorByType[cellType]

    /**
     * Цвет клетки в отладке. [ownColor] — её собственный цвет (из генома или выставленный
     * хамелеоном), [contraction] — насколько она сжата, 0..1; важен только мышце.
     */
    fun color(cellType: Int, ownColor: Int, contraction: Float): Int =
        when (styleByType[cellType]) {
            STYLE_CHAMELEON -> ownColor
            STYLE_MUSCLE -> darken(colorByType[cellType], contraction)
            else -> colorByType[cellType]
        }

    /** Рисовать ли на клетке спираль — знак хамелеона, см. CellInstanceBuffer.FLAG_SPIRAL. */
    fun hasSpiral(cellType: Int): Boolean = styleByType[cellType] == STYLE_CHAMELEON

    fun isMuscle(cellType: Int): Boolean = styleByType[cellType] == STYLE_MUSCLE

    fun isEye(cellType: Int): Boolean = shapeByType[cellType] == SHAPE_RAY

    /** Клетка с направлением, кроме глаза: у того вместо стрелки луч. */
    fun hasArrow(cellType: Int): Boolean = shapeByType[cellType] == SHAPE_ARROW

    /**
     * Стрелка направления: из центра на [ARROW_LENGTH] радиусов. [signal] подсвечивает
     * её — хвост тянет сильнее, стрелка ярче.
     */
    fun writeArrow(
        sink: OverlaySink,
        x: Float,
        y: Float,
        angleCos: Float,
        angleSin: Float,
        radius: Float,
        signal: Float
    ) {
        val length = radius * ARROW_LENGTH
        sink.add(
            x, y,
            x + angleCos * length, y + angleSin * length,
            ARROW_COLOR,
            OverlayInstanceBuffer.params(OverlayInstanceBuffer.KIND_ARROW, signal, 0)
        )
    }

    /**
     * Луч глаза: от обода до предела видимости [range] — так его пускает Eye.doOnTick.
     * Цвет — цвет глаза, яркость — от [signal], выхода глаза: видит ли он что-то.
     */
    fun writeEyeRay(
        sink: OverlaySink,
        cellType: Int,
        x: Float,
        y: Float,
        angleCos: Float,
        angleSin: Float,
        radius: Float,
        range: Float,
        signal: Float
    ) {
        if (range <= radius) return
        sink.add(
            x + angleCos * radius, y + angleSin * radius,
            x + angleCos * range, y + angleSin * range,
            colorByType[cellType],
            OverlayInstanceBuffer.params(OverlayInstanceBuffer.KIND_RAY, signal, 0)
        )
    }

    /**
     * Нейросвязь от источника к приёмнику. Цвет — тот, что в NeuralLinkEntity.color, а туда
     * он приходит из генома (LinkData.color), как и в редакторе. [seed] — индекс связи:
     * от него только сдвиг фазы импульсов, чтобы связи не мигали в такт.
     */
    fun writeNeuralLink(
        sink: OverlaySink,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        color: Int,
        signal: Float,
        seed: Int
    ) {
        sink.add(
            fromX, fromY, toX, toY,
            color,
            OverlayInstanceBuffer.params(OverlayInstanceBuffer.KIND_NEURAL, signal, seed)
        )
    }

    /** Куда складывать фигуры оверлея: снимок потока симуляции или буфер редактора. */
    fun interface OverlaySink {
        fun add(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, params: Int)
    }

    companion object {
        private const val STYLE_PLAIN: Byte = 0
        private const val STYLE_CHAMELEON: Byte = 1
        private const val STYLE_MUSCLE: Byte = 2

        private const val SHAPE_NONE: Byte = 0
        private const val SHAPE_ARROW: Byte = 1
        private const val SHAPE_RAY: Byte = 2

        /** Насколько темнеет полностью сжатая мышца: 0.55 — до 45 % яркости. */
        const val MUSCLE_DARKEN = 0.55f

        /** Длина стрелки направления в радиусах клетки: выходит за клетку на 80 %. */
        const val ARROW_LENGTH = 1.8f

        /** Белая, чуть прозрачная: читается на клетке любого цвета (обводка у неё тёмная). */
        const val ARROW_COLOR = (235 shl 24) or 0xFFFFFF

        /** Цвет Color.toIntBits темнее в 1 - MUSCLE_DARKEN * [amount] раз, альфа та же. */
        fun darken(abgr: Int, amount: Float): Int {
            val k = 1f - MUSCLE_DARKEN * amount.coerceIn(0f, 1f)
            val r = ((abgr and 0xFF) * k).toInt()
            val g = (((abgr ushr 8) and 0xFF) * k).toInt()
            val b = (((abgr ushr 16) and 0xFF) * k).toInt()
            return (abgr and 0xFF000000.toInt()) or (b shl 16) or (g shl 8) or r
        }
    }
}
