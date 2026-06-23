package io.github.some_example_name.old.ui.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.widget.VisTable

abstract class VisSimpleScreen(
    val background: Color = Color(0.04f, 0.04f, 0.06f, 1f),
    val isScrollable: Boolean = false
) : Screen {

    lateinit var stage: Stage
    private lateinit var rootTable: VisTable

    override fun show() {
        stage = Stage(ScreenViewport())
        Gdx.input.inputProcessor = stage

        rootTable = globalVisTable {
            setFillParent(true)

            if (isScrollable) {
//                defaults().pad(28.dp())
                visScrollPane({
                    expand().fill()
                }) {
                    compose()
                }
            } else {
                compose()
            }
        }

        stage.addActor(rootTable)
    }

    /**
     * Главный метод экрана. Полностью в стиле Jetpack Compose.
     *
     * Пример использования:
     * override fun VisTable.compose() {
     *     visLabel("Заголовок") { center(); padBottom(30f) }
     *     row()
     *     visTextButton("Играть") {
     *         onClick = { ... }
     *         expandX().fillX()
     *     }
     * }
     */
    protected abstract fun VisTable.compose()

    /**
     * Полная перерисовка экрана (аналог recomposition).
     * Используй, когда сильно поменялось состояние (новый список, смена режима и т.д.).
     */
    protected fun recompose() {
        if (!::rootTable.isInitialized) return

        rootTable.clearChildren()

        rootTable.apply {
            if (isScrollable) {
                visScrollPane({
                    expand().fill()
                }) {
                    compose()
                }
            } else {
                compose()
            }
        }
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(background.r, background.g, background.b, background.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        recompose()
    }

    override fun pause() {}

    override fun resume() {}

    override fun hide() {}

    override fun dispose() {
        stage.dispose()
    }
}
