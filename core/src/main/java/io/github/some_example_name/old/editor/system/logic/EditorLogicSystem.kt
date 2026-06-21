package io.github.some_example_name.old.editor.system.logic

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.scenes.scene2d.Stage
import io.github.some_example_name.old.core.utils.setMinMaxDistForChildCellToParent
import io.github.some_example_name.old.editor.system.command.CommandEditorStackManager
import io.github.some_example_name.old.editor.undo_redo_commands.MoveCellCommand
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.currentStage
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.currentTick
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.grabbedCellIndex
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.isRightClick
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.lastGrabbedCellX
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.lastGrabbedCellY
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.lastStage
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.lastTick
import io.github.some_example_name.old.editor.entities.CellReplay
import io.github.some_example_name.old.editor.system.CellSearchManager
import io.github.some_example_name.old.editor.system.control.LeftRightClickManager
import io.github.some_example_name.old.editor.system.simulation.EditorSimulationSystem
import io.github.some_example_name.old.editor.system.SymmetryManager
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.systems.physics.GridManager
import io.github.some_example_name.old.game.MyGame
import kotlin.system.measureNanoTime

class EditorLogicSystem(
    val commandEditorStackManager: CommandEditorStackManager,
    val editorSimulationSystem: EditorSimulationSystem,
    val cellReplay: CellReplay,
    val cellEntity: CellEntity,
    val particleEntity: ParticleEntity,
    val linkEntity: LinkEntity,
    val symmetryManager: SymmetryManager,
    val gridManager: GridManager,
    val cellSearchManager: CellSearchManager,
    val toEditorDataMapper: ToEditorDataMapper,
    val leftRightClickManager: LeftRightClickManager
): RestartSimulationCallBack {

    private var isDruggingCamera = false
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
        leftRightClickManager.bindToScreen(camera, game, stage)
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
    }

    fun putUiCommand(command: UiEditorCommands) {
        when (command) {
            CtrlY -> commandEditorStackManager.redo()

            CtrlZ -> commandEditorStackManager.undo()

            is TouchDown -> {
                val clickedCell = cellSearchManager.getClickedCellIndex(
                    clickX = command.x,
                    clickY = command.y
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

                    val grabbedEditorCell = toEditorDataMapper.mapToEditorData(grabbedCellIndex)

                    val (x, y) = symmetryManager.snapPosition(
                        particleEntity.x[grabbedCellIndex],
                        particleEntity.y[grabbedCellIndex],
                        cellIndex = grabbedCellIndex
                    )

                    particleEntity.x[grabbedCellIndex] = x
                    particleEntity.y[grabbedCellIndex] = y

                    val newX = particleEntity.x[grabbedCellIndex]
                    val newY = particleEntity.y[grabbedCellIndex]
                    val parentIndex = cellEntity.parentIndex[grabbedCellIndex]

                    val oldNeighboursIds = cellSearchManager.getAllCloseNeighboursEditor(
                        lastGrabbedCellX,
                        lastGrabbedCellY,
                        grabbedRadius = particleEntity.radius[grabbedCellIndex],
                        grabbedCellIndex,
                    )
                    val oldNeighboursJustAdded = oldNeighboursIds.map { id ->
                        toEditorDataMapper.mapToEditorData(id)
                    }

                    val newNeighboursIds = cellSearchManager.getAllCloseNeighboursEditor(
                        newX,
                        newY,
                        grabbedRadius = particleEntity.radius[grabbedCellIndex],
                        grabbedCellIndex
                    )

                    val newNeighbours = newNeighboursIds.map { id ->
                        toEditorDataMapper.mapToEditorData(id)
                    }

                    commandEditorStackManager.executeCommand(MoveCellCommand(
                        grabbedEditorCell = grabbedEditorCell,
                        parentEditorCell = toEditorDataMapper.mapToEditorData(parentIndex),
                        oldNeighboursJustAdded = oldNeighboursJustAdded,
                        newNeighbours = newNeighbours,
                        newX = newX,
                        newY = newY,
                        currentStage = currentStage,
                        stageInstruction = genomeStageInstruction
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
                cellSearchManager.getClickedCellIndex(
                    clickX = command.x,
                    clickY = command.y
                )?.let {
                    val clickedIndex = it.first
                    val clickedCell = toEditorDataMapper.mapToEditorData(clickedIndex)
                    grabbedCellIndex = -1
                    if (Gdx.app.type == Application.ApplicationType.Desktop) {
                        if (command.isLeft) {
                            leftRightClickManager.leftClick(clickedIndex, clickedCell, command.isCtrl)
                        } else {
                            if (!command.isCtrl) {
                                leftRightClickManager.rightClick(clickedIndex, clickedCell)
                            }
                        }
                    } else {
                        if (!command.isCtrl) {
                            if (isRightClick) {
                                leftRightClickManager.rightClick(clickedIndex, clickedCell)
                            } else {
                                leftRightClickManager.leftClick(clickedIndex, clickedCell, false)
                            }
                        } else {
                            leftRightClickManager.leftClick(clickedIndex, clickedCell, true)
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

    fun dispose() {
        stage = null
    }

}
