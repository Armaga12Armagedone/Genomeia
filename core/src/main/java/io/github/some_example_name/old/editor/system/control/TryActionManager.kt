package io.github.some_example_name.old.editor.system.control

import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.currentTick
import io.github.some_example_name.old.editor.system.CellSearchManager
import io.github.some_example_name.old.editor.system.command.CommandEditorStackManager
import io.github.some_example_name.old.editor.system.logic.ToEditorDataMapper
import io.github.some_example_name.old.editor.system.simulation.EditorSimulationSystem
import io.github.some_example_name.old.editor.undo_redo_commands.ChangeDivideCommand
import io.github.some_example_name.old.editor.undo_redo_commands.DivideCellCommand
import io.github.some_example_name.old.editor.undo_redo_commands.MutateCellCommand
import io.github.some_example_name.old.editor.undo_redo_commands.RemoveCellCommand
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.systems.genomics.genome.Action

class TryActionManager(
    val commandEditorStackManager: CommandEditorStackManager,
    val editorSimulationSystem: EditorSimulationSystem,
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val cellSearchManager: CellSearchManager,
    val toEditorDataMapper: ToEditorDataMapper
) {
    var defaultActionType: LastActionType? = null
    var defaultAction: Action? = null

    fun tryToMutate(clickedCellIndex: Int, action: Action) {
        val genomeStageInstruction = editorSimulationSystem.genome.genomeStageInstruction

        commandEditorStackManager.executeCommand(
            command = MutateCellCommand(
                currentTick = currentTick,
                action = action,
                clickedCell = toEditorDataMapper.mapToEditorData(clickedCellIndex),
                stageInstruction = genomeStageInstruction,
                doesNeedAddNewStage = genomeStageInstruction.size <= currentTick,
            )
        )

        defaultActionType = LastActionType.MUTATE
        defaultAction = action.copy(
            id = -1,
            angle = null,
            physicalLink = hashMapOf()
        )
    }

    fun tryToChange(
        clickedIndex: Int,
        divide: Action
    ) {
        val genomeStageInstruction = editorSimulationSystem.genome.genomeStageInstruction
        commandEditorStackManager.executeCommand(
            command = ChangeDivideCommand(
                currentTick = currentTick,
                clickedCell = toEditorDataMapper.mapToEditorData(clickedIndex),
                divide = divide,
                stageInstruction = genomeStageInstruction
            )
        )
        defaultActionType = LastActionType.CHANGE
        defaultAction = divide.copy(
            id = -1,
            angle = null,
            physicalLink = hashMapOf()
        )
    }

    fun tryToRemove(clickedCellIndex: Int) {
        val genomeStageInstruction = editorSimulationSystem.genome.genomeStageInstruction
        val clickedCell = toEditorDataMapper.mapToEditorData(clickedCellIndex)
        commandEditorStackManager.executeCommand(
            command = RemoveCellCommand(
                currentTick = currentTick,
                clickedCell = clickedCell,
                parentCell = toEditorDataMapper.mapToEditorData(clickedCell.parentIndex),
                stageInstruction = genomeStageInstruction
            )
        )
        defaultActionType = LastActionType.DELETE
        defaultAction = null
    }

    fun tryToDivide(
        clickedCellIndex: Int,
        newDividedCellPosition: Pair<Float, Float>?,
        action: Action
    ) {
        if (newDividedCellPosition == null) return

        val genomeStageInstruction = editorSimulationSystem.genome.genomeStageInstruction

        val radius = 0.5f //TODO поменять когда будет выбор радиуса

        val neighboursIds = cellSearchManager.getAllCloseNeighboursEditor(
            grabbedX = newDividedCellPosition.first,
            grabbedY = newDividedCellPosition.second,
            grabbedRadius = radius
        )
        val neighboursCells = neighboursIds.map {
            toEditorDataMapper.mapToEditorData(it)
        }

        commandEditorStackManager.executeCommand(
            command = DivideCellCommand(
                clickedCell = toEditorDataMapper.mapToEditorData(clickedCellIndex),
                neighboursCells = neighboursCells,
                divide = action,
                newId = editorSimulationSystem.maxCellId + 1,
                newPoint = newDividedCellPosition,
                doesNeedAddNewStage = genomeStageInstruction.size <= currentTick,
                stageInstruction = genomeStageInstruction,
                currentTick = currentTick,
            )
        )

        defaultActionType = LastActionType.DIVIDE
        defaultAction = action.copy(
            id = -1,
            angle = null,
            physicalLink = hashMapOf()
        )
    }
}
