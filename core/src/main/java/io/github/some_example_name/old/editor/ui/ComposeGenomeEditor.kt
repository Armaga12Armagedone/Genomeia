package io.github.some_example_name.old.editor.ui

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisSlider
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.currentTick
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.isRightClick
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.lastTick
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.previousCtrlClicked
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.symmetryManager
import io.github.some_example_name.old.editor.system.logic.CtrlY
import io.github.some_example_name.old.editor.system.logic.CtrlZ
import io.github.some_example_name.old.editor.system.logic.EditorLogicSystem
import io.github.some_example_name.old.editor.system.logic.GoToEndOfTimeLine
import io.github.some_example_name.old.editor.system.logic.GoToStartOfTimeLine
import io.github.some_example_name.old.editor.system.logic.NextTickButtonTap
import io.github.some_example_name.old.editor.system.logic.PrevTickButtonTap
import io.github.some_example_name.old.editor.system.logic.TimeSlider
import io.github.some_example_name.old.editor.system.render.EditorRenderSystem
import io.github.some_example_name.old.editor.system.simulation.EditorSimulationSystem
import io.github.some_example_name.old.editor.ui.dialog.SymmetryDialog
import io.github.some_example_name.old.ui.core.FlowAlignment
import io.github.some_example_name.old.ui.core.STYLE_DARK
import io.github.some_example_name.old.ui.core.dp
import io.github.some_example_name.old.ui.core.globalVisTable
import io.github.some_example_name.old.ui.core.visFlowRow
import io.github.some_example_name.old.ui.core.visLabel
import io.github.some_example_name.old.ui.core.visLeftArrowButton
import io.github.some_example_name.old.ui.core.visSelectBox
import io.github.some_example_name.old.ui.core.visSlider
import io.github.some_example_name.old.ui.core.visTable
import io.github.some_example_name.old.ui.core.visTextButton
import io.github.some_example_name.old.ui.core.visToggleButton

class ComposeGenomeEditor {

    var isCtrl = false
    var isProgrammaticChange = false

    lateinit var tickLabel: VisLabel
    lateinit var timeSlider: VisSlider

    fun composeGenomeEditor(
        stage: Stage,
        editorSimulationSystem: EditorSimulationSystem,
        renderSystem: EditorRenderSystem,
        editorLogicSystem: EditorLogicSystem
    ) {
        val rootTable = globalVisTable {
            setFillParent(true)

            add().expandX().fillX().padTop(16.dp())
            row()

            visFlowRow(
                alignment = FlowAlignment.Center,
                horizontalSpacing = 12.dp(),
                verticalSpacing = 8.dp()
            ) {
                visLeftArrowButton(onClick = {
                    stage.saveDialog(isGoToMenu = true, genome = editorSimulationSystem.genome)
                })

                visTextButton(bundle.get("button.saveGenome"), onClick = {
                    stage.saveDialog(isGoToMenu = false, genome = editorSimulationSystem.genome)
                })

                visToggleButton(
                    text = bundle.get("button.showPhysicalLink"),
                    checked = renderSystem.showPhysicalLink,
                    onCheckedChange = {
                        renderSystem.showPhysicalLink = it
                    }
                )

                visTextButton("Neural link color", onClick = {
                    stage.colorPickerDialog()
                })

                visTextButton("Symmetry", onClick = {
                    SymmetryDialog(symmetryManager).show(stage)
                })

                if (Gdx.app.type == Application.ApplicationType.Android) {
                    visToggleButton(
                        text = bundle.get("button.neural-linking"),
                        checked = isCtrl,
                        onCheckedChange = {
                            isCtrl = it
                            if (!isCtrl) previousCtrlClicked = -1
                        }
                    )

                    visToggleButton(
                        text = bundle.get("button.performLastAction"),
                        checked = isRightClick,
                        onCheckedChange = {
                            isRightClick = it
                        }
                    )
                }

                visSelectBox(arrayOf("Main genome", "arm", "head", "tail", "neuron"))

            }
            row()

            add().expand().fill()
            row()

            visTable(
                cellInit = {
                    expandX().fillX().bottom()
                    pad(20.dp(), 16.dp(), 0.dp(), 16.dp())
                }
            ) {
                defaults().pad(8.dp()).center()

                visTextButton("Undo", onClick = {
                    editorLogicSystem.putUiCommand(CtrlZ)
                })

                tickLabel = visLabel(text = "${bundle.get("button.tick")}0", textColor = STYLE_DARK, align = Align.center) {
                    expandX().fillX()
                    center()
                }

                visTextButton("Redo", onClick = {
                    editorLogicSystem.putUiCommand(CtrlY)
                })
            }

            row()

            visTable(
                cellInit = {
                    expandX().fillX().bottom()
                    pad(0.dp(), 16.dp(), 20.dp(), 16.dp())
                }
            ) {
                defaults().pad(8.dp()).center()

                visTextButton("<<", onClick = {
                    editorLogicSystem.putUiCommand(GoToStartOfTimeLine)
                })

                visTextButton("<", onClick = {
                    editorLogicSystem.putUiCommand(PrevTickButtonTap)
                })

                timeSlider = visSlider(
                    min = 0f,
                    max = lastTick.toFloat(),
                    step = 1f,
                    value = currentTick.toFloat(),
                    onValueChange = { newValue ->
                        if (!isProgrammaticChange) {
                            editorLogicSystem.putUiCommand(
                                TimeSlider(value = newValue.toInt())
                            )
                        }
                    }
                ) {
                    expandX().fillX()
                    pad(0f, 24.dp(), 0f, 24.dp())
                }

                visTextButton(">", onClick = {
                    editorLogicSystem.putUiCommand(NextTickButtonTap)
                })

                visTextButton(">>", onClick = {
                    editorLogicSystem.putUiCommand(GoToEndOfTimeLine)
                })
            }
        }
        stage.addActor(rootTable)
    }
}
