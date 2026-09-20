package io.github.some_example_name.old.editor.ui

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisSlider
import com.kotcrab.vis.ui.widget.VisTextButton
import io.github.some_example_name.old.ui.screens.makeStyledButton
import io.github.some_example_name.old.ui.screens.makeStyledSlider
import com.kotcrab.vis.ui.widget.color.ColorPickerAdapter
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.core.FileProvider
import io.github.some_example_name.old.core.color_picker.ColorPicker
import io.github.some_example_name.old.editor.system.logic.CtrlY
import io.github.some_example_name.old.editor.system.logic.CtrlZ
import io.github.some_example_name.old.editor.system.logic.NextStageButtonTap
import io.github.some_example_name.old.editor.system.logic.NextTickButtonClamped
import io.github.some_example_name.old.editor.system.logic.NextTickButtonTap
import io.github.some_example_name.old.editor.system.logic.PrevStageButtonTap
import io.github.some_example_name.old.editor.system.logic.PrevTickButtonTap
import io.github.some_example_name.old.editor.system.logic.TimeSlider
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.isRightClick
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.lastTick
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.linkColor
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.previousCtrlClicked
import io.github.some_example_name.old.editor.system.logic.EditorLogicSystem
import io.github.some_example_name.old.editor.system.render.EditorRenderSystem
import io.github.some_example_name.old.editor.system.simulation.EditorSimulationSystem
import io.github.some_example_name.old.editor.system.SymmetryManager
import io.github.some_example_name.old.editor.ui.dialog.SymmetryDialog
import io.github.some_example_name.old.systems.genomics.genome.GenomeJsonReader
import io.github.some_example_name.old.systems.render.usePostProcess
import io.github.some_example_name.old.ui.screens.MenuScreen
import io.github.some_example_name.old.ui.screens.MyGame
import io.github.some_example_name.old.ui.screens.SimulationScreen
import io.github.some_example_name.old.ui.screens.applyCustomFont


