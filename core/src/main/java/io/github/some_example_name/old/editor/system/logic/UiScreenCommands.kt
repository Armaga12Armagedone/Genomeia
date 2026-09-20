package io.github.some_example_name.old.editor.system.logic

import io.github.some_example_name.old.editor.entities.EditorCell

sealed interface UiScreenCommands

class ShowDivideDialog(
    val clickedCell: EditorCell,
    val newDividedCellPosition: Pair<Float, Float>
): UiScreenCommands

class ShowMutateDialog(
    val clickedCell: EditorCell,
    val parentCell: EditorCell?,
    val currentTick: Int
): UiScreenCommands

class ShowMutateOrDivideDialog(
    val clickedCell: EditorCell,
    val parentCell: EditorCell?,
    val newDividedCellPosition: Pair<Float, Float>,
    val currentTick: Int
): UiScreenCommands

class ShowChangeRemoveDialog(
    val clickedCell: EditorCell,
): UiScreenCommands
