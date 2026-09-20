package io.github.some_example_name.old.features.editor.dialog

import com.badlogic.gdx.graphics.Color
import com.kotcrab.vis.ui.widget.VisSlider
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.editor.system.ActionDialogSystem
import io.github.some_example_name.old.core.ui.dp
import io.github.some_example_name.old.core.ui.visLabel
import io.github.some_example_name.old.core.ui.visSlider
import io.github.some_example_name.old.core.ui.visTable
import io.github.some_example_name.old.core.ui.visTextButton
import io.github.some_example_name.old.core.ui.visToggleButton


fun VisTable.eyeCompose(actionSystem: ActionDialogSystem) {
    val distanceAction = (actionSystem.action.lengthDirected ?: actionSystem.clickedCell.actual?.lengthDirected ?: throw Exception("lengthDirected is null")) * 40f
    val distanceLabel = visLabel("${bundle.get("button.distance")} $distanceAction", font = game.extraLargeFont)
    row()

    var distanceSlider: VisSlider? = null

    fun changeAngle(delta: Float) {
        distanceSlider?.let { it ->
            it.value = (it.value + delta).coerceIn(26f, 1399f)
        }
    }

    visTable ({ growX().expandX() }) {
        visTextButton("<", onClick = { changeAngle(-1f) })

        distanceSlider = visSlider(25f, 1400f, 1f, value = distanceAction,
            onValueChange = {
                distanceLabel.setText("${bundle.get("button.distance")}: ${it.toInt()}")

                actionSystem.action = actionSystem.action.copy(lengthDirected = it / 40f)
            }) { growX().expandX() }

        visTextButton(">", onClick = { changeAngle(1f) })
    }
    row()

    visTable {
        visLabel(bundle.get("button.colorRecognition"), font = game.extraLargeFont) { padRight(16.dp()) }

        val color = getColorFromBits(actionSystem.action.colorRecognition ?: actionSystem.clickedCell.actual?.colorRecognition ?: throw Exception("colorRecognition is null"))

        lateinit var rButton: VisTextButton
        lateinit var gButton: VisTextButton
        lateinit var bButton: VisTextButton

        fun updateColor() {
            val r = if (rButton.isChecked) 1f else 0f
            val g = if (gButton.isChecked) 1f else 0f
            val b = if (bButton.isChecked) 1f else 0f
            val bits = encodeColorToBits(r, g, b)
            actionSystem.action = actionSystem.action.copy(colorRecognition = bits)
        }

        rButton = visToggleButton(
            text = "R",
            checked = color.r > 0f,
            onCheckedChange = { updateColor() }
        )

        gButton = visToggleButton(
            text = "G",
            checked = color.g > 0f,
            onCheckedChange = { updateColor() }
        )

        bButton = visToggleButton(
            text = "B",
            checked = color.b > 0f,
            onCheckedChange = { updateColor() }
        )
    }
    row()
}


fun getColorFromBits(bits: Int): Color {
    if (bits == 0) return Color.BLACK.cpy()

    var r = 0f
    var g = 0f
    var b = 0f
    var count = 0

    if (bits and 1 != 0) {
        r += 1f
        count++
    }
    if (bits and 2 != 0) {
        g += 1f
        count++
    }
    if (bits and 4 != 0) {
        b += 1f
        count++
    }

    return Color(r / count, g / count, b / count, 1f)
}

fun encodeColorToBits(r: Float, g: Float, b: Float): Int {
    var bits = 0
    if (r == 1f) bits = bits or 1
    if (g == 1f) bits = bits or 2
    if (b == 1f) bits = bits or 4
    return bits
}
