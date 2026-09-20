package io.github.some_example_name.old.editor.system.logic

import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.systems.genomics.genome.Action

sealed interface UiEditorCommands

object PrevTickButtonTap: UiEditorCommands
object NextTickButtonTap: UiEditorCommands
object CtrlZ: UiEditorCommands
object CtrlY: UiEditorCommands

class PanScreen(
    val x: Float,
    val y: Float,
    val deltaX: Float,
    val deltaY: Float
): UiEditorCommands

object FlingScreen: UiEditorCommands

class TapScreen(
    val x: Float,
    val y: Float,
    val isLeft: Boolean,
    val isCtrl: Boolean
): UiEditorCommands

class TimeSlider(
    val value: Int
): UiEditorCommands

object GoToEndOfTimeLine: UiEditorCommands
object GoToStartOfTimeLine: UiEditorCommands

class TouchDown(val x: Float, val y: Float): UiEditorCommands

class DivideDialog(
    val clickedCell: EditorCell,
    val newDividedCellPosition: Pair<Float, Float>
): UiEditorCommands

class MutateDialog(
    val clickedCell: EditorCell,
    val parentCell: EditorCell?,
    val currentTick: Int
): UiEditorCommands



class TryToRemove(
    val clickedCellIndex: Int
): UiEditorCommands

class TryToChange(
    val clickedCellIndex: Int,
    val divide: Action
): UiEditorCommands

class TryToMutate(
    val clickedCellIndex: Int,
    val mutate: Action
): UiEditorCommands

class TryToDivide(
    val clickedCellIndex: Int,
    val newDividedCellPosition: Pair<Float, Float>,
    val divide: Action
): UiEditorCommands
