package io.github.some_example_name.old.features.levels.dialogs

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.kotcrab.vis.ui.widget.VisCheckBox
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisScrollPane
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.core.ui.dp
import io.github.some_example_name.old.core.ui.makeStyledButton
import io.github.some_example_name.old.core.ui.setupTitleSize

class AllowCellsDialog: VisDialog("Allow cell dialog") {
    init {
        setupTitleSize(game)
        setupUI()
    }

    fun setupUI() {
        val table = VisTable()
        table.setFillParent(true)

        val title = VisLabel("Allow cells to use in game") // TODO заменить на bundle
        table.add(title).center().pad(6.dp()).row()

        val cellsListTab = VisTable()
        cellsListTab.top()

        for (cell in DISimulationContainer.cellList) {
            val cellTab = VisTable()
            val cellName = VisLabel(cell.name)
            val cellBool = makeStyledButton("Use", game, mutableListOf<Texture>(), true)

            cellTab.add(cellName).left().pad(16.dp())
            cellTab.add(cellBool).right().expandX().pad(8.dp())

            cellsListTab.add(cellTab).fillX().expandX().pad(8.dp()).row()
        }

        val cellsScroll = VisScrollPane(cellsListTab)

        cellsScroll.setScrollingDisabled(true, false)

        table.add(cellsScroll)
            .center()
            .width(400.dp())
            .height(300.dp())
            .expandY()
            .fill()
            .row()

        val btnClose = makeStyledButton("Close", game, mutableListOf(), false).also { button ->
            button.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    super.clicked(event, x, y)
                    hide()
                }
            })
        }

        table.add(btnClose).center().pad(10.dp())

        add(table)
    }

}




























//Нафиг этот ваш UI... TODO сделать visDSLDialog
