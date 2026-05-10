package io.github.some_example_name.old.editor.entities

import io.github.some_example_name.old.entities.LinkEntity
import it.unimi.dsi.fastutil.ints.IntArrayList

class LinkReplay(
    startCapacity: Int,
    val linkEntity: LinkEntity,
) {
    var capacity = startCapacity
    var size = 0

    var isNeuronLink = BooleanArray(startCapacity)
    var isLink1NeuralDirected = BooleanArray(startCapacity)
    var index = IntArray(startCapacity)

    val replayCellsCounterInTick = IntArrayList(10)
    val tickStartIndices = IntArrayList(10)   // ← НОВОЕ: стартовые индексы для каждого тика

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > capacity) {
            val newCapacity = minCapacity.coerceAtLeast(capacity * 2)
            isNeuronLink = isNeuronLink.copyOf(newCapacity)
            isLink1NeuralDirected = isLink1NeuralDirected.copyOf(newCapacity)
            index = index.copyOf(newCapacity)
            capacity = newCapacity
        }
    }

    fun copy() {
        val cellsAmount = linkEntity.aliveList.size

        // Запоминаем количество клеток и стартовую позицию этого тика
        replayCellsCounterInTick.add(cellsAmount)
        tickStartIndices.add(size)                    // ← сохраняем начало текущего тика

        ensureCapacity(size + cellsAmount)

        System.arraycopy(linkEntity.isNeuronLink, 0, isNeuronLink, size, cellsAmount)
        System.arraycopy(linkEntity.isLink1NeuralDirected, 0, isLink1NeuralDirected, size, cellsAmount)
        linkEntity.aliveList.getElements(0, index, size, cellsAmount)

        size += cellsAmount
    }

    inline fun forEachInTick(tick: Int, action: (cellType: Boolean, index: Boolean, pos: Int) -> Unit) {
        if (tick < 0 || tick >= tickStartIndices.size) return

        val start = tickStartIndices.getInt(tick)
        val count = replayCellsCounterInTick.getInt(tick)
        val end = start + count

        for (i in start until end) {
            action(isNeuronLink[i], isLink1NeuralDirected[i], index[i])
        }
    }

    fun getTickCount(): Int = replayCellsCounterInTick.size

    fun getLinkIsNeural(
        tick: Int,
        indexInTick: Int
    ): Boolean? {
        if (tick < 0 || tick >= tickStartIndices.size) return null

        val start = tickStartIndices.getInt(tick)
        val count = replayCellsCounterInTick.getInt(tick)

        if (indexInTick < 0 || indexInTick >= count) return null

        val pos = start + indexInTick

        return isNeuronLink[pos]
    }
}
