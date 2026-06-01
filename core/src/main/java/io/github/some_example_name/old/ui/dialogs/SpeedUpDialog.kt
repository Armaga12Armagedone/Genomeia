package io.github.some_example_name.old.ui.dialogs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.I18NBundle
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisSlider
import com.kotcrab.vis.ui.widget.VisTextButton
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.ui.screens.MyGame
import io.github.some_example_name.old.ui.screens.applyCustomFontMedium
import io.github.some_example_name.old.ui.screens.valueChanged

class SpeedUpDialog(
    val game: MyGame,
    val bundle: I18NBundle
) : VisDialog("Speed up") {

    init {
        val density = Gdx.graphics.density
        setupTitleSize(game)

        contentTable.defaults().left().pad(6f * density)

        val radiusLabel = VisLabel("Target ups: ${DISimulationContainer.simulationData.targetUPS}")
        game.applyCustomFontMedium(radiusLabel)
        val radiusSlider = VisSlider(1f, 1000f, 1f, false).apply {
            value = DISimulationContainer.simulationData.targetUPS.toFloat()
            addListener { e ->
                if (valueChanged(e)) {
                    DISimulationContainer.simulationData.targetUPS = value.toInt()
                    radiusLabel.setText("Target ups: ${DISimulationContainer.simulationData.targetUPS}")
                }
                false
            }
            invalidateHierarchy()
        }
        contentTable.add(radiusLabel).left()
        contentTable.row()
        contentTable.add(radiusSlider).fillX()
        contentTable.row()

        val speedUpSimToggle = VisTextButton("max speed", DISimulationContainer.roundStyleToggle)
        speedUpSimToggle.isChecked = DISimulationContainer.simulationData.maxSpeed
        speedUpSimToggle.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                DISimulationContainer.simulationData.maxSpeed = speedUpSimToggle.isChecked
            }
        })
        contentTable.add(speedUpSimToggle).row()

        closeOnEscape()
        pack()
    }
}
