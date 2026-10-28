package io.github.some_example_name.old.features.levels.dialogs

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.ui.makeStyledButton
import io.github.some_example_name.old.core.ui.setupTitleSize
import io.github.some_example_name.old.game.applyCustomFont
import io.github.some_example_name.old.game.applyCustomFontLarge

class PopUp(title: String, val text: String): VisDialog(title) {
    init {
        setupTitleSize(DIGameGlobalContainer.game)
        val table = VisTable()

        val titleLabel = VisLabel(title)
        DIGameGlobalContainer.game.applyCustomFontLarge(titleLabel)
        //table.add(titleLabel).center().top().row()

        val textLabel = VisLabel(text)
        DIGameGlobalContainer.game.applyCustomFont(textLabel)
        table.add(textLabel).center().top().row()

        val okBut = makeStyledButton("Ok", DIGameGlobalContainer.game, mutableListOf()).apply { addListener( object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                super.clicked(event, x, y)
                DIGameGlobalContainer.luaEnv.env.popupActive = false
                DIGameGlobalContainer.luaEnv.env.execute()
                hide()
            }
        })}

        table.add(okBut).fillX()

        add(table)
    }
}
