package io.github.some_example_name.old.editor.commands

import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage
import io.github.some_example_name.old.systems.genomics.genome.LinkData
import io.github.some_example_name.old.genome_editor_deprecated.EditorCell
import io.github.some_example_name.old.genome_editor_deprecated.EditorLinks

class AddNeuralLinkCommand(
    val currentStage: Int,
    val cellFrom: EditorCell,
    val cellTo: EditorCell,
    val genomeStageInstruction: MutableList<GenomeStage>,
    val doesNeedAddNewStage: Boolean,
    val isNeural: Boolean,
    val parentId: Int
) : Command {

    override val stage = currentStage

    private val oldGenomeStageInstruction = genomeStageInstruction.map { it.deepCopy() }
    private var newGenomeStageInstruction: List<GenomeStage>? = null

    override fun execute() {

        if (newGenomeStageInstruction != null) {
            genomeStageInstruction.clear()
            genomeStageInstruction.addAll(newGenomeStageInstruction!!)
            return
        }

        val linkData = if (isNeural) {
            LinkData(
                isNeuronal = false,
                directedNeuronLink = null
            )
        } else {
            LinkData(
                isNeuronal = true,
                directedNeuronLink = cellTo.id
            )
        }

        if (doesNeedAddNewStage) {
            genomeStageInstruction.add(GenomeStage())
        }

        when {
            cellFrom.isPhantom && cellTo.isPhantom -> {
                if (cellFrom.divide?.physicalLink[cellTo.id] != null) {
                    cellFrom.divide.physicalLink.compute(cellTo.id) { _, old ->
                        old?.copy(
                            isNeuronal = linkData.isNeuronal,
                            directedNeuronLink = linkData.directedNeuronLink
                        )
                    }
                } else if (cellTo.divide?.physicalLink[cellFrom.id] != null) {
                    cellTo.divide.physicalLink.compute(cellFrom.id) { _, old ->
                        old?.copy(
                            isNeuronal = linkData.isNeuronal,
                            directedNeuronLink = linkData.directedNeuronLink
                        )
                    }
                }
            }
            cellFrom.isPhantom && !cellTo.isPhantom -> {
                cellFrom.divide?.physicalLink?.compute(cellTo.id) { _, old ->
                    old?.copy(
                        isNeuronal = linkData.isNeuronal,
                        directedNeuronLink = linkData.directedNeuronLink
                    )
                }
            }
            !cellFrom.isPhantom && cellTo.isPhantom -> {
                cellTo.divide?.physicalLink?.compute(cellFrom.id) { _, old ->
                    old?.copy(
                        isNeuronal = linkData.isNeuronal,
                        directedNeuronLink = linkData.directedNeuronLink
                    )
                }
            }
            else -> {
                val otherCellId = if (cellTo.id != parentId) cellTo.id else cellFrom.id
                val parentCell = parentId

                val mutate = Action(physicalLink = hashMapOf(otherCellId to linkData))
                genomeStageInstruction[currentStage].cellActions.compute(parentCell) { _, current ->
                    return@compute when {
                        current == null -> CellAction(mutate = mutate)
                        current.mutate == null -> current.copy(mutate = mutate)
                        else -> {
                            current.also {
                                it.mutate?.physicalLink?.compute(otherCellId) { _, old ->
                                    if (old == null) return@compute linkData
                                    old.copy(
                                        isNeuronal = linkData.isNeuronal,
                                        directedNeuronLink = linkData.directedNeuronLink
                                    )
                                }
                                if (current.mutate!!.physicalLink.isEmpty()) {
                                    val default = Action()
                                    if (default == current.mutate) {
                                        current.mutate = null
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        newGenomeStageInstruction = genomeStageInstruction.map { it.deepCopy() }
    }

    override fun undo() {
        genomeStageInstruction.clear()
        genomeStageInstruction.addAll(oldGenomeStageInstruction)
    }
}
