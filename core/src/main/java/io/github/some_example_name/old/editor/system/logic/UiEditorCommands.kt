package io.github.some_example_name.old.editor.system.logic

import io.github.some_example_name.old.commands.GameCommand
import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.editor.system.Axial
import io.github.some_example_name.old.editor.system.NoSymmetry
import io.github.some_example_name.old.editor.system.SquareGrid
import io.github.some_example_name.old.editor.system.SymmetryMode
import io.github.some_example_name.old.editor.system.TriangleGrid
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.render.RenderMode
import java.util.Locale

/**
 * Действие игрока в редакторе генома. Все они идут в EditorLogicSystem.putUiCommand —
 * это единственный вход, и там же они пишутся в журнал.
 */
sealed interface UiEditorCommands : GameCommand

object PrevTickButtonTap: UiEditorCommands {
    override val name get() = "PrevTick"
}

object NextTickButtonTap: UiEditorCommands {
    override val name get() = "NextTick"
}

object CtrlZ: UiEditorCommands {
    override val name get() = "Undo"
}

object CtrlY: UiEditorCommands {
    override val name get() = "Redo"
}

/** Перетаскивание: камеры, если под пальцем пусто, или схваченной клетки. */
class PanScreen(
    val x: Float,
    val y: Float,
    val deltaX: Float,
    val deltaY: Float
): UiEditorCommands {
    override val name get() = "Pan"
    override val detail get() = "${x.toInt()},${y.toInt()}"
}

/** Конец перетаскивания. Если тащили клетку — здесь она и переносится. */
object FlingScreen: UiEditorCommands {
    override val name get() = "Fling"
}

/**
 * Нажатие по полю.
 *
 * @param isLeft уже РЕШЁННОЕ «левый ли это клик». На телефоне кнопки мыши нет, и правым
 *   кликом считается нажатие в режиме «повторить последнее действие». Решается при
 *   создании команды, чтобы обработчик не зависел от платформы: иначе запись с телефона
 *   на десктопе повторилась бы по другой ветке.
 * @param isCtrl включён ли режим нейросвязей (Ctrl на десктопе, кнопка на телефоне).
 */
class TapScreen(
    val x: Float,
    val y: Float,
    val isLeft: Boolean,
    val isCtrl: Boolean
): UiEditorCommands {
    override val name get() = "Tap"
    override val detail
        get() = "${x.toInt()},${y.toInt()} ${if (isLeft) "L" else "R"}${if (isCtrl) " ctrl" else ""}"
}

class TimeSlider(
    val value: Int
): UiEditorCommands {
    override val name get() = "TimeSlider"
    override val detail get() = value.toString()
}

object GoToEndOfTimeLine: UiEditorCommands {
    override val name get() = "GoToEnd"
}

object GoToStartOfTimeLine: UiEditorCommands {
    override val name get() = "GoToStart"
}

class TouchDown(val x: Float, val y: Float): UiEditorCommands {
    override val name get() = "TouchDown"
    override val detail get() = "${x.toInt()},${y.toInt()}"
}

/** В диалоге «Деление или мутация» выбрали деление — показать диалог деления. */
class DivideDialog(
    val clickedCell: EditorCell,
    val newDividedCellPosition: Pair<Float, Float>
): UiEditorCommands {
    override val name get() = "DivideDialog"
    override val detail get() = "cell=${clickedCell.id}"
}

/** В диалоге «Деление или мутация» выбрали мутацию — показать диалог мутации. */
class MutateDialog(
    val clickedCell: EditorCell,
    val parentCell: EditorCell?,
    val currentTick: Int
): UiEditorCommands {
    override val name get() = "MutateDialog"
    override val detail get() = "cell=${clickedCell.id}"
}

class TryToRemove(
    val clickedCellIndex: Int
): UiEditorCommands {
    override val name get() = "Remove"
    override val detail get() = "index=$clickedCellIndex"
}

class TryToChange(
    val clickedCellIndex: Int,
    val divide: Action
): UiEditorCommands {
    override val name get() = "ChangeDivide"
    override val detail get() = "index=$clickedCellIndex"
}

class TryToMutate(
    val clickedCellIndex: Int,
    val mutate: Action
): UiEditorCommands {
    override val name get() = "Mutate"
    override val detail get() = "index=$clickedCellIndex"
}

class TryToCancelMutation(
    val clickedCellIndex: Int
): UiEditorCommands {
    override val name get() = "CancelMutation"
    override val detail get() = "index=$clickedCellIndex"
}

class TryToDivide(
    val clickedCellIndex: Int,
    val newDividedCellPosition: Pair<Float, Float>,
    val divide: Action
): UiEditorCommands {
    override val name get() = "Divide"
    override val detail get() = "index=$clickedCellIndex"
}

/**
 * Режим нейросвязей: Ctrl на десктопе, кнопка на телефоне. Выключение сбрасывает
 * начатую связь — клетку, с которой её начали тянуть.
 */
class SetNeuralLinking(val enabled: Boolean): UiEditorCommands {
    override val name get() = "SetNeuralLinking"
    override val detail get() = enabled.toString()
}

/** Телефон: нажатие повторяет последнее действие вместо диалога (аналог правого клика). */
class SetRepeatLastAction(val enabled: Boolean): UiEditorCommands {
    override val name get() = "SetRepeatLastAction"
    override val detail get() = enabled.toString()
}

/** Цвет новых нейросвязей, RGBA8888 — числом, а не Color: команда должна быть данными. */
class SetNeuralLinkColor(val rgba8888: Int): UiEditorCommands {
    override val name get() = "SetNeuralLinkColor"
    override val detail get() = Integer.toHexString(rgba8888)
}

/** Симметрия, по которой раскладываются новые и перенесённые клетки. */
class SetSymmetry(val mode: SymmetryMode): UiEditorCommands {
    override val name get() = "SetSymmetry"
    override val detail
        get() = when (mode) {
            NoSymmetry -> "none"
            Axial -> "axial"
            is SquareGrid -> "square(${mode.step})"
            is TriangleGrid -> "triangle(${mode.step})"
        }
}

class SetShowPhysicalLinks(val visible: Boolean): UiEditorCommands {
    override val name get() = "SetShowPhysicalLinks"
    override val detail get() = visible.toString()
}

class SetRenderMode(val mode: RenderMode): UiEditorCommands {
    override val name get() = "SetRenderMode"
    override val detail get() = mode.name
}

/** Записать текущий геном в файл под этим именем, запекши раскладку тела. */
class SaveGenome(val genomeName: String): UiEditorCommands {
    override val name get() = "SaveGenome"
    override val detail get() = genomeName
}

/** Android: отдать сохранённый файл генома системе (поделиться / сохранить в загрузки). */
class ExportGenome(val genomeName: String): UiEditorCommands {
    override val name get() = "ExportGenome"
    override val detail get() = genomeName
}

/** Масштаб камеры колесом или щипком: итоговые масштаб и положение, а не приращение. */
class ZoomCamera(val zoom: Float, val x: Float, val y: Float): UiEditorCommands {
    override val name get() = "ZoomCamera"
    override val detail get() = String.format(Locale.ROOT, "%.4f", zoom)
}
