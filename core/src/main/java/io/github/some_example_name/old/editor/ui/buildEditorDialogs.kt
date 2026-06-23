package io.github.some_example_name.old.editor.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Stage
import com.kotcrab.vis.ui.widget.color.ColorPickerAdapter
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.core.DIGameGlobalContainer.genomeJsonReader
import io.github.some_example_name.old.core.color_picker.ColorPicker
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.linkColor
import io.github.some_example_name.old.editor.entities.CellReplay
import io.github.some_example_name.old.editor.entities.EyeReplay
import io.github.some_example_name.old.editor.entities.NeuralReplay
import io.github.some_example_name.old.editor.system.logic.DivideDialog
import io.github.some_example_name.old.editor.system.logic.MutateDialog
import io.github.some_example_name.old.editor.system.logic.ShowChangeRemoveDialog
import io.github.some_example_name.old.editor.system.logic.ShowDivideDialog
import io.github.some_example_name.old.editor.system.logic.ShowMutateDialog
import io.github.some_example_name.old.editor.system.logic.ShowMutateOrDivideDialog
import io.github.some_example_name.old.editor.system.logic.TryToChange
import io.github.some_example_name.old.editor.system.logic.TryToDivide
import io.github.some_example_name.old.editor.system.logic.TryToMutate
import io.github.some_example_name.old.editor.system.logic.TryToRemove
import io.github.some_example_name.old.editor.ui.dialog.ChangeRemoveActionDialog
import io.github.some_example_name.old.editor.ui.dialog.DivideActionDialog
import io.github.some_example_name.old.editor.ui.dialog.MutateActionDialog
import io.github.some_example_name.old.editor.ui.dialog.MutateOrDivideDialog
import io.github.some_example_name.old.ui.screens.MenuScreen
import io.github.some_example_name.old.ui.screens.SimulationScreen
import io.github.some_example_name.old.systems.genomics.genome.Genome

fun Stage.changeRemoveActionDialog(
    command: ShowChangeRemoveDialog,
    onRemove: (TryToRemove) -> Unit,
    onChange: (TryToChange) -> Unit,
) {
    val dialogMutateOrDivide = ChangeRemoveActionDialog(
        clickedCell = command.clickedCell,
        divide = command.clickedCell.divide?.copy() ?: throw Exception("clickedCell.divide is null"),
        onRemove = { onRemove(TryToRemove(clickedCellIndex = command.clickedCell.index)) },
        onChange = { divide ->
            onChange(
                TryToChange(
                    clickedCellIndex = command.clickedCell.index,
                    divide = divide.copy()
                )
            )
        }
    )
    dialogMutateOrDivide.show(this)
}

fun Stage.mutateOrDivideDialog(
    command: ShowMutateOrDivideDialog,
    onMutate: (MutateDialog) -> Unit,
    onDivide: (DivideDialog) -> Unit
) {
    val dialogMutateOrDivide = MutateOrDivideDialog(
        clickedCell = command.clickedCell,
        onMutate = { onMutate.invoke(
            MutateDialog(
                clickedCell = command.clickedCell,
                parentCell = command.parentCell,
                currentTick = command.currentTick
            )
        ) },
        onDivide = { onDivide.invoke(
            DivideDialog(
                clickedCell = command.clickedCell,
                newDividedCellPosition = command.newDividedCellPosition
            )
        ) }
    )
    dialogMutateOrDivide.show(this)
}

fun Stage.mutateActionDialog(
    command: ShowMutateDialog,
    eyeReplay: EyeReplay,
    neuralReplay: NeuralReplay,
    cellReplay: CellReplay,
    onMutate: (TryToMutate) -> Unit
) {
    val dialogMutate = MutateActionDialog(
        clickedCell = command.clickedCell,
        parentCell = command.parentCell,
        startCurrentStageTick = command.currentTick,
        eyeReplay = eyeReplay,
        neuralReplay = neuralReplay,
        cellReplay = cellReplay,
        clickedIndex = command.clickedCell.index,
        onMutate = { action ->
            onMutate.invoke(
                TryToMutate(
                    clickedCellIndex = command.clickedCell.index,
                    mutate = action.copy()
                )
            )
        }
    )
    dialogMutate.show(this)
}

fun Stage.divideActionDialog(
    command: ShowDivideDialog,
    onDivide: (TryToDivide) -> Unit
) {
    val dialogDivide = DivideActionDialog(
        clickedCell = command.clickedCell,
        newDividedCellPosition = command.newDividedCellPosition,
        onDivide = { action ->
            onDivide.invoke(
                TryToDivide(
                    clickedCellIndex = command.clickedCell.index,
                    newDividedCellPosition = command.newDividedCellPosition,
                    divide = action.copy()
                )
            )
        }
    )
    dialogDivide.show(this)
}

fun Stage.colorPickerDialog() {
    val colorPicker = ColorPicker(
        title = bundle.get("button.chooseColorDialog"),
        listener = object : ColorPickerAdapter() {
            override fun changed(newColor: Color) {}

            override fun finished(newColor: Color?) {
                super.finished(newColor)
                if (newColor == null) return
                val newColor = newColor.cpy()
                linkColor = newColor
            }
        },
        colorInit = linkColor.cpy()
    )
    this.addActor(colorPicker)
    colorPicker.fadeIn()
}


fun Stage.saveDialog(
    isGoToMenu: Boolean,
    genome: Genome
) {
    SaveGenomeDialog(
        genomeJsonReader = genomeJsonReader,
        genome = genome,
        onSaveAndTest = { genomeNameForTest ->
            game.screen.dispose()
            game.screen = SimulationScreen(
                map = null,
                genomeName = genomeNameForTest
            )
        },
        onGoMenu = {
            game.screen.dispose()
            game.screen = MenuScreen()
        },
        isGoToMenu = isGoToMenu
    ).show(this)
}
