package io.github.some_example_name.old.entities

import io.github.some_example_name.old.core.utils.UnorderedIntPairMap

class NeuralLinkEntity(
    linksStartMaxAmount: Int,
    val cellEntity: CellEntity,
    val isEditor: Boolean,
    /** Источник арен: нейросвязи организма лежат в массивах подряд. См. OrganEntity. */
    private val organEntity: OrganEntity
) : Entity(linksStartMaxAmount) {

    var links1 = IntArray(maxAmount) { -1 }
    var links2 = IntArray(maxAmount) { -1 }
    var linksGeneration1 = IntArray(maxAmount) { -1 }
    var linksGeneration2 = IntArray(maxAmount) { -1 }
    var isLink1NeuralDirected = BooleanArray(maxAmount)
    var color = IntArray(maxAmount)
    val linkIndexMap = UnorderedIntPairMap(10_000)
    val linkEditorIndexMap = UnorderedIntPairMap(100)

    fun addNeuralLink(
        cellIndex: Int,
        otherCellIndex: Int,
        isLink1NeuralDirected: Boolean,
        color: Int,
        cellGeneration: Int = LinkEntity.NO_GENERATION_CHECK,
        otherCellGeneration: Int = LinkEntity.NO_GENERATION_CHECK,
    ): Int {
        if (!isCellUsable(cellIndex, cellGeneration) ||
            !isCellUsable(otherCellIndex, otherCellGeneration)
        ) {
            return -1
        }

        // Слот из арены нейросвязей организма — та же схема, что у физических связей
        // (LinkEntity.addLink). Нейросвязь всегда внутриорганизменная.
        //
        // Ёмкость этой арены, в отличие от клеток и связей, взята оценкой, а не замером
        // (OrganEntity.DEFAULT_MAX_NEURAL_LINKS), поэтому исчерпание здесь вероятнее всего
        // означает не разросшееся тело, а недооценённую константу.
        val organIndex = cellEntity.organIndex[cellIndex]
        val arenaLinkSlot = if (organEntity.hasArena(organIndex)) {
            val slot = organEntity.takeNeuralLinkSlot(organIndex)
            if (slot == -1) {
                throw IllegalStateException(
                    "арена нейросвязей организма $organIndex исчерпана " +
                        "(ёмкость ${organEntity.neuralLinkArenaCapacity[organIndex]}) — " +
                        "поднимите OrganEntity.DEFAULT_MAX_NEURAL_LINKS"
                )
            }
            slot
        } else -1

        val addLinkIndex = if (arenaLinkSlot == -1) add() else addAt(arenaLinkSlot)

        links1[addLinkIndex] = cellIndex
        links2[addLinkIndex] = otherCellIndex
        linksGeneration1[addLinkIndex] = cellEntity.getGeneration(cellIndex)
        linksGeneration2[addLinkIndex] = cellEntity.getGeneration(otherCellIndex)
        this.isLink1NeuralDirected[addLinkIndex] = isLink1NeuralDirected
        this.color[addLinkIndex] = color

        linkIndexMap.put(cellIndex, otherCellIndex, addLinkIndex)
        linkEditorIndexMap.put(cellIndex, otherCellIndex, addLinkIndex)

        cellEntity.addNeuralConnection(cellIndex, addLinkIndex)
        cellEntity.addNeuralConnection(otherCellIndex, addLinkIndex)

        return addLinkIndex
    }

    fun deleteNeuralLink(linkIndex: Int, linkGeneration: Int? = null) {
        if (isAlive[linkIndex] && (linkGeneration == null || getGeneration(linkIndex) == linkGeneration)) {
            delete(linkIndex)

            val cellA = links1[linkIndex]
            val cellB = links2[linkIndex]

            linkIndexMap.remove(cellA, cellB)
            if (!isEditor) {
                linkEditorIndexMap.remove(cellA, cellB)
            }

            cellEntity.removeNeuralConnection(cellA, linkIndex)
            cellEntity.removeNeuralConnection(cellB, linkIndex)

            links1[linkIndex] = -1
            links2[linkIndex] = -1
            linksGeneration1[linkIndex] = -1
            linksGeneration2[linkIndex] = -1
            isLink1NeuralDirected[linkIndex] = false
            color[linkIndex] = 0
        }
    }

    fun detachAllNeuralLinks(cellIndex: Int) {
        val links = cellEntity.neuralConnections.get(cellIndex) ?: return

        while (!links.isEmpty) {
            val linkIndex = links.getInt(0)

            if (!isAlive[linkIndex]) {
                cellEntity.removeNeuralConnection(cellIndex, linkIndex)
                continue
            }

            deleteNeuralLink(linkIndex)
        }
    }

    private fun isCellUsable(cellIndex: Int, expectedGeneration: Int): Boolean {
        if (cellIndex < 0 || !cellEntity.isAlive[cellIndex]) return false
        if (expectedGeneration == LinkEntity.NO_GENERATION_CHECK) return true
        return cellEntity.getGeneration(cellIndex) == expectedGeneration
    }

    override fun onCopy() {
        // TODO
    }

    override fun onPaste() {
        // TODO
    }

    override fun onClear(bound: Int) {
        links1.clear(-1)
        links2.clear(-1)
        linksGeneration1.clear(-1)
        linksGeneration2.clear(-1)
        isLink1NeuralDirected.clear(false)
        color.clear()
        linkIndexMap.clear()
        linkEditorIndexMap.clear()
    }

    override fun onResize(oldMax: Int) {
        links1 = links1.resize(-1)
        links2 = links2.resize(-1)
        linksGeneration1 = linksGeneration1.resize(-1)
        linksGeneration2 = linksGeneration2.resize(-1)
        isLink1NeuralDirected = isLink1NeuralDirected.resize(false)
        color = color.resize()
    }
}
