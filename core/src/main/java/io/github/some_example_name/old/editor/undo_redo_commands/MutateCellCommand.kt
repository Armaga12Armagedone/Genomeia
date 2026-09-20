package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage

class MutateCellCommand(
    val action: Action,
    val clickedCell: EditorCell,
    stageInstruction: MutableList<GenomeStage>,
    currentTick: Int
) : UndoRedoCommand(
    tick = currentTick,
    genomeStageInstruction = stageInstruction,
    doesNeedAddNewStage = stageInstruction.size <= currentTick
) {

    override fun execute(): StageResult {
        val stage = genomeStageInstruction[tick]
        val oldValue = stage.cellActions[clickedCell.id]

        val newValue = when {
            oldValue == null -> CellAction(mutate = action)
            oldValue.mutate == null -> oldValue.copy(mutate = action)
            else -> oldValue.copy(
                mutate = action.copy(
                    physicalLink = oldValue.mutate.physicalLink
                )
            )
        }

        return StageResult.Keep(stage.copy(cellActions = stage.cellActions + (clickedCell.id to newValue)))
    }
}
