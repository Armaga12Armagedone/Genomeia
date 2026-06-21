package io.github.some_example_name.old.ui.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.I18NBundle
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.util.TableUtils
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.core.DIGameGlobalContainer.fileProvider
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.core.FileProvider
import io.github.some_example_name.old.game.MyGame
import io.github.some_example_name.old.game.applyCustomFont


class EcoSystemScreen: Screen {

    private lateinit var stage: Stage

    override fun show() {
        stage = Stage(ScreenViewport())
        Gdx.input.inputProcessor = stage

        val table = VisTable()
        table.setFillParent(true)
        TableUtils.setSpacingDefaults(table)

        val buttonsTable = VisTable()
        buttonsTable.defaults()

        val roundStyle = DISimulationContainer.roundStyle

         val globalSettingsButton = VisTextButton("GlobalSettings", roundStyle)
//        val iconTexture = Texture(Gdx.files.internal("GenomButton.jpg")) TODO from Armaga(Absolute Solv): Можно заменить обычную кнопку на изображение для стиля. Но пока можно просто кнопку
//        val iconDrawable: Drawable = TextureRegionDrawable(TextureRegion(iconTexture))
//
//        val globalSettingsButton = VisImageButton(iconDrawable, "unstyled");

        game.applyCustomFont(globalSettingsButton)
        globalSettingsButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                game.screen = EcoSystemScreenGlobalSettings()
            }
        })

        val cellsSettingsButton = VisTextButton("Cells Settings", roundStyle)
        game.applyCustomFont(cellsSettingsButton)
        cellsSettingsButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                game.screen = EcoSystemScreenCellsSettings()
            }
        })

        buttonsTable.add(cellsSettingsButton).height(120*Gdx.graphics.density).uniformX()
        buttonsTable.add(globalSettingsButton).height(120*Gdx.graphics.density).uniformX().row()

        val menuButton = VisTextButton(bundle.get("button.menu"), roundStyle)
        game.applyCustomFont(menuButton)
        menuButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor?) {
                game.screen = MenuScreen()
            }
        })
        buttonsTable.add(menuButton).height(60f * Gdx.graphics.density).colspan(2).center()

        table.add(buttonsTable).center()
        table.row()

        stage.addActor(table)
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {}
    override fun dispose() {
        stage.dispose()
    }
}
