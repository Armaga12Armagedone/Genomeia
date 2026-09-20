package io.github.some_example_name.old.editor.ui.dialog

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.utils.I18NBundle
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisRadioButton
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.editor.system.Axial
import io.github.some_example_name.old.editor.system.NoSymmetry
import io.github.some_example_name.old.editor.system.SquareGrid
import io.github.some_example_name.old.editor.system.SymmetryManager
import io.github.some_example_name.old.editor.system.TriangleGrid
import io.github.some_example_name.old.ui.dialogs.setupTitleSize
import io.github.some_example_name.old.ui.screens.MyGame
import io.github.some_example_name.old.ui.screens.applyCustomFont
import io.github.some_example_name.old.ui.screens.makeStyledButton

class SymmetryDialog(
    val game: MyGame,
    val bundle: I18NBundle,
    val symmetryManager: SymmetryManager
) : VisDialog(bundle.get("dialog.select_symmetry") ?: "Select symmetry") {

    private val textures = mutableListOf<Texture>()
    private val buttonGroup = ButtonGroup<VisRadioButton>()
    private val noSymmetryButton   = VisRadioButton(bundle.get("symmetry.none")     ?: "No symmetry")
    private val axialButton        = VisRadioButton(bundle.get("symmetry.axial")    ?: "Axial symmetry")
    private val squareGridButton   = VisRadioButton(bundle.get("symmetry.square")   ?: "Square grid")
    private val triangleGridButton = VisRadioButton(bundle.get("symmetry.triangle") ?: "Triangle grid")

    init {
        val density = Gdx.graphics.density
        setupTitleSize(game)

        contentTable.pad(8f * density)
        contentTable.defaults().left().pad(4f * density)

        buttonGroup.add(noSymmetryButton, axialButton, squareGridButton, triangleGridButton)
        buttonGroup.setMaxCheckCount(1); buttonGroup.setMinCheckCount(1)
        listOf(noSymmetryButton, axialButton, squareGridButton, triangleGridButton)
            .forEach { game.applyCustomFont(it) }

        // 2-column grid of radio buttons
        val radioGrid = VisTable()
        radioGrid.add(noSymmetryButton).padRight(16f * density)
        radioGrid.add(axialButton).row()
        radioGrid.add(squareGridButton).padRight(16f * density)
        radioGrid.add(triangleGridButton)
        contentTable.add(radioGrid).colspan(2).padBottom(12f * density).row()

        // Horizontal OK / Cancel
        val btnH = Gdx.graphics.height * 0.055f
        val okBtn = makeStyledButton(bundle.get("button.ok") ?: "OK", game, textures)
        val cancelBtn = makeStyledButton(bundle.get("button.cancel") ?: "Cancel", game, textures)

        okBtn.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            override fun changed(event: ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                applySelectedSymmetry()
                fadeOut()
            }
        })
        cancelBtn.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            override fun changed(event: ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor?) {
                fadeOut()
            }
        })

        val btnRow = VisTable()
        btnRow.add(okBtn).height(btnH).padRight(8f * density)
        btnRow.add(cancelBtn).height(btnH)
        contentTable.add(btnRow).center().colspan(2)

        when (symmetryManager.symmetryMode) {
            is NoSymmetry  -> noSymmetryButton.isChecked   = true
            Axial          -> axialButton.isChecked         = true
            is SquareGrid  -> squareGridButton.isChecked    = true
            is TriangleGrid -> triangleGridButton.isChecked = true
        }

        closeOnEscape()
        pack()
        centerWindow()
    }

    private fun applySelectedSymmetry() {
        symmetryManager.symmetryMode = when {
            noSymmetryButton.isChecked   -> NoSymmetry
            axialButton.isChecked        -> Axial
            squareGridButton.isChecked   -> symmetryManager.symmetryMode as? SquareGrid ?: SquareGrid(0.6f)
            else                         -> symmetryManager.symmetryMode as? TriangleGrid ?: TriangleGrid(0.6f)
        }
    }

    override fun hide(action: com.badlogic.gdx.scenes.scene2d.Action?) {
        super.hide(action)
        addAction(Actions.sequence(
            Actions.delay(0.3f),
            Actions.run { textures.forEach { it.dispose() }; textures.clear() }
        ))
    }
}
