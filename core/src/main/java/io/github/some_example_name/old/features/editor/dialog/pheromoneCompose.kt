package io.github.some_example_name.old.features.editor.dialog

import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.editor.system.ActionDialogSystem
import io.github.some_example_name.old.core.ui.dp
import io.github.some_example_name.old.core.ui.visLabel
import io.github.some_example_name.old.core.ui.visSelectBox
import io.github.some_example_name.old.core.ui.visTable


fun VisTable.pheromoneCompose(actionSystem: ActionDialogSystem) {
    visTable {
        visLabel(text = bundle.get("label.pheromoneType")) { padRight(16.dp())}

        val pheromoneType = actionSystem.action.pheromoneType ?: actionSystem.clickedCell.actual?.pheromoneType ?: throw Exception("pheromoneType is null")
        visSelectBox(
            items = Array(32) { i ->
                when (i) {
                    0 -> "Food - p$i"
                    11 -> "Stem grow - p$i"
                    18 -> "Dead cell - p$i"
                    else -> "p$i"
                }
            },
            selectedIndex = pheromoneType.coerceIn(0, 31),
            onChange = { _, pheromoneId ->
                actionSystem.action = actionSystem.action.copy(pheromoneType = pheromoneId)
            }
        )
    }
    row()
}
