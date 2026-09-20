package io.github.some_example_name.old.ui.dialogs

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.utils.Scaling
import com.kotcrab.vis.ui.widget.VisImageButton
import com.kotcrab.vis.ui.widget.VisWindow
import io.github.some_example_name.old.ui.screens.MyGame
import io.github.some_example_name.old.ui.screens.applyCustomFontMedium
import io.github.some_example_name.old.ui.screens.roundCorners

fun VisWindow.setupTitleSize(game: MyGame) {
    roundCorners()
    addCloseButton()
    transparentCloseButton()

    val d = Gdx.graphics.density
    val titleLabel = getTitleLabel()
    game.applyCustomFontMedium(titleLabel)

    val titleTable = getTitleTable()
    titleTable.pad(5f * d)
    titleTable.padTop(5f * d).padBottom(5f * d)

    val closeButton = titleTable.children.last() as? VisImageButton ?: return
    val closeBtnSize = if (Gdx.app.type == Application.ApplicationType.Android) 20f else 32f
    closeButton.image.setScaling(Scaling.fit)
    closeButton.imageCell.size(closeBtnSize * d * 0.75f)
    titleTable.getCell(closeButton).size(closeBtnSize * d, closeBtnSize * d)

    titleTable.invalidateHierarchy()
    padTop(titleTable.getPrefHeight())
}

// Remove the square button background from the close (×) button; keep only the icon
private fun VisWindow.transparentCloseButton() {
    for (cell in getTitleTable().cells) {
        val btn = cell.actor
        if (btn is ImageButton) {
            val orig = btn.style
            val ns = ImageButton.ImageButtonStyle()
            ns.imageUp   = orig.imageUp
            ns.imageDown = orig.imageDown
            ns.imageOver = orig.imageOver
            btn.style = ns
        }
    }
}
