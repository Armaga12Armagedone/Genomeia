package io.github.some_example_name.old.editor.system

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.scenes.scene2d.Stage
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.core.utils.invSqrt
import io.github.some_example_name.old.core.utils.setMinMaxDistForChildCellToParent
import io.github.some_example_name.old.editor.commands.AddNeuralLinkCommand
import io.github.some_example_name.old.editor.commands.ChangeDivideCommand
import io.github.some_example_name.old.editor.commands.CommandEditorStackManager
import io.github.some_example_name.old.editor.commands.CtrlY
import io.github.some_example_name.old.editor.commands.CtrlZ
import io.github.some_example_name.old.editor.commands.DivideCellCommand
import io.github.some_example_name.old.editor.commands.FlingScreen
import io.github.some_example_name.old.editor.commands.GoToEndOfTimeLine
import io.github.some_example_name.old.editor.commands.MoveCellCommand
import io.github.some_example_name.old.editor.commands.MutateCellCommand
import io.github.some_example_name.old.editor.commands.NextStageButtonTap
import io.github.some_example_name.old.editor.commands.NextTickButtonClamped
import io.github.some_example_name.old.editor.commands.NextTickButtonTap
import io.github.some_example_name.old.editor.commands.PanScreen
import io.github.some_example_name.old.editor.commands.PrevStageButtonTap
import io.github.some_example_name.old.editor.commands.PrevTickButtonTap
import io.github.some_example_name.old.editor.commands.RemoveCellCommand
import io.github.some_example_name.old.editor.commands.TapScreen
import io.github.some_example_name.old.editor.commands.TimeSlider
import io.github.some_example_name.old.editor.commands.TouchDown
import io.github.some_example_name.old.editor.commands.UiEditorCommands
import io.github.some_example_name.old.editor.commands.UiScreenCommands
import io.github.some_example_name.old.editor.commands.tryToDivideCell
import io.github.some_example_name.old.editor.entities.CellReplay
import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.editor.entities.EyeReplay
import io.github.some_example_name.old.editor.entities.LinkReplay
import io.github.some_example_name.old.editor.entities.NeuralReplay
import io.github.some_example_name.old.editor.ui.dialog.ChangeRemoveActionDialog
import io.github.some_example_name.old.editor.ui.dialog.DivideActionDialog
import io.github.some_example_name.old.editor.ui.dialog.MutateActionDialog
import io.github.some_example_name.old.editor.ui.dialog.MutateOrDivideDialog
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.ui.screens.MyGame
import kotlin.math.atan2
import kotlin.system.measureNanoTime


enum class LastActionType {
    DIVIDE, MUTATE, CHANGE, DELETE
}

interface RestartSimulation {
    fun restartSimulation()
}

