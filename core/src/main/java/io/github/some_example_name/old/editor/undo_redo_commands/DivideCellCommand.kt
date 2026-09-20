package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage
import io.github.some_example_name.old.systems.genomics.genome.LinkData
import kotlin.math.atan2
import kotlin.math.sqrt

class DivideCellCommand(
    val clickedCell: EditorCell,
    val neighboursCells: List<EditorCell>,
    val divide: Action,
    val newId: Int,
    val newPoint: Pair<Float, Float>,
    stageInstruction: MutableList<GenomeStage>,
    currentTick: Int
) : UndoRedoCommand(
    tick = currentTick,
    genomeStageInstruction = stageInstruction,
    doesNeedAddNewStage = stageInstruction.size <= currentTick
) {

    override fun execute(): StageResult {
        val justAddedCellX = newPoint.first
        val justAddedCellY = newPoint.second

        val deltaXAngle = justAddedCellX - clickedCell.x
        val deltaYAngle = justAddedCellY - clickedCell.y

        val angle = atan2(deltaYAngle, deltaXAngle) - clickedCell.angleToParent

        val physicalLink = HashMap(neighboursCells.associate {
            val deltaX = justAddedCellX - it.x
            val deltaY = justAddedCellY - it.y
            val length = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
            it.id to LinkData(length = length)
        })

        val divideAction = divide.copy(
            id = newId,
            angle = angle,
            physicalLink = physicalLink
        )

        val stage = genomeStageInstruction[tick]
        val oldValue = stage.cellActions[clickedCell.id]

        val newValue = oldValue?.copy(divide = divideAction) ?: CellAction(divide = divideAction)

        return StageResult.Keep(stage.copy(cellActions = stage.cellActions + (clickedCell.id to newValue)))
    }
}
