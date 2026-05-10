package io.github.some_example_name.old.editor.entities

import io.github.some_example_name.old.entities.EyeEntity
import io.github.some_example_name.old.entities.SpecialEntity
import it.unimi.dsi.fastutil.ints.IntArrayList

class EyeReplay(
    startCapacity: Int,
    val specialEntity: SpecialEntity,
    val eyeEntity: EyeEntity
) {
    var capacity = startCapacity
    var size = 0

    var colorDifferentiation = ByteArray(startCapacity)
    var visibilityRange = FloatArray(startCapacity)

    val replayCellsCounterInTick = IntArrayList(10)
    val tickStartIndices = IntArrayList(10)   // ← НОВОЕ: стартовые индексы для каждого тика

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > capacity) {
            val newCapacity = minCapacity.coerceAtLeast(capacity * 2)
            colorDifferentiation = colorDifferentiation.copyOf(newCapacity)
            visibilityRange = visibilityRange.copyOf(newCapacity)
            capacity = newCapacity
        }
    }

    fun copy() {
        val cellsAmount = eyeEntity.aliveList.size

        // Запоминаем количество клеток и стартовую позицию этого тика
        replayCellsCounterInTick.add(cellsAmount)
        tickStartIndices.add(size)                    // ← сохраняем начало текущего тика

        ensureCapacity(size + cellsAmount)

        System.arraycopy(eyeEntity.colorDifferentiation, 0, colorDifferentiation, size, cellsAmount)
        System.arraycopy(eyeEntity.visibilityRange, 0, visibilityRange, size, cellsAmount)

        size += cellsAmount
    }

    fun getTickCount(): Int = replayCellsCounterInTick.size

    fun getEyePositionInReplay(
        tick: Int,
        indexInTick: Int
    ): Int? {
        if (tick < 0 || tick >= tickStartIndices.size) return null

        val start = tickStartIndices.getInt(tick)
        val count = replayCellsCounterInTick.getInt(tick)

        if (indexInTick < 0 || indexInTick >= count) return null

        val pos = start + indexInTick

        return pos
    }

    fun getColorDifferentiation(tick: Int, indexInTick: Int): Byte? {
        val index = getEyePositionInReplay(tick, indexInTick)
        return index?.let { colorDifferentiation[it] }
    }
    fun getVisibilityRange(tick: Int, indexInTick: Int): Float? {
        val index = getEyePositionInReplay(tick, indexInTick)
        return index?.let { visibilityRange[it] }
    }
}
