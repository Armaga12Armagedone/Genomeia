package io.github.some_example_name.old.editor.ui.dialog

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.utils.I18NBundle
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisRadioButton
import io.github.some_example_name.old.editor.system.Axial
import io.github.some_example_name.old.editor.system.NoSymmetry
import io.github.some_example_name.old.editor.system.SquareGrid
import io.github.some_example_name.old.editor.system.SymmetryManager
import io.github.some_example_name.old.editor.system.TriangleGrid
import io.github.some_example_name.old.ui.dialogs.setupTitleSize
import io.github.some_example_name.old.ui.screens.MyGame

class SymmetryDialog(
    val game: MyGame,
    val bundle: I18NBundle,
    val symmetryManager: SymmetryManager
) : VisDialog(bundle.get("dialog.select_symmetry") ?: "Select symmetry") {

    private val buttonGroup = ButtonGroup<VisRadioButton>()

    private val noSymmetryButton = VisRadioButton(bundle.get("symmetry.none") ?: "No symmetry")
    private val axialButton = VisRadioButton(bundle.get("symmetry.axial") ?: "Axial symmetry")
    private val squareGridButton = VisRadioButton(bundle.get("symmetry.square") ?: "Square grid")
    private val triangleGridButton = VisRadioButton(bundle.get("symmetry.triangle") ?: "Triangle grid")

    init {
        val density = Gdx.graphics.density
        setupTitleSize(game)

        contentTable.defaults().left().pad(6f * density)

        // Добавляем радио-кнопки в группу (только одна может быть выбрана)
        buttonGroup.add(noSymmetryButton, axialButton, squareGridButton, triangleGridButton)
        buttonGroup.setMaxCheckCount(1)
        buttonGroup.setMinCheckCount(1)

        // Заполняем таблицу
        contentTable.add(noSymmetryButton).row()
        contentTable.add(axialButton).row()
        contentTable.add(squareGridButton).row()
        contentTable.add(triangleGridButton).row()

        // Кнопки OK / Cancel (в стиле оригинального actionButton)
        actionButton(bundle.get("button.ok") ?: "OK", game = game) {
            applySelectedSymmetry()
            fadeOut()
        }.also {
            it.pad(8f * density)
            contentTable.add(it)
        }

        actionButton(bundle.get("button.cancel") ?: "Cancel", game = game) {
            fadeOut()
        }.also {
            it.pad(8f * density)
            contentTable.add(it).row()
        }

        // Устанавливаем текущий выбранный режим
        when (symmetryManager.symmetryMode) {
            is NoSymmetry -> noSymmetryButton.isChecked = true
            Axial -> axialButton.isChecked = true
            is SquareGrid -> squareGridButton.isChecked = true
            is TriangleGrid -> triangleGridButton.isChecked = true
        }

        closeOnEscape()
        pack()
    }

    private fun applySelectedSymmetry() {
        symmetryManager.symmetryMode = when {
            noSymmetryButton.isChecked -> NoSymmetry
            axialButton.isChecked -> Axial
            squareGridButton.isChecked -> {
                // Если уже был SquareGrid — сохраняем старый step/offset
                val current = symmetryManager.symmetryMode
                current as? SquareGrid ?: SquareGrid(0.6f)
            }
            else -> { // triangleGridButton.isChecked
                val current = symmetryManager.symmetryMode
                current as? TriangleGrid ?: TriangleGrid(0.6f)
            }
        }
    }
}
