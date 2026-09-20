package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage
import io.github.some_example_name.old.systems.genomics.genome.LinkData
import kotlin.math.atan2
import kotlin.math.sqrt

class MoveCellCommand(
    val grabbedEditorCell: EditorCell,
    val parentEditorCell: EditorCell,
    val oldNeighboursJustAdded: List<EditorCell>,
    val newNeighbours: List<EditorCell>,
    val newX: Float,
    val newY: Float,
    currentTick: Int,
    val stageInstruction: MutableList<GenomeStage>
) : UndoRedoCommand(
    tick = currentTick,
    genomeStageInstruction = stageInstruction,
    doesNeedAddNewStage = false
) {

    override fun execute(): StageResult {
        val stage = genomeStageInstruction[tick]

        // Обновляем divide action родителя перетаскиваеймой клетки
        val grabbedCellParentAction = stage.cellActions[parentEditorCell.id] ?: throw Exception("No moved cell")
        val grabbedParentDivide = grabbedCellParentAction.divide ?: throw Exception("No moved cell")

        val newPhysicalLink: Map<Int, LinkData> = newNeighbours
            .filter { it.id != grabbedEditorCell.id }
            .associate { neighbour ->
                val deltaX = newX - neighbour.x
                val deltaY = newY - neighbour.y
                val length = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                neighbour.id to LinkData(length = length)
            }

        val deltaX = newX - parentEditorCell.x
        val deltaY = newY - parentEditorCell.y
        val newAngle = atan2(deltaY, deltaX) - parentEditorCell.angleToParent

        val newGrabbedParentDivide = grabbedParentDivide.copy(
            physicalLink = newPhysicalLink,
            angle = newAngle
        )

        // Убираем линки на перемещаемую клетку у старых только что добавленных клеток
        val updatedActionsFromOldNeighbours = oldNeighboursJustAdded
            .filter { it.isPhantom }
            .mapNotNull { oldNeighbour ->
                val oldAction = stage.cellActions[oldNeighbour.parentId]
                val oldDivide = oldAction?.divide
                if (oldDivide?.physicalLink?.containsKey(grabbedEditorCell.id) == true) {
                    val newLinks = oldDivide.physicalLink - grabbedEditorCell.id
                    val newDivide = oldDivide.copy(physicalLink = newLinks)
                    oldNeighbour.parentId to oldAction.copy(divide = newDivide)
                } else {
                    null
                }
            }
            .toMap()

        // Собираем итоговую карту cellActions
        val finalCellActions = stage.cellActions
            .plus(parentEditorCell.id to grabbedCellParentAction.copy(divide = newGrabbedParentDivide))
            .plus(updatedActionsFromOldNeighbours)

        return StageResult.Keep(stage.copy(cellActions = finalCellActions))
    }
}
