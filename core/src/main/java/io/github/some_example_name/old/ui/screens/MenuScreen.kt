package io.github.some_example_name.old.ui.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.util.TableUtils
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.FileProvider
import io.github.some_example_name.old.systems.genomics.genome.GenomeJsonReader
import io.github.some_example_name.old.ui.dialogs.GenomeListDialog


class MenuScreen(
    private val game: MyGame,
    val multiPlatformFileProvider: FileProvider
) : Screen {

    private val stage = Stage(ScreenViewport())
    private val bundle = DIGameGlobalContainer.bundle

    val genomeJsonReader: GenomeJsonReader = GenomeJsonReader()
    var onResize: (() -> Unit)? = null

    init {
        val density = Gdx.graphics.density
        val table = VisTable()
        TableUtils.setSpacingDefaults(table)
//        table.defaults().minWidth(400f)
        table.columnDefaults(0).pad(10f * density)
        table.setFillParent(true)

        val genomeia = VisLabel(bundle.get("title.genomeia"))
        game.applyCustomFont(genomeia)
        genomeia.setAlignment(Align.center)
        table.add(genomeia).fillX().padBottom(10f).row()

        val patch = NinePatch(Texture(Gdx.files.internal("button.png")), 20, 20, 20, 20)
        val roundUp = NinePatchDrawable(patch).tint(com.badlogic.gdx.graphics.Color(0.44f, 0.40f, 0.40f, 1f))
        val roundDown = NinePatchDrawable(patch).tint(com.badlogic.gdx.graphics.Color(0.2f,0.2f,0.2f,1f))
        val roundOver = NinePatchDrawable(patch).tint(com.badlogic.gdx.graphics.Color(0f, 0.9f, 1f, 1f))

        val baseStyle = VisUI.getSkin().get("blue", VisTextButton.VisTextButtonStyle::class.java)

        val roundStyle = VisTextButton.VisTextButtonStyle(baseStyle).apply {
            up = roundUp
            down = roundDown
            over = roundOver
        }

        val emptyButton = VisTextButton(bundle.get("button.empty"), roundStyle)
        emptyButton.pad(4f)
        game.applyCustomFont(emptyButton)
        table.add(emptyButton).fillX().height(60f * density).row()
        emptyButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                val oldScreen = game.screen
                game.screen =
                    SimulationScreen(multiPlatformFileProvider, game, null, bundle, null) // Передаем map
                oldScreen.dispose()
//                game.screen = WorldEditorScreen(
//                    multiPlatformFileProvider = multiPlatformFileProvider,
//                    game = game,
//                    bundle = bundle
//                )
            }
        })

//        currentGenomeIndex = 0
        val genomeEditorButton = VisTextButton(bundle.get("button.editor"), roundStyle)
        genomeEditorButton.pad(4f)
        game.applyCustomFont(genomeEditorButton)
        table.add(genomeEditorButton).fillX().height(60f * density).row()
        genomeEditorButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                val genomes = genomeJsonReader.getGenomeFileNamesFromFolder("user_genomes")

                when (genomes.size) {
                    0 -> {/*game.screen = GenomeEditorScreen(
                        multiPlatformFileProvider = multiPlatformFileProvider,
                        game = game,
                        genomeName = null,
                        bundle = bundle
                    )*/
                    }
                    else -> {
                        GenomeListDialog(
                            genomesList = genomes,
                            selectedGenomeIndex = null,
                            title = bundle.get("button.selectGenome"),
                            new = bundle.get("button.new"),
                            select = bundle.get("button.select"),
                            import = bundle.get("button.import"),
                            onNew = {
//                                game.screen = GenomeEditorScreen(
//                                    multiPlatformFileProvider = multiPlatformFileProvider,
//                                    game = game,
//                                    genomeName = null,
//                                    bundle = bundle
//                                )
                            },
                            onNext = { genomeName ->
//                                game.screen = GenomeEditorScreen(
//                                    multiPlatformFileProvider = multiPlatformFileProvider,
//                                    game = game,
//                                    genomeName = "$genomeName.json",
//                                    bundle = bundle
//                                )
                            },
                            onRestart = {

                            },
                            game = game,
                            onResize = { handler ->
                                onResize = if (handler == {}) null else handler
                            },
                            isMenu = true
                        ).show(stage)
                    }
                }
            }
        })
        val optionsButton = VisTextButton(bundle.get("button.options"), roundStyle)
        optionsButton.pad(4f)
        game.applyCustomFont(optionsButton)
        table.add(optionsButton).fillX().height(60 * density).row()
        optionsButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                game.screen = SettingsScreen(game, multiPlatformFileProvider, bundle = bundle)
            }
        })

        val substrateSettingsButton = VisTextButton(bundle.get("button.substrateSettings"), roundStyle)
        substrateSettingsButton.pad(4f)
        game.applyCustomFont(substrateSettingsButton)
        table.add(substrateSettingsButton).fillX().height(60f * density).row()
        substrateSettingsButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                game.screen = JsonEditorScreen(game, multiPlatformFileProvider, bundle = bundle)
            }
        })

        val exitButton = VisTextButton(bundle.get("button.exit"), roundStyle)
        emptyButton.pad(4f)
        game.applyCustomFont(exitButton)
        table.add(exitButton).fillX().height(60f * density).row()
        exitButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                Gdx.app.exit()
            }
        })

        stage.addActor(table)
        Gdx.input.inputProcessor = stage

        // Отладка: проверьте размер шрифта в логах
        Gdx.app.log("FontDebug", "Menu font cap height: ${game.mediumFont.capHeight}")
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        if (width == 0 || height == 0) return  // Avoid divide-by-zero on minimize
        stage.viewport.update(width, height, true)
        onResize?.invoke()
    }

    override fun pause() {}
    override fun resume() {}
    override fun show() {}
    override fun hide() {}
    override fun dispose() {
        stage.dispose()
    }
}
