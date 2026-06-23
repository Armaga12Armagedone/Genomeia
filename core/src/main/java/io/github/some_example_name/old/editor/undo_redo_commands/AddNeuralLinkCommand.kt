package io.github.some_example_name.old.editor.undo_redo_commands

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage
import io.github.some_example_name.old.systems.genomics.genome.LinkData

//TODO тут происходит какая-то жесть, очень сложная и запутанная логика с нейролинками, надо как-то упростить
class AddNeuralLinkCommand(
    val cellFrom: EditorCell,
    val cellTo: EditorCell,
    val doesNeedAddNewStage: Boolean,
    val isNeural: Boolean,
    val isLongNeuralLink: Boolean,
    val color: Color,
    val linkId: Int,
    val isLink1NeuralDirected: Boolean,
    val cellAId: Int,
    val cellBId: Int,
    stageInstruction: MutableList<GenomeStage>,
    currentTick: Int
) : UndoRedoCommand(
    tick = currentTick,
    genomeStageInstruction = stageInstruction
) {

    override fun execute() {
        val linkData = when {
            linkId == -1 && isLongNeuralLink -> {
                LinkData(
                    isNeuronal = true,
                    directedNeuronLink = cellTo.id,
                    color = color
                )
            } // Создаем длинный нейро-линк
            linkId != -1 && isLongNeuralLink -> {
                null
            } // Удаляем длинный нейро-линк
            isNeural -> {
                LinkData(
                    isNeuronal = false,
                    directedNeuronLink = null
                )
            }
            !isNeural -> {
                LinkData(
                    isNeuronal = true,
                    directedNeuronLink = cellTo.id,
                    color = color
                )
            }
            else -> {
                LinkData(
                    isNeuronal = false,
                    directedNeuronLink = null
                )
            }
        }

        if (doesNeedAddNewStage) {
            genomeStageInstruction.add(GenomeStage())
        }

        when {
            cellFrom.isPhantom && cellTo.isPhantom -> {
                if (cellFrom.divide?.physicalLink[cellTo.id] != null) {
                    cellFrom.divide.physicalLink.compute(cellTo.id) { _, current ->
                        if (linkData == null) return@compute null
                        if (current == null) {
                            return@compute linkData
                        }
                        current.copy(
                            isNeuronal = linkData.isNeuronal,
                            directedNeuronLink = linkData.directedNeuronLink,
                            color = linkData.color
                        )
                    }
                } else if (cellTo.divide?.physicalLink[cellFrom.id] != null) {
                    cellTo.divide.physicalLink.compute(cellFrom.id) { _, current ->
                        if (linkData == null) return@compute null
                        if (current == null) {
                            return@compute linkData
                        }
                        current.copy(
                            isNeuronal = linkData.isNeuronal,
                            directedNeuronLink = linkData.directedNeuronLink,
                            color = linkData.color
                        )
                    }
                } else {
                    cellFrom.divide?.physicalLink?.compute(cellTo.id) { _, current ->
                        if (linkData == null) return@compute null
                        if (current == null) {
                            return@compute linkData
                        }
                        current.copy(
                            isNeuronal = linkData.isNeuronal,
                            directedNeuronLink = linkData.directedNeuronLink,
                            color = linkData.color
                        )
                    }
                }
            }
            cellFrom.isPhantom && !cellTo.isPhantom -> {
                cellFrom.divide?.physicalLink?.compute(cellTo.id) { _, current ->
                    if (linkData == null) return@compute null
                    if (current == null) {
                        return@compute linkData
                    }
                    current.copy(
                        isNeuronal = linkData.isNeuronal,
                        directedNeuronLink = linkData.directedNeuronLink,
                        color = linkData.color
                    )
                }
            }
            !cellFrom.isPhantom && cellTo.isPhantom -> {
                cellTo.divide?.physicalLink?.compute(cellFrom.id) { _, current ->
                    if (linkData == null) return@compute null
                    if (current == null) {
                        return@compute linkData
                    }
                    current.copy(
                        isNeuronal = linkData.isNeuronal,
                        directedNeuronLink = linkData.directedNeuronLink,
                        color = linkData.color
                    )
                }
            }
            else -> {
                var otherCellId = cellTo.id
                var parentCell = cellFrom.id

                if (isNeural) {
                    if (isLink1NeuralDirected) {
                        otherCellId = cellAId
                        parentCell = cellBId
                    } else {
                        otherCellId = cellBId
                        parentCell = cellAId
                    }
                }

                val mutate = Action(physicalLink = hashMapOf(otherCellId to linkData))

                genomeStageInstruction[tick].cellActions.compute(parentCell) { _, current ->
                    return@compute when {
                        current == null -> {
                            CellAction(mutate = mutate)
                        }
                        current.mutate == null -> {
                            current.copy(mutate = mutate)
                        }
                        else -> {
                            current.also {
                                if (linkData != null) {
                                    it.mutate?.physicalLink?.compute(otherCellId) { _, old ->
                                        if (old == null) {
                                            return@compute linkData
                                        }
                                        null
                                    }
                                } else {
                                    it.mutate?.physicalLink?.let { map ->
                                        if (!map.containsKey(otherCellId)) {
                                            map[otherCellId] = linkData
                                        } else {
                                            map.remove(otherCellId)
                                        }
                                    }
                                }
                                if (current.mutate!!.physicalLink.isEmpty() && current.mutate == Action()) {
                                    val default = Action()
                                    if (default == current.mutate) {
                                        current.mutate = null
                                    }
                                }
                                if (current.mutate == null && current.divide == null) return@compute null
                            }
                        }
                    }
                }

                if (genomeStageInstruction[tick].cellActions.isEmpty()) {
                    genomeStageInstruction.removeAt(genomeStageInstruction.lastIndex)
                }
            }
        }
    }
}
