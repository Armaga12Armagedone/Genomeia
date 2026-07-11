package io.github.some_example_name.old.editor.system.control

import io.github.some_example_name.old.editor.undo_redo_commands.AddNeuralLinkCommand
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.currentTick
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.linkColor
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.nextStageTick
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.previousCtrlClicked
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.uiScreenCommands
import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.editor.entities.LinkReplay
import io.github.some_example_name.old.editor.system.CellSearchManager
import io.github.some_example_name.old.editor.system.SymmetryManager
import io.github.some_example_name.old.editor.system.command.CommandEditorStackManager
import io.github.some_example_name.old.editor.system.logic.ShowChangeRemoveDialog
import io.github.some_example_name.old.editor.system.logic.ShowMutateDialog
import io.github.some_example_name.old.editor.system.logic.ShowMutateOrDivideDialog
import io.github.some_example_name.old.editor.system.logic.ToEditorDataMapper
import io.github.some_example_name.old.editor.system.simulation.EditorSimulationSystem
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity

class LeftRightClickManager(
    val commandEditorStackManager: CommandEditorStackManager,
    val editorSimulationSystem: EditorSimulationSystem,
    val linkReplay: LinkReplay,
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val symmetryManager: SymmetryManager,
    val cellSearchManager: CellSearchManager,
    val toEditorDataMapper: ToEditorDataMapper,
    val tryActionManager: TryActionManager
) {

    fun leftClick(clickedIndex: Int, clickedCell: EditorCell, isCtrl: Boolean) {
        val genomeStageInstruction = editorSimulationSystem.genomeStageInstruction
        if (!isCtrl) {
            val newDividedCellPosition = cellSearchManager.getPositionForNewCell(
                clickedCellIndex = clickedIndex,
                symmetryManager = symmetryManager
            )

            uiScreenCommands = when {
                clickedCell.isPhantom && clickedCell.divide != null -> {
                    ShowChangeRemoveDialog(clickedCell)
                }

                newDividedCellPosition != null && clickedCell.divide == null -> {
                    ShowMutateOrDivideDialog(
                        clickedCell = clickedCell,
                        parentCell = if (clickedCell.parentIndex != -1) toEditorDataMapper.mapToEditorData(
                            clickedCell.parentIndex
                        ) else null,
                        newDividedCellPosition = newDividedCellPosition,
                        currentTick = currentTick
                    )
                }

                else -> {
                    ShowMutateDialog(
                        clickedCell = clickedCell,
                        parentCell = if (clickedCell.parentIndex != -1) toEditorDataMapper.mapToEditorData(
                            clickedCell.parentIndex
                        ) else null,
                        currentTick = currentTick
                    )
                }
            }
        } else {
            if (previousCtrlClicked != -1 && previousCtrlClicked != clickedIndex) {
                //Выполнение команды по созданию нейролинка
                val cellFrom = toEditorDataMapper.mapToEditorData(previousCtrlClicked)
                val cellTo = toEditorDataMapper.mapToEditorData(clickedIndex)
                val linkIndex = linkEntity.linkIndexMap.get(previousCtrlClicked, clickedIndex)

                val isNeural = if (linkIndex != -1) {
                    linkReplay.getLinkIsNeural(nextStageTick, linkIndex) ?: throw Exception()
                } else false

                val isLink1NeuralDirected = if (linkIndex != -1) {
                    linkReplay.getIsLink1NeuralDirected(nextStageTick, linkIndex) ?: throw Exception()
                } else false

                val isLongNeuralLink = if (linkIndex != -1) {
                    linkReplay.getIsLongNeuralLink(nextStageTick, linkIndex) ?: throw Exception()
                } else true
                commandEditorStackManager.executeCommand(
                    command = AddNeuralLinkCommand(
                        cellFrom = cellFrom,
                        cellTo = cellTo,
                        isNeural = isNeural,
                        isLink1NeuralDirected = isLink1NeuralDirected,
                        isLongNeuralLink = isLongNeuralLink,
                        color = linkColor,
                        linkId = linkIndex,
                        cellAId = if (linkIndex != -1) {
                            cellEntity.cellGenomeId[linkEntity.links1[linkIndex]]
                        } else cellFrom.id,
                        cellBId = if (linkIndex != -1) {
                            cellEntity.cellGenomeId[linkEntity.links2[linkIndex]]
                        } else cellTo.id,
                        stageInstruction = genomeStageInstruction,
                        currentTick = currentTick
                    )
                )
            }
            previousCtrlClicked = clickedIndex
        }
    }

    fun rightClick(clickedIndex: Int, clickedCell: EditorCell) {
        //Выполнение послдней команды
        when (tryActionManager.defaultActionType) {
            LastActionType.DIVIDE -> {
                if (!clickedCell.isPhantom && clickedCell.divide == null ) {
                    val newDividedCellPosition = cellSearchManager.getPositionForNewCell(
                        clickedCellIndex = clickedIndex,
                        symmetryManager = symmetryManager
                    )
                    tryActionManager.defaultAction?.let {
                        tryActionManager.tryToDivide(clickedIndex, newDividedCellPosition, it.copy(
                            id = -1,
                            angle = null,
                            physicalLink = hashMapOf()
                        ))
                    }
                }
            }
            LastActionType.MUTATE -> {
                tryActionManager.defaultAction?.let {
                    tryActionManager.tryToMutate(
                        clickedIndex, it.copy(
                            id = -1,
                            angle = null,
                            physicalLink = hashMapOf()
                        )
                    )
                }
            }
            LastActionType.CHANGE -> {
                if (clickedCell.isPhantom && clickedCell.divide != null) {
                    tryActionManager.defaultAction?.let {
                        tryActionManager.tryToChange(clickedIndex, it.copy(
                            id = -1,
                            angle = null,
                            physicalLink = hashMapOf()
                        ))
                    }
                }
            }
            LastActionType.DELETE -> {
                if (clickedCell.isPhantom && clickedCell.divide != null) {
                    tryActionManager.tryToRemove(clickedIndex)
                }
            }
            null -> {}
        }
    }
}
