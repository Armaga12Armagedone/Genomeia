package io.github.some_example_name.old.features.worldeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextArea
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.ui.visTextButton
import java.awt.Color

class LogicUI: Screen { //не нужно TODO убрать
    private val textEditor: VisTextArea = VisTextArea()
    private val errorLabel: VisLabel = VisLabel()
    private lateinit var stage: Stage

    override fun show() {
        stage = Stage(ScreenViewport())
        setupUI()
        Gdx.input.inputProcessor = stage
    }

    fun setupUI() {
        val table = VisTable()
        table.setFillParent(true)

        table.add(textEditor).width(Gdx.graphics.width*0.80f).height(Gdx.graphics.height*0.8f)

        table.apply {
            visTextButton("debug", onClick = {
                validateLua()
            }) {top()}

            row()
        }

        val logTable = VisTable()

        logTable.setBackground(VisUI.getSkin().getDrawable("white"))
        logTable.setColor(com.badlogic.gdx.graphics.Color.GRAY)

        errorLabel.setAlignment(Align.top or Align.right)

        logTable.add(errorLabel).expand().top().left()

        table.add(logTable).bottom().width(Gdx.graphics.width*0.8f).height(Gdx.graphics.height*0.2f).padTop(10f)

        stage.addActor(table)

    }

    fun validateLua() {
        DIGameGlobalContainer.luaEnv.setScript(textEditor.text)
        val errors = DIGameGlobalContainer.luaEnv.run()
        errorLabel.setText(errors)

    }

    override fun dispose() {

    }

    override fun resize(width: Int, height: Int) {

    }

    override fun render(delta: Float) {
        clearScreen()
        stage.act(delta)
        stage.draw()
    }

    private fun clearScreen() {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }

    override fun pause() {

    }

    override fun resume() {

    }

    override fun hide() {

    }
}
