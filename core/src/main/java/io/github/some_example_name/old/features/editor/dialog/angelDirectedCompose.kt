package io.github.some_example_name.old.features.editor.dialog

import com.kotcrab.vis.ui.widget.VisSlider
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.editor.system.ActionDialogSystem
import io.github.some_example_name.old.core.ui.visLabel
import io.github.some_example_name.old.core.ui.visSlider
import io.github.some_example_name.old.core.ui.visTable
import io.github.some_example_name.old.core.ui.visTextButton
import kotlin.math.PI
import kotlin.math.roundToInt


fun VisTable.angelDirectedCompose(actionSystem: ActionDialogSystem, getCircleWidgetFrom: () -> CircleWidget?) {
    val angleAction = (actionSystem.angleDirected * (180 / PI.toFloat()) * 10).roundToInt() / 10.0f

    val angleLabel = visLabel(text = "${bundle.get("button.angle")}: $angleAction", font = game.extraLargeFont)
    row()

    visTable({ growX().expandX() }) {

        var angleSlider: VisSlider? = null

        fun changeAngle(delta: Float) {
            angleSlider?.let {
                val newValue = (it.value + delta).coerceIn(-180f, 180f)
                it.value = newValue
            }
        }

        visTextButton("<<", onClick = { changeAngle(-1f) })
        visTextButton("<", onClick = { changeAngle(-0.1f) })

        angleSlider = visSlider(
            min = -180f,
            max = 180f,
            step = 0.1f,
            value = angleAction,
            onValueChange = { newValue ->
                val angle = (newValue * 10).roundToInt() / 10f
                angleLabel.setText("${bundle.get("button.angle")}: $angle")

                val radianAngle = angle * (Math.PI.toFloat() / 180f)
                actionSystem.action = actionSystem.action.copy(angleDirected = radianAngle)
                getCircleWidgetFrom.invoke()?.setAngle(radianAngle + actionSystem.angleParent)
            }
        ) {
            growX().expandX()
        }

        visTextButton(">", onClick = { changeAngle(0.1f) })
        visTextButton(">>", onClick = { changeAngle(1f) })
    }
    row()
}
