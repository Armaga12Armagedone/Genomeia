package io.github.some_example_name.old.features.editor.dialog

import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.editor.system.ActionDialogSystem
import io.github.some_example_name.old.systems.genomics.genome.Action

/**
 * Часть диалога клетки в редакторе генома: своя верстка и свои поля генома.
 *
 * Клетка перечисляет свои секции в Cell.editorSections(), и диалог собирается из них.
 * Общие секции (нейрон, направление, глаз, феромон) лежат здесь, в пакете диалога; секция,
 * нужная одной клетке, — в файле этой клетки, рядом с логикой (образцы: SynapseSection в
 * Synapse.kt, ControllerSection в Controller.kt). Новая клетка с нестандартным UI не
 * требует правок в ActionDialog и ActionDialogSystem.
 *
 * Секции — object'ы: смена типа клетки сравнивает списки секций по ссылке. Секция, которая
 * есть у обоих типов, свои поля сохраняет; которой нет у нового — чистит ([clear]);
 * которой не было у старого — ставит значения по умолчанию ([applyDefaults]).
 *
 * Верстка только собирает черновик Action в [ActionDialogSystem.action]; в геном он попадает
 * командой по кнопке диалога, как и раньше.
 */
interface CellEditorSection {

    /** Поставить свои поля генома по умолчанию: клетке выбрали тип с этой секцией. */
    fun applyDefaults(action: Action): Action = action

    /** Обнулить свои поля: у нового типа клетки этой секции нет. */
    fun clear(action: Action): Action = action

    /** Нарисовать секцию; каждая строка заканчивается row(). */
    fun VisTable.compose(dialog: CellDialog)

    /** Свои строки «было -> стало» в списке изменений мутации. */
    fun describeMutation(actual: Action, mutate: Action, out: StringBuilder) {}
}

/** Что диалог клетки даёт секциям. */
interface CellDialog {
    /** Черновик действия и клетка, по которой кликнули. */
    val actionSystem: ActionDialogSystem

    /** Кружок с превью клетки: секция направления крутит на нём стрелку. */
    val circleWidget: CircleWidget?
}
