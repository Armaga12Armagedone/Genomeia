package io.github.some_example_name.old.editor.system.control

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.scenes.scene2d.Stage
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.editor.undo_redo_commands.AddNeuralLinkCommand
import io.github.some_example_name.old.editor.undo_redo_commands.ChangeDivideCommand
import io.github.some_example_name.old.editor.undo_redo_commands.DivideCellCommand
import io.github.some_example_name.old.editor.undo_redo_commands.MutateCellCommand
import io.github.some_example_name.old.editor.undo_redo_commands.RemoveCellCommand
import io.github.some_example_name.old.editor.system.logic.UiScreenCommands
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.currentStage
import io.github.some_example_name.old.editor.entities.CellReplay
import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.editor.entities.EyeReplay
import io.github.some_example_name.old.editor.entities.LinkReplay
import io.github.some_example_name.old.editor.entities.NeuralReplay
import io.github.some_example_name.old.editor.system.CellSearchManager
import io.github.some_example_name.old.editor.system.SymmetryManager
import io.github.some_example_name.old.editor.system.command.CommandEditorStackManager
import io.github.some_example_name.old.editor.system.logic.LastActionType
import io.github.some_example_name.old.editor.system.logic.ToEditorDataMapper
import io.github.some_example_name.old.editor.system.simulation.EditorSimulationSystem
import io.github.some_example_name.old.editor.ui.dialog.ChangeRemoveActionDialog
import io.github.some_example_name.old.editor.ui.dialog.DivideActionDialog
import io.github.some_example_name.old.editor.ui.dialog.MutateActionDialog
import io.github.some_example_name.old.editor.ui.dialog.MutateOrDivideDialog
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.game.MyGame

