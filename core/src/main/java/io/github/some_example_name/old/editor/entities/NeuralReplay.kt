package io.github.some_example_name.old.editor.entities

import io.github.some_example_name.old.entities.NeuralEntity
import it.unimi.dsi.fastutil.ints.IntArrayList

class NeuralReplay(
    startCapacity: Int,
    val neuralEntity: NeuralEntity
) {
    var capacity = startCapacity
    var size = 0

    var activationFuncType = ByteArray(startCapacity)
    var a = FloatArray(startCapacity)
    var b = FloatArray(startCapacity)
    var c = FloatArray(startCapacity)
    var isSum = BooleanArray(startCapacity)

    val replayCellsCounterInTick = IntArrayList(10)
    val tickStartIndices = IntArrayList(10)   // ← НОВОЕ: стартовые индексы для каждого тика

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > capacity) {
            val newCapacity = minCapacity.coerceAtLeast(capacity * 2)

            activationFuncType = activationFuncType.copyOf(newCapacity)
            a = a.copyOf(newCapacity)
            b = b.copyOf(newCapacity)
            c = c.copyOf(newCapacity)
            isSum = isSum.copyOf(newCapacity)

            capacity = newCapacity
        }
    }

    fun copy() {
        val cellsAmount = neuralEntity.aliveList.size

        // Запоминаем количество клеток и стартовую позицию этого тика
        replayCellsCounterInTick.add(cellsAmount)
        tickStartIndices.add(size)                    // ← сохраняем начало текущего тика

        ensureCapacity(size + cellsAmount)

        System.arraycopy(neuralEntity.activationFuncType, 0, activationFuncType, size, cellsAmount)
        System.arraycopy(neuralEntity.a, 0, a, size, cellsAmount)
        System.arraycopy(neuralEntity.b, 0, b, size, cellsAmount)
        System.arraycopy(neuralEntity.c, 0, c, size, cellsAmount)
        System.arraycopy(neuralEntity.isSum, 0, isSum, size, cellsAmount)

        size += cellsAmount
    }

    fun getTickCount(): Int = replayCellsCounterInTick.size

    fun getNeuralPositionInReplay(
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

    fun getActivationFuncType(tick: Int, indexInTick: Int): Byte? {
        val index = getNeuralPositionInReplay(tick, indexInTick)
        return index?.let { activationFuncType[it] }
    }
    fun getA(tick: Int, indexInTick: Int): Float? {
        val index = getNeuralPositionInReplay(tick, indexInTick)
        return index?.let { a[it] }
    }
    fun getB(tick: Int, indexInTick: Int): Float? {
        val index = getNeuralPositionInReplay(tick, indexInTick)
        return index?.let { b[it] }
    }
    fun getC(tick: Int, indexInTick: Int): Float? {
        val index = getNeuralPositionInReplay(tick, indexInTick)
        return index?.let { c[it] }
    }
    fun getIsSum(tick: Int, indexInTick: Int): Boolean? {
        val index = getNeuralPositionInReplay(tick, indexInTick)
        return index?.let { isSum[it] }
    }
}
