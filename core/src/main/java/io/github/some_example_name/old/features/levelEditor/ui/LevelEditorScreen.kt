package io.github.some_example_name.old.features.levelEditor.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Stage
import com.kotcrab.vis.ui.VisUI
import io.github.some_example_name.old.features.levelEditor.node.Node
import io.github.some_example_name.old.features.levelEditor.nodes.BaseNode

class LevelEditorScreen: Screen {

    private val stage: Stage = Stage()
    private val camera = OrthographicCamera().apply {
        setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    }

    private val batch = SpriteBatch()

    val node = BaseNode().apply { this.init() }

    override fun dispose() {
        stage.dispose()
    }

    override fun hide() {
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.10f, 0.12f, 0.14f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.update()

//        batch.begin()
//        //node.draw(batch = batch)
//        batch.end()

        stage.act(delta)
        stage.draw()
    }

    override fun pause() {
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)

        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
    }

    override fun show() {
        Gdx.input.setInputProcessor(stage)

        stage.addActor(node)
        node.setPosition(256f, 256f)
    }

    override fun resume() {
    }
}
