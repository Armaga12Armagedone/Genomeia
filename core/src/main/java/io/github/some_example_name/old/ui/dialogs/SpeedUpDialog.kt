package io.github.some_example_name.old.ui.dialogs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.I18NBundle
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisLabel
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.ui.screens.MyGame
import io.github.some_example_name.old.ui.screens.applyCustomFont
import io.github.some_example_name.old.ui.screens.makeStyledButton
import io.github.some_example_name.old.ui.screens.makeStyledSlider
import io.github.some_example_name.old.ui.screens.valueChanged

class SpeedUpDialog(
    val game: MyGame,
    val bundle: I18NBundle
) : VisDialog("Speed up") {

    private val textures = mutableListOf<Texture>()

    init {
        val density = Gdx.graphics.density
        setupTitleSize(game)

        val btnH = Gdx.graphics.height * 0.055f

        // Label row
        val upsLabel = VisLabel("Target ups: ${DISimulationContainer.simulationData.targetUPS}")
        game.applyCustomFont(upsLabel)

        // Slider row (horizontal: label on left, slider on right)
        val upsSlider = makeStyledSlider(1f, 1000f, 1f, false, textures).apply {
            value = DISimulationContainer.simulationData.targetUPS.toFloat()
            addListener { e ->
                if (valueChanged(e)) {
                    DISimulationContainer.simulationData.targetUPS = value.toInt()
                    upsLabel.setText("Target ups: ${DISimulationContainer.simulationData.targetUPS}")
                }
                false
            }
        }

        // Max speed toggle + close in one horizontal row
        val maxSpeedToggle = makeStyledButton("Max speed", game, textures, toggle = true).apply {
            isChecked = DISimulationContainer.simulationData.maxSpeed
            addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
                override fun changed(event: ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                    DISimulationContainer.simulationData.maxSpeed = isChecked
                }
            })
        }

        val closeBtn = makeStyledButton(bundle.get("button.close") ?: "Close", game, textures).apply {
            addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
                override fun changed(event: ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                    fadeOut()
                }
            })
        }

        val pad = 8f * density

        contentTable.pad(pad)
        contentTable.add(upsLabel).left().padBottom(4f * density).row()
        contentTable.add(upsSlider).fillX().minWidth(280f * density).padBottom(pad).row()

        val btnRow = com.kotcrab.vis.ui.widget.VisTable()
        btnRow.add(maxSpeedToggle).height(btnH).padRight(pad)
        btnRow.add(closeBtn).height(btnH)
        contentTable.add(btnRow).center()

        closeOnEscape()
        pack()
        centerWindow()
    }

    override fun hide(action: com.badlogic.gdx.scenes.scene2d.Action?) {
        super.hide(action)
        addAction(Actions.sequence(
            Actions.delay(0.3f),
            Actions.run { textures.forEach { it.dispose() }; textures.clear() }
        ))
    }
}