class LeftRightClickManager(
    val commandEditorStackManager: CommandEditorStackManager,
    val editorSimulationSystem: EditorSimulationSystem,
    val cellReplay: CellReplay,
    val linkReplay: LinkReplay,
    val eyeReplay: EyeReplay,
    val neuralReplay: NeuralReplay,
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val symmetryManager: SymmetryManager,
    val cellSearchManager: CellSearchManager,
    val toEditorDataMapper: ToEditorDataMapper
) {

    var uiScreenCommands: UiScreenCommands? = null
    private var defaultActionType: LastActionType? = null
    private var defaultAction: Action? = null

    private lateinit var camera: OrthographicCamera
    private lateinit var game: MyGame
    private var stage: Stage? = null

    fun bindToScreen(
        camera: OrthographicCamera,
        game: MyGame,
        stage: Stage
    ) {
        this.camera = camera
        this.game = game
        this.stage = stage
    }

    fun leftClick(clickedIndex: Int, clickedCell: EditorCell, isCtrl: Boolean) {

        val genomeStageInstruction = editorSimulationSystem.genome.genomeStageInstruction
        if (!isCtrl) {
            val newDividedCellPosition = cellSearchManager.tryToDivideCell(
                clickedCellIndex = clickedIndex,
                symmetryManager = symmetryManager
            )

            when {
                clickedCell.isPhantom && clickedCell.divide != null -> {
                    //Change и remove
                    val dialogMutateOrDivide = ChangeRemoveActionDialog(
                        clickedCell = clickedCell,
                        divide = clickedCell.divide.copy(),
                        game = game,
                        bundle = DIGameGlobalContainer.bundle,
                        onRemove = {
                            tryToRemove(clickedIndex)
                            defaultActionType = LastActionType.DELETE
                            defaultAction = null
                        },
                        onChange = { divide ->
                            tryToChange(clickedIndex, divide.copy())
                            defaultActionType = LastActionType.CHANGE
                            defaultAction = divide.copy(
                                id = -1,
                                angle = null,
                                physicalLink = hashMapOf()
                            )
                        }
                    )
                    dialogMutateOrDivide.show(stage)
                }

                newDividedCellPosition != null && clickedCell.divide == null -> {
                    val dialogMutateOrDivide = MutateOrDivideDialog(
                        clickedCell = clickedCell,
                        onMutate = {
                            showMutateDialog(
                                clickedCell,
                                clickedIndex
                            )
                        },
                        onDivide = {
                            showDivideDialog(
                                clickedCell,
                                clickedIndex,
                                newDividedCellPosition
                            )
                        },
                        game = game,
                        bundle = DIGameGlobalContainer.bundle
                    )
                    dialogMutateOrDivide.show(stage)
                }
                else -> {
                    showMutateDialog(
                        clickedCell,
                        clickedIndex
                    )
                }
            }
        } else {
            if (DIGenomeEditorContainer.previousCtrlClicked != -1 && DIGenomeEditorContainer.previousCtrlClicked != clickedIndex) {

                val cellFrom = toEditorDataMapper.mapToEditorData(DIGenomeEditorContainer.previousCtrlClicked)
                val cellTo = toEditorDataMapper.mapToEditorData(clickedIndex)
                val linkIndex = linkEntity.linkIndexMap.get(DIGenomeEditorContainer.previousCtrlClicked, clickedIndex)

                val nextStageTick = editorSimulationSystem.tickByStage[(DIGenomeEditorContainer.currentStage + 1).coerceIn(0,
                    DIGenomeEditorContainer.lastStage
                )]

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
                        currentStage = DIGenomeEditorContainer.currentStage,
                        cellFrom = cellFrom,
                        cellTo = cellTo,
                        stageInstruction = genomeStageInstruction,
                        doesNeedAddNewStage = genomeStageInstruction.size <= DIGenomeEditorContainer.currentStage,
                        isNeural = isNeural,
                        isLink1NeuralDirected = isLink1NeuralDirected,
                        isLongNeuralLink = isLongNeuralLink,
                        color = DIGenomeEditorContainer.linkColor,
                        linkId = linkIndex,
                        cellAId = if (linkIndex != -1) {
                            cellEntity.cellGenomeId[linkEntity.links1[linkIndex]]
                        } else cellFrom.id,
                        cellBId = if (linkIndex != -1) {
                            cellEntity.cellGenomeId[linkEntity.links2[linkIndex]]
                        } else cellTo.id
                    )
                )
            }
            DIGenomeEditorContainer.previousCtrlClicked = clickedIndex
        }
    }

    fun showMutateDialog(
        clickedCell: EditorCell,
        clickedIndex: Int
    ) {
        val dialogMutate = MutateActionDialog(
            clickedCell = clickedCell,
            parentCell = if (clickedCell.parentIndex != -1) toEditorDataMapper.mapToEditorData(
                clickedCell.parentIndex
            ) else null,
            startCurrentStageTick = editorSimulationSystem.tickByStage[DIGenomeEditorContainer.currentStage],
            eyeReplay = eyeReplay,
            neuralReplay = neuralReplay,
            cellReplay = cellReplay,
            clickedIndex = clickedIndex,
            game = game,
            bundle = DIGameGlobalContainer.bundle,
            onMutate = { action ->
                defaultActionType = LastActionType.MUTATE
                defaultAction = action.copy(
                    id = -1,
                    angle = null,
                    physicalLink = hashMapOf()
                )
//                println(action.toString())
                tryToMutate(clickedIndex, action.copy())
            }
        )
        dialogMutate.show(stage)
    }


    fun showDivideDialog(
        clickedCell: EditorCell,
        clickedIndex: Int,
        newDividedCellPosition: Pair<Float, Float>
    ) {
        val dialogDivide = DivideActionDialog(
            clickedCell = clickedCell,
            newDividedCellPosition = newDividedCellPosition,
            game = game,
            bundle = DIGameGlobalContainer.bundle,
            onDivide = { action ->
                defaultActionType = LastActionType.DIVIDE
                defaultAction = action.copy(
                    id = -1,
                    angle = null,
                    physicalLink = hashMapOf()
                )
//                println(action.toString())
                tryToDivide(clickedIndex, newDividedCellPosition, action.copy())
            }
        )
        dialogDivide.show(stage)
    }

    fun rightClick(clickedIndex: Int, clickedCell: EditorCell) {
        when (defaultActionType) {
            LastActionType.DIVIDE -> {
                if (!clickedCell.isPhantom && clickedCell.divide == null ) {
                    val newDividedCellPosition = cellSearchManager.tryToDivideCell(
                        clickedCellIndex = clickedIndex,
                        symmetryManager = symmetryManager
                    )
                    defaultAction?.let {
                        tryToDivide(clickedIndex, newDividedCellPosition, it.copy(
                            id = -1,
                            angle = null,
                            physicalLink = hashMapOf()
                        ))
                    }
                }
            }
            LastActionType.MUTATE -> {
                defaultAction?.let { tryToMutate(clickedIndex, it.copy(
                    id = -1,
                    angle = null,
                    physicalLink = hashMapOf()
                )) }
            }
            LastActionType.CHANGE -> {
                if (clickedCell.isPhantom && clickedCell.divide != null) {
                    defaultAction?.let {
                        tryToChange(clickedIndex, it.copy(
                            id = -1,
                            angle = null,
                            physicalLink = hashMapOf()
                        ))
                    }
                }
            }
            LastActionType.DELETE -> {
                if (clickedCell.isPhantom && clickedCell.divide != null) {
                    tryToRemove(clickedIndex)
                }
            }
            null -> {}
        }
    }

    private fun tryToMutate(clickedCellIndex: Int, action: Action) {
        val genomeStageInstruction = editorSimulationSystem.genome.genomeStageInstruction

        commandEditorStackManager.executeCommand(
            command = MutateCellCommand(
                currentStage = currentStage,
                action = action,
                clickedCell = toEditorDataMapper.mapToEditorData(clickedCellIndex),
                stageInstruction = genomeStageInstruction,
                doesNeedAddNewStage = genomeStageInstruction.size <= currentStage,
            )
        )
    }

    private fun tryToChange(
        clickedIndex: Int,
        divide: Action
    ) {
        val genomeStageInstruction = editorSimulationSystem.genome.genomeStageInstruction
        commandEditorStackManager.executeCommand(
            command = ChangeDivideCommand(
                currentStage = currentStage,
                clickedCell = toEditorDataMapper.mapToEditorData(clickedIndex),
                divide = divide,
                stageInstruction = genomeStageInstruction
            )
        )
    }

    private fun tryToRemove(clickedCellIndex: Int) {
        val genomeStageInstruction = editorSimulationSystem.genome.genomeStageInstruction
        val clickedCell = toEditorDataMapper.mapToEditorData(clickedCellIndex)
        commandEditorStackManager.executeCommand(
            command = RemoveCellCommand(
                currentStage = currentStage,
                clickedCell = clickedCell,
                parentCell = toEditorDataMapper.mapToEditorData(clickedCell.parentIndex),
                stageInstruction = genomeStageInstruction
            )
        )
    }

    private fun tryToDivide(
        clickedCellIndex: Int,
        newDividedCellPosition: Pair<Float, Float>?,
        action: Action
    ) {
        if (newDividedCellPosition == null) return
//        pikSounds.random().play(GlobalSettings.SOUND_VOLUME / 100f)
        val isLastTick = DIGenomeEditorContainer.currentTick == DIGenomeEditorContainer.lastTick

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
                doesNeedAddNewStage = genomeStageInstruction.size <= DIGenomeEditorContainer.currentStage,
                stageInstruction = genomeStageInstruction,
                currentStage = DIGenomeEditorContainer.currentStage,
            )
        )

//        if (isLastTick)
//            putUiCommand(NextTickButtonTap)
    }

}