class MenuUiBuilder(
    val game: MyGame,
    val stage: Stage,
    val editorLogicSystem: EditorLogicSystem,
    val genomeJsonReader: GenomeJsonReader,
    val renderSystem: EditorRenderSystem,
    val fileProvider: FileProvider,
    val editorSimulationSystem: EditorSimulationSystem,
    val symmetryManager: SymmetryManager
) {

    lateinit var timeSlider: VisSlider
    lateinit var stageText: VisLabel
    lateinit var tickText: VisLabel
    var isCtrl = false
    var isProgrammaticChange = false

    private val textures = mutableListOf<Texture>()

    fun dispose() {
        textures.forEach { it.dispose() }
        textures.clear()
    }

    fun setSliderValueProgrammatically(value: Float) {
        isProgrammaticChange = true
        timeSlider.value = value
        isProgrammaticChange = false
    }

    fun buildEditorMenu() {
        dispose()
        val density = Gdx.graphics.density
        stage.clear()
        val root = Table()
        root.setFillParent(true)
        stage.addActor(root)

        val prevStageButton = makeStyledButton(" << ", game, textures)
        val prevTickButton  = makeStyledButton(" < ",  game, textures)
        val nextTickButton  = makeStyledButton(" > ",  game, textures)
        val nextStageButton = makeStyledButton(" >> ", game, textures)
        stageText = VisLabel("0")
        game.applyCustomFont(stageText)
        stageText.setAlignment(Align.left)
        tickText = VisLabel("0")
        game.applyCustomFont(tickText)
        timeSlider = makeStyledSlider(0f, lastTick.toFloat(), 1f, false, textures)
        timeSlider.value = 0f

        timeSlider.addListener { event ->
            if (!isProgrammaticChange && event is ChangeListener.ChangeEvent) {
                editorLogicSystem.putUiCommand(
                    TimeSlider(
                        value = timeSlider.value.toInt(),
                        isDragging = timeSlider.isDragging
                    )
                )
            }
            false
        }

        val goToMenuButton = makeStyledButton(bundle.get("button.menu"), game, textures)
        goToMenuButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) { saveDialog(true) }
        })

        val chooseColorButton = makeStyledButton(bundle.get("button.saveGenome"), game, textures)
        chooseColorButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) { saveDialog(false) }
        })

        val showPhysicalLinkButton = makeStyledButton(bundle.get("button.showPhysicalLink"), game, textures, toggle = true)
        showPhysicalLinkButton.isChecked = renderSystem.showPhysicalLink


        showPhysicalLinkButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                renderSystem.showPhysicalLink = showPhysicalLinkButton.isChecked
            }
        })

        val usePostProcessLinkButton = makeStyledButton("Use post process", game, textures, toggle = true)
        usePostProcessLinkButton.isChecked = usePostProcess
        usePostProcessLinkButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                usePostProcess = usePostProcessLinkButton.isChecked
            }
        })

        val colorPicker = ColorPicker(
            game = game,
            title = bundle.get("button.chooseColorDialog"),
            listener = object : ColorPickerAdapter() {
                override fun changed(newColor: Color) {
                    val newColor = newColor.cpy()
                }

                override fun finished(newColor: Color?) {
                    super.finished(newColor)
                    if (newColor == null) return
                    val newColor = newColor.cpy()
                    linkColor = newColor
                }
            },
            colorInit = linkColor.cpy()
        )

        val neuralColorLinkButton = makeStyledButton("Neural link color", game, textures)

        // Toggle кнопка
        neuralColorLinkButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                stage.addActor(colorPicker)
                colorPicker.fadeIn()
            }
        })

        val symmetryButton = makeStyledButton("Symmetry", game, textures)
        symmetryButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                SymmetryDialog(game = game, bundle = bundle, symmetryManager = symmetryManager).show(stage)
            }
        })

        val ctrlZ = makeStyledButton("Ctrl+z", game, textures)
        ctrlZ.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                editorLogicSystem.putUiCommand(CtrlZ)
            }
        })

        val ctrlY = makeStyledButton("Ctrl+y", game, textures)
        ctrlY.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                editorLogicSystem.putUiCommand(CtrlY)
            }
        })

        val ctrl = makeStyledButton(bundle.get("button.neural-linking"), game, textures, toggle = true)
        ctrl.isChecked = isCtrl
        ctrl.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                isCtrl = ctrl.isChecked
                if (!isCtrl) previousCtrlClicked = -1
            }
        })

        val rightClick = makeStyledButton(bundle.get("button.performLastAction"), game, textures, toggle = true)
        rightClick.isChecked = isRightClick
        rightClick.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                isRightClick = rightClick.isChecked
            }
        })

        val buttons = mutableListOf(
            goToMenuButton, chooseColorButton, showPhysicalLinkButton, neuralColorLinkButton, usePostProcessLinkButton, symmetryButton
        )
        if (Gdx.app.type == Application.ApplicationType.Android) {
            buttons.add(ctrlZ)
            buttons.add(ctrlY)
            buttons.add(ctrl)
            buttons.add(rightClick)
        }

        val controls = Table()
        controls.defaults().pad(0f).space(0f)


        val topControls = Table()
        topControls.defaults().pad(8f * density).center()

        var currentWidth = 0f
        var rowTable = Table()
        rowTable.defaults().pad(8f * density).center()

        for (button in buttons) {
            val prefWidth = button.prefWidth + 16f * density
            if (currentWidth + prefWidth > Gdx.graphics.width && currentWidth > 0f) {
                topControls.add(rowTable).growX().center().row()
                rowTable = Table()
                rowTable.defaults().padLeft(8f * density).padRight(8f * density).center()
                currentWidth = 0f
            }
            rowTable.add(button).height(Gdx.graphics.height * 0.05f)
            currentWidth += prefWidth
        }
        if (rowTable.hasChildren()) {
            topControls.add(rowTable).growX().center()
        }


        controls.add(topControls).center().pad(16f * density).row()


        controls.add().growY().row()


        val labelsRow = Table()

        val labelsRow1 = Table()
        val labelsRow2 = Table()


        val tick = VisLabel(bundle.get("button.tick"))
        game.applyCustomFont(tick)
        labelsRow1.add(tick).padRight(4f * density).padLeft(40f * density)
        labelsRow1.add(tickText).size(40f * density, 30f * density).padRight(16f * density)
        val stage = VisLabel(bundle.get("button.stage"))
        game.applyCustomFont(stage)
        labelsRow2.add(stage).padRight(4f * density).padLeft(0f)
        labelsRow2.add(stageText).size(40f * density, 30f * density)

        labelsRow.add(labelsRow1)//.row()
        labelsRow.add(labelsRow2)

        controls.add(labelsRow).center().pad(8f * density).row()


        val sliderRow = Table()
        sliderRow.defaults().pad(0f).space(0f)
        val navBtnH = Gdx.graphics.height * 0.045f
        sliderRow.add(prevStageButton).height(navBtnH).padRight(8f * density)
        sliderRow.add(prevTickButton).height(navBtnH).padRight(8f * density)
        sliderRow.add(timeSlider).growX()
        sliderRow.add(nextTickButton).height(navBtnH).padLeft(8f * density)
        sliderRow.add(nextStageButton).height(navBtnH).padLeft(8f * density)


        controls.add(sliderRow).growX().pad(32f * density).row()


        root.add(controls).expand().fill()

        prevStageButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                editorLogicSystem.putUiCommand(PrevStageButtonTap)
            }
        })

        nextStageButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                editorLogicSystem.putUiCommand(NextStageButtonTap)
            }
        })

        prevTickButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                editorLogicSystem.putUiCommand(PrevTickButtonTap)
            }
        })

        nextTickButton.addListener(object : ClickListener() {
            private var pressStartTime: Long = 0
            private val longPressThreshold = 500L // 0.5 секунды
            private var isLongPressing = false
            private var longPressTask: Timer.Task? = null

            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                pressStartTime = System.currentTimeMillis()
                isLongPressing = false

                // Запускаем задачу для долгого нажатия
                longPressTask = Timer.schedule(object : Timer.Task() {
                    override fun run() {
                        isLongPressing = true
                        editorLogicSystem.putUiCommand(NextTickButtonClamped(false))
                    }
                }, 0.5f, 0.0083333f) // Старт через 0.5 сек, повтор каждые 50 мс

                return true
            }

            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                longPressTask?.cancel()
                longPressTask = null

                val pressDuration = System.currentTimeMillis() - pressStartTime
                if (!isLongPressing && pressDuration < longPressThreshold) {
                    editorLogicSystem.putUiCommand(NextTickButtonTap)
                } else if (isLongPressing) {
                    editorLogicSystem.putUiCommand(NextTickButtonClamped(true))
                }

                super.touchUp(event, x, y, pointer, button)
            }
        })
    }

    fun saveDialog(isGoToMenu: Boolean) {
        val genome = editorSimulationSystem.genome

        SaveGenomeDialog(
            genomeJsonReader = genomeJsonReader,
            genome = genome,
            onSaveAndTest = { genomeNameForTest ->
                game.screen.dispose()
                game.screen = SimulationScreen(
                    multiPlatformFileProvider = fileProvider,
                    game = game,
                    bundle = bundle,
                    map = null,
                    genomeName = genomeNameForTest
                )
            },
            onGoMenu = {
                game.screen.dispose()
                game.screen = MenuScreen(
                    multiPlatformFileProvider = fileProvider,
                    game = game,
                )
            },
            game = game,
            bundle = bundle,
            isGoToMenu = isGoToMenu
        ).show(stage)
    }

}

