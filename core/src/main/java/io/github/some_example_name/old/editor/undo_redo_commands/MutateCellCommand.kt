package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage

class MutateCellCommand(
    val action: Action,
    val clickedCell: EditorCell,
    val doesNeedAddNewStage: Boolean,
    stageInstruction: MutableList<GenomeStage>,
    currentTick: Int
) : UndoRedoCommand(
    tick = currentTick,
    genomeStageInstruction = stageInstruction
) {

    override fun execute() {
        if (doesNeedAddNewStage) {
            genomeStageInstruction.add(GenomeStage())
        }

        genomeStageInstruction[tick].cellActions.compute(clickedCell.id) { _, oldValue ->
            if (oldValue == null) return@compute CellAction(
                mutate = action
            )
            if (oldValue.mutate == null) return@compute oldValue.copy(mutate = action)

            oldValue.copy(
                mutate = action.copy(
                    physicalLink = oldValue.mutate?.physicalLink ?: action.physicalLink
                )
            )
        }
    }
}
