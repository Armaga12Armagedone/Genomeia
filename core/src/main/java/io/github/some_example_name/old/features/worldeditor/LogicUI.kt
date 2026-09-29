package io.github.some_example_name.old.features.worldeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextArea
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.ui.visTextButton

class LogicUI: Screen {
    private val textEditor: VisTextArea = VisTextArea()
    private lateinit var stage: Stage

    override fun show() {
        stage = Stage(ScreenViewport())
        setupUI()
        Gdx.input.inputProcessor = stage
    }

    fun setupUI() {
        val table = VisTable()
        table.setFillParent(true)

        table.add(textEditor).width(Gdx.graphics.width*0.80f).height(Gdx.graphics.height.toFloat())

        table.apply {
            visTextButton("compile", onClick = {
                validateJson()
            }) {top()}

            row()
        }

        stage.addActor(table)

    }

    fun validateJson() {
        val valid = DIGameGlobalContainer.logicParser.checkJSON(textEditor.text)
        println(valid)
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
