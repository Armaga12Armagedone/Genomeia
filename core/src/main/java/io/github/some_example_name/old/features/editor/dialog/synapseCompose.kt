package io.github.some_example_name.old.features.editor.dialog

import com.kotcrab.vis.ui.util.FloatDigitsOnlyFilter
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.cells.Synapse
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.core.ui.dp
import io.github.some_example_name.old.core.ui.hoverPopup
import io.github.some_example_name.old.core.ui.visLabel
import io.github.some_example_name.old.core.ui.visTable
import io.github.some_example_name.old.core.ui.visTextField
import io.github.some_example_name.old.editor.system.ActionDialogSystem

/**
 * Параметры синапса вместо a, b, c обычного нейрона. Хранятся они в тех же полях
 * (a = decayRate, b = learningRate, c = depression), см. Synapse.
 *
 * Пустое поле — значение по умолчанию. У learningRate и depression ноль тоже означает
 * «по умолчанию» (так их видит симуляция), поэтому ноль показывается пустым полем.
 *
 * Пояснения к полям не занимают место в диалоге, а всплывают при наведении мыши
 * или при зажатии пальцем — см. hoverPopup.
 */
fun VisTable.synapseCompose(actionSystem: ActionDialogSystem) {
    val actual = actionSystem.clickedCell.actual
    var decayRate = actionSystem.action.a ?: actual?.a ?: Synapse.DEFAULT_DECAY_RATE
    var learningRate = actionSystem.action.b ?: actual?.b ?: Synapse.DEFAULT_LEARNING_RATE
    var depression = actionSystem.action.c ?: actual?.c ?: Synapse.DEFAULT_DEPRESSION

    fun windowText() = bundle.format(
        "synapse.window",
        Synapse.learningWindowTicks(
            Synapse.decayRate(decayRate),
            Synapse.learningRate(learningRate),
            Synapse.depression(depression)
        )
    )

    // Метка окна стоит под полями, а обновляется из них — поэтому ссылка заводится заранее.
    var windowLabel: VisLabel? = null
    fun updateWindow() = windowLabel?.setText(windowText())

    visTable({ left() }) {
        synapseParam(
            name = bundle.get("synapse.learningRate"),
            hint = bundle.get("synapse.learningRate.hint"),
            value = learningRate,
            default = Synapse.DEFAULT_LEARNING_RATE,
            zeroMeansDefault = true
        ) {
            learningRate = it
            actionSystem.action = actionSystem.action.copy(b = it)
            updateWindow()
        }

        synapseParam(
            name = bundle.get("synapse.depression"),
            hint = bundle.get("synapse.depression.hint"),
            value = depression,
            default = Synapse.DEFAULT_DEPRESSION,
            zeroMeansDefault = true
        ) {
            depression = it
            actionSystem.action = actionSystem.action.copy(c = it)
            updateWindow()
        }

        synapseParam(
            name = bundle.get("synapse.decayRate"),
            hint = bundle.get("synapse.decayRate.hint"),
            value = decayRate,
            default = Synapse.DEFAULT_DECAY_RATE,
            zeroMeansDefault = false
        ) {
            decayRate = it
            actionSystem.action = actionSystem.action.copy(a = it)
            updateWindow()
        }
    }
    row()

    windowLabel = visLabel(windowText()) { left().padBottom(32.dp()) }
    row()
}

/** Строка «название + поле» с всплывающим пояснением. Отрицательные значения синапсу не нужны. */
private fun VisTable.synapseParam(
    name: String,
    hint: String,
    value: Float,
    default: Float,
    zeroMeansDefault: Boolean,
    onChange: (Float) -> Unit
) {
    // Пояснение висит и на названии, и на поле: на телефоне зажатие названия показывает
    // его, не открывая клавиатуру.
    visLabel("$name:", font = game.extraLargeFont) { left().padRight(16.dp()) }
        .hoverPopup { visLabel(hint) }
    visTextField(
        text = if (zeroMeansDefault && value <= 0f) "" else value.toString(),
        hint = default.toString(),
        textFieldFilter = FloatDigitsOnlyFilter(false),
        onTextChange = { text ->
            if (text.isBlank()) onChange(default) else text.toFloatOrNull()?.let(onChange)
        }
    ) { left() }
        .hoverPopup { visLabel(hint) }
    row()
}