class EditorLogicSystem(
    val commandEditorStackManager: CommandEditorStackManager,
    val editorSimulationSystem: EditorSimulationSystem,
    val cellReplay: CellReplay,
    val linkReplay: LinkReplay,
    val eyeReplay: EyeReplay,
    val neuralReplay: NeuralReplay,
    val cellEntity: CellEntity,
    val particleEntity: ParticleEntity,
    val linkEntity: LinkEntity,
    val symmetryManager: SymmetryManager
): RestartSimulation {
    var currentTick = 0
    var currentStage = 0
    var lastTick = 0
    var lastStage = 0
    var grabbedCellIndex = -1
    var lastGrabbedCellX = 0.0f
    var lastGrabbedCellY = 0.0f
    private var isDruggingCamera = false
    var uiScreenCommands: UiScreenCommands? = null
    private var defaultActionType: LastActionType? = null
    private var defaultAction: Action? = null
    var previousCtrlClicked = -1
    private lateinit var camera: OrthographicCamera
    private lateinit var game: MyGame
    private var stage: Stage? = null

    init {
        commandEditorStackManager.bind(this)
    }

    fun bindToScreen(
        camera: OrthographicCamera,
        game: MyGame,
        stage: Stage
    ) {
        this.camera = camera
        this.game = game
        this.stage = stage
    }

    override fun restartSimulation() {

        val nanoTime = measureNanoTime {
            editorSimulationSystem.simulate()
        }
        println("simulate: ${nanoTime / 1_000_000.0} ms")
        lastTick = cellReplay.getTickCount() - 1
        lastStage = editorSimulationSystem.genome.genomeStageInstruction.size
        if (lastTick < currentTick) {
            currentTick = lastTick
            currentStage = editorSimulationSystem.stageByTick.getStage(currentTick)
        }

//        println(lastStage)
    }

    fun toEditorData(index: Int): EditorCell {
        val id = cellEntity.cellGenomeId[index]
        val currentCellIndex = cellReplay.getCellIndex(currentTick, index)
        val isPhantom = currentCellIndex == null
        val parentIndex = cellEntity.parentIndex[index]
        val parentId = cellEntity.cellGenomeId[parentIndex]
        val action = if (currentStage != lastStage){
            editorSimulationSystem.genome.genomeStageInstruction[currentStage]
                .cellActions[if (isPhantom) parentId else id]
        } else null

        val angleToParent = if (index != 0) {
            val dx = particleEntity.x[index] - particleEntity.x[parentIndex]
            val dy = particleEntity.y[index] - particleEntity.y[parentIndex]

            val len = 1f / invSqrt(dx * dx + dy * dy)
            val toChildCos = dx / len
            val toChildSin = dy / len

            atan2(toChildSin, toChildCos)
        } else 0f

        return EditorCell(
            id = id,
            parentIndex = parentIndex,
            parentId = parentId,
            x = particleEntity.x[index],
            y = particleEntity.y[index],
            radius = particleEntity.radius[index],
            isPhantom = isPhantom,
            angleToParent = angleToParent,
            divide = action?.divide,
            mutate = action?.mutate
        )
    }

    fun putUiCommand(command: UiEditorCommands) {
        when (command) {
            CtrlY -> commandEditorStackManager.redo()

            CtrlZ -> commandEditorStackManager.undo()

            is TouchDown -> {
                val clickedCell = editorSimulationSystem.getClickedCellIndex(
                    clickX = command.x,
                    clickY = command.y,
                    currentTick = currentTick,
                    nextStageTick = editorSimulationSystem.tickByStage[(currentStage + 1).coerceIn(0, lastStage)]
                )

                if (clickedCell != null && clickedCell.second) {
                    grabbedCellIndex = clickedCell.first
                    lastGrabbedCellX = particleEntity.x[grabbedCellIndex]
                    lastGrabbedCellY = particleEntity.y[grabbedCellIndex]
                    isDruggingCamera = false
                } else {
                    grabbedCellIndex = -1
                    lastGrabbedCellX = -1.0f
                    lastGrabbedCellY = -1.0f
                    isDruggingCamera = true
                }
            }

            is PanScreen -> {
                if (grabbedCellIndex == -1) {
                    if (isDruggingCamera) {
                        camera.translate(command.deltaX, command.deltaY, 0f)
                    }
                } else {
                    val parentIndex = cellEntity.parentIndex[grabbedCellIndex]
                    val parentCellX = particleEntity.x[parentIndex]
                    val parentCellY = particleEntity.y[parentIndex]

                    val (finalX, finalY) = setMinMaxDistForChildCellToParent(
                        command.x,
                        command.y,
                        parentCellX,
                        parentCellY
                    )

                    particleEntity.x[grabbedCellIndex] = finalX
                    particleEntity.y[grabbedCellIndex] = finalY
                }
            }

            FlingScreen -> {
                if (grabbedCellIndex != -1) {
                    val genomeStageInstruction = editorSimulationSystem.genome.genomeStageInstruction

                    val grabbedEditorCell = toEditorData(grabbedCellIndex)

                    val (x, y) = symmetryManager.snapPosition(particleEntity.x[grabbedCellIndex], particleEntity.y[grabbedCellIndex])

                    particleEntity.x[grabbedCellIndex] = x
                    particleEntity.y[grabbedCellIndex] = y

                    val newX = particleEntity.x[grabbedCellIndex]
                    val newY = particleEntity.y[grabbedCellIndex]
                    val parentIndex = cellEntity.parentIndex[grabbedCellIndex]

                    val oldNeighboursIds = editorSimulationSystem.getAllCloseNeighboursEditor(
                        lastGrabbedCellX,
                        lastGrabbedCellY,
                        grabbedRadius = particleEntity.radius[grabbedCellIndex],
                        grabbedCellIndex,
                        currentTick = currentTick,
                        nextStageTick = editorSimulationSystem.tickByStage[(currentStage + 1).coerceIn(0, lastStage)]
                    )
                    val oldNeighboursJustAdded = oldNeighboursIds.map { id ->
                        toEditorData(id)
                    }

                    val newNeighboursIds = editorSimulationSystem.getAllCloseNeighboursEditor(
                        newX,
                        newY,
                        grabbedRadius = particleEntity.radius[grabbedCellIndex],
                        grabbedCellIndex,
                        currentTick = currentTick,
                        nextStageTick = editorSimulationSystem.tickByStage[(currentStage + 1).coerceIn(0, lastStage)]
                    )

                    val newNeighbours = newNeighboursIds.map { id ->
                        toEditorData(id)
                    }

                    commandEditorStackManager.executeCommand(MoveCellCommand(
                        grabbedEditorCell = grabbedEditorCell,
                        parentEditorCell = toEditorData(parentIndex),
                        oldNeighboursJustAdded = oldNeighboursJustAdded,
                        newNeighbours = newNeighbours,
                        newX = newX,
                        newY = newY,
                        currentStage = currentStage,
                        autoLinking = true,
                        genomeStageInstruction = genomeStageInstruction
                    ))

                }
                grabbedCellIndex = -1
                lastGrabbedCellX = -1.0f
                lastGrabbedCellY = -1.0f
                isDruggingCamera = false
            }

            NextTickButtonTap -> {
                if (currentTick < cellReplay.getTickCount() - 1) {
                    currentTick++
                    currentStage = editorSimulationSystem.stageByTick.getStage(currentTick)
                }
            }

            PrevStageButtonTap -> {
                if (currentStage > 0) {
                    currentStage--
                    currentTick = editorSimulationSystem.tickByStage[currentStage]
                }
            }

            PrevTickButtonTap -> {
                if (currentTick > 0) {
                    currentTick--
                    currentStage = editorSimulationSystem.stageByTick.getStage(currentTick)
                }
            }

            NextStageButtonTap -> {
                if (currentStage < editorSimulationSystem.tickByStage.size - 1) {
                    currentStage++
                    currentTick = editorSimulationSystem.tickByStage[currentStage]
                }
            }

            is NextTickButtonClamped -> {
                if (command.isFinish) {
                    // Завершение долгого нажатия
                } else {
                    if (currentTick < cellReplay.getTickCount() - 1) {
                        currentTick++
                        currentStage = editorSimulationSystem.stageByTick.getStage(currentTick)
                    }
                }
            }

            is TapScreen -> {
                editorSimulationSystem.getClickedCellIndex(
                    clickX = command.x,
                    clickY = command.y,
                    currentTick = currentTick,
                    nextStageTick = editorSimulationSystem.tickByStage[(currentStage + 1).coerceIn(0, lastStage)]
                )?.let {
                    val clickedIndex = it.first
                    val clickedCell = toEditorData(clickedIndex)
                    grabbedCellIndex = -1
                    if (Gdx.app.type == Application.ApplicationType.Desktop) {
                        if (command.isLeft) {
                            leftClick(clickedIndex, clickedCell, command.isCtrl)
                        } else {
                            if (!command.isCtrl) {
                                rightClick(clickedIndex, clickedCell)
                            }
                        }
                    } else {
                        if (!command.isCtrl) {
                            if (!command.isLeft) {
                                rightClick(clickedIndex, clickedCell)
                            } else {
                                leftClick(clickedIndex, clickedCell, false)
                            }
                        } else {
                            leftClick(clickedIndex, clickedCell, true)
                        }
                    }
                }

                grabbedCellIndex = -1
                lastGrabbedCellX = -1.0f
                lastGrabbedCellY = -1.0f
                isDruggingCamera = false
            }

            GoToEndOfTimeLine -> {
                currentTick = cellReplay.tickStartIndices.size - 1
                currentStage = editorSimulationSystem.stageByTick.getStage(currentTick)
            }

            is TimeSlider -> {
                currentTick = command.value
                if (command.isDragging) {
                    currentTick = command.value
                    currentStage = editorSimulationSystem.stageByTick.getStage(currentTick)
                }
            }
        }
    }

    private fun leftClick(clickedIndex: Int, clickedCell: EditorCell, isCtrl: Boolean) {

        val genomeStageInstruction = editorSimulationSystem.genome.genomeStageInstruction
        if (!isCtrl) {
            val newDividedCellPosition = tryToDivideCell(
                clickedCellIndex = clickedIndex,
                gridManager = editorSimulationSystem.gridManager,
                editorLogicSystem = this,
                symmetryManager = symmetryManager
            )

            when {
                clickedCell.isPhantom && clickedCell.divide != null -> {
                    //Change и remove
                    val dialogMutateOrDivide = ChangeRemoveActionDialog(
                        clickedCell = clickedCell,
                        divide = clickedCell.divide.copy(),
                        game = game,
                        bundle = bundle,
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
                        bundle = bundle
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
            if (previousCtrlClicked != -1 && previousCtrlClicked != clickedIndex) {

                val cellFrom = toEditorData(previousCtrlClicked)
                val cellTo = toEditorData(clickedIndex)
                val linkId = linkEntity.linkIndexMap.get(previousCtrlClicked, clickedIndex)
                if (linkId != -1) {
                    val nextStageTick = editorSimulationSystem.tickByStage[(currentStage + 1).coerceIn(0, lastStage)]
                    commandEditorStackManager.executeCommand(
                        command = AddNeuralLinkCommand(
                            currentStage = currentStage,
                            cellFrom = cellFrom,
                            cellTo = cellTo,
                            genomeStageInstruction = genomeStageInstruction,
                            doesNeedAddNewStage = genomeStageInstruction.size <= currentStage,
                            isNeural = linkReplay.getLinkIsNeural(nextStageTick, linkId) ?: false,
                            parentId = cellEntity.cellGenomeId[linkEntity.links2[linkId]]//Не важно какой links2 или же link1, это нужно для выбора из какой клетки будет action мутации
                        )
                    )
                }
            }
            previousCtrlClicked = clickedIndex
        }
    }


    fun showMutateDialog(
        clickedCell: EditorCell,
        clickedIndex: Int
    ) {
        val dialogMutate = MutateActionDialog(
            clickedCell = clickedCell,
            parentCell = toEditorData(clickedCell.parentIndex),
            startCurrentStageTick = editorSimulationSystem.tickByStage[currentStage],
            eyeReplay = eyeReplay,
            neuralReplay = neuralReplay,
            cellReplay = cellReplay,
            clickedIndex = clickedIndex,
            game = game,
            bundle = bundle,
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
            bundle = bundle,
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

    private fun rightClick(clickedIndex: Int, clickedCell: EditorCell) {
        when (defaultActionType) {
            LastActionType.DIVIDE -> {
                if (!clickedCell.isPhantom && clickedCell.divide == null ) {
                    val newDividedCellPosition = tryToDivideCell(
                        clickedCellIndex = clickedIndex,
                        gridManager = editorSimulationSystem.gridManager,
                        editorLogicSystem = this,
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
                stage = currentStage,
                action = action,
                clickedCell = toEditorData(clickedCellIndex),
                genomeStageInstruction = genomeStageInstruction,
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
                stage = currentStage,
                clickedCell = toEditorData(clickedIndex),
                divide = divide,
                genomeStageInstruction = genomeStageInstruction
            )
        )
    }

    private fun tryToRemove(clickedCellIndex: Int) {
        val genomeStageInstruction = editorSimulationSystem.genome.genomeStageInstruction
        val clickedCell = toEditorData(clickedCellIndex)
        commandEditorStackManager.executeCommand(
            command = RemoveCellCommand(
                currentStage = currentStage,
                clickedCell = clickedCell,
                parentCell = toEditorData(clickedCell.parentIndex),
                genomeStageInstruction = genomeStageInstruction
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
        val isLastTick = currentTick == lastTick

        val genomeStageInstruction = editorSimulationSystem.genome.genomeStageInstruction

        val radius = 0.5f //TODO поменять когда будет выбор радиуса

        val neighboursIds = editorSimulationSystem.getAllCloseNeighboursEditor(
            grabbedX = newDividedCellPosition.first,
            grabbedY = newDividedCellPosition.second,
            grabbedRadius = radius,
            currentTick = currentTick,
            nextStageTick = editorSimulationSystem.tickByStage[(currentStage + 1).coerceIn(0, lastStage)]
        )
        val neighboursCells = neighboursIds.map {
            toEditorData(it)
        }

        commandEditorStackManager.executeCommand(
            command = DivideCellCommand(
                clickedCell = toEditorData(clickedCellIndex),
                neighboursCells = neighboursCells,
                divide = action,
                newId = editorSimulationSystem.maxCellId + 1,
                newPoint = newDividedCellPosition,
                doesNeedAddNewStage = genomeStageInstruction.size <= currentStage,
                genomeStageInstruction = genomeStageInstruction,
                currentStage = currentStage,
                autoLinking = true
            )
        )

//        if (isLastTick)
//            putUiCommand(NextTickButtonTap)
    }

    fun dispose() {
        stage = null
    }

}
