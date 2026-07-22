package io.github.some_example_name.old.features.menu

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.core.DIGameGlobalContainer.genomeJsonReader
import io.github.some_example_name.old.core.DISingleThreadSimulationContainer
import io.github.some_example_name.old.features.editor.GenomeEditorScreen
import io.github.some_example_name.old.features.devsupport.VisSupportSimpleScreen
import io.github.some_example_name.old.features.worldeditor.WorldEditorScreen
import io.github.some_example_name.old.core.ui.STYLE_DARK
import io.github.some_example_name.old.core.ui.makeStyledButton
import io.github.some_example_name.old.features.simulation.GenomeListDialog
import io.github.some_example_name.old.features.ecosystem.EcoSystemScreen
import io.github.some_example_name.old.features.settings.SettingsScreen

class MenuScreen : Screen {

    private val stage = Stage(ScreenViewport())
    private val batch = SpriteBatch()
    private val camera = OrthographicCamera()

    private val extraTextures = mutableListOf<Texture>()

    var onResize: (() -> Unit)? = null

    private lateinit var spriteBatch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var root: Table
    private lateinit var fontMatrix: Matrix4
    private lateinit var shapeRenderer: ShapeRenderer
    private var currentScreenWidth = 0
    private var currentScreenHeight = 0

    init {
        if (DISingleThreadSimulationContainer.menuViewModel == null) {
            DISingleThreadSimulationContainer.menuViewModel = MenuViewModel()
            DISingleThreadSimulationContainer.menuViewModel?.startMenuSimulation()
        }
        DISingleThreadSimulationContainer.menuViewModel?.renderSystem?.isRenderUi = false
    }

    override fun show() {
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        spriteBatch = SpriteBatch()
        font = BitmapFont()
        fontMatrix = Matrix4()
        shapeRenderer = ShapeRenderer()
        currentScreenWidth = Gdx.graphics.width
        currentScreenHeight = Gdx.graphics.height

        DISingleThreadSimulationContainer.menuViewModel?.renderSystem?.create(
            fontMatrix = fontMatrix,
            spriteBatch = spriteBatch,
            font = font,
            shapeRenderer = shapeRenderer,
            camera = camera
        )

        buildMenu()

        Gdx.input.inputProcessor = stage

        camera.position.set(24f, 24f, 0f)
        camera.zoom = 0.02f
        camera.update()
    }

    private fun buildMenu() {
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        val density = Gdx.graphics.density

        val table = VisTable()
        table.setFillParent(true)
        table.center()
        table.pad(0f, (w * 0.04f).coerceAtLeast(24f), 0f, 0f)

        val titleLabel = VisLabel("GENOMEIA")
        titleLabel.style = Label.LabelStyle(game.titleFont, Color(STYLE_DARK))
        titleLabel.setAlignment(Align.left)
        table.add(titleLabel).center().padBottom(h * 0.018f).row()

        val btnW = (w * 0.26f).coerceIn(180f * density, 520f * density)
        val btnGap = (h * 0.012f).coerceAtLeast(6f * density)

        data class Btn(val label: String, val action: () -> Unit)

        val btns = listOf(
            Btn(bundle.get("button.empty")) {
                val old = game.screen
                game.screen = WorldEditorScreen()
                old.dispose()
            },
            Btn(bundle.get("button.editor")) {
                val genomes = genomeJsonReader.getGenomeFileNamesFromFolder()
                if (genomes.isEmpty()) {
                    game.screen = GenomeEditorScreen(genomeName = null)
                } else {
                    GenomeListDialog(
                        genomesList = genomes, selectedGenomeIndex = null,
                        title = bundle.get("button.selectGenome"),
                        new = bundle.get("button.new"),
                        select = bundle.get("button.select"),
                        import = bundle.get("button.import"),
                        onNew = {
                            game.screen =
                                GenomeEditorScreen(genomeName = null)
                        },
                        onNext = { n ->
                            game.screen = GenomeEditorScreen(genomeName = n)
                        },
                        onRestart = {},
                        game = game,
                        onResize = { h -> onResize = if (h == {}) null else h },
                        isMenu = true
                    ).show(stage)
                }
            },
            Btn(bundle.get("button.options")) {
                game.screen = SettingsScreen()
            },
            Btn(bundle.get("button.substrateSettings")) {
                game.screen = EcoSystemScreen()
            },
            Btn(bundle.get("label.support")) {
                game.screen = VisSupportSimpleScreen()
            },
            Btn(bundle.get("button.exit")) { Gdx.app.exit() }
        )

        val glyphLayout = GlyphLayout()
        for (btn in btns) {
            val b = makeStyledButton(btn.label, game, extraTextures)
            // Scale label to fill ~85% of button width
            glyphLayout.setText(game.buttonFont, btn.label)
            if (glyphLayout.width > 0f) {
                // Scale is almost always < 1 (shrinking from 48dp down), so no blur
                val scale = (btnW * 0.85f / glyphLayout.width).coerceIn(0.3f, 1.0f)
                b.label.setFontScale(scale)
            }
            val action = btn.action
            b.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent, x: Float, y: Float) = action()
            })
            table.add(b).center().width(btnW).padBottom(btnGap).row()
        }

        stage.addActor(table)
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        DISingleThreadSimulationContainer.menuViewModel?.updateFrame()

        val x = (Gdx.input.x.toFloat() / Gdx.graphics.width.toFloat() - 0.5f) * 2f
        val y = (Gdx.input.y.toFloat() / Gdx.graphics.height.toFloat() - 0.5f) * 2f

        camera.position.set(24f + x, 24f - y, 0f)
        camera.update()

        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_BLEND)

        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        if (width == currentScreenWidth && height == currentScreenHeight) return

        stage.viewport.update(width, height, true)

        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()

        font.data.setScale(Gdx.graphics.density)

        DISingleThreadSimulationContainer.menuViewModel?.renderSystem?.resize(width, height)
        val uiProjection = fontMatrix.setToOrtho2D(
            0f,
            0f,
            Gdx.graphics.width.toFloat(),
            Gdx.graphics.height.toFloat()
        )
        spriteBatch.projectionMatrix = uiProjection

        currentScreenWidth = width
        currentScreenHeight = height
        onResize?.invoke()
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {}

    override fun dispose() {
        stage.dispose()
        batch.dispose()
        extraTextures.forEach { it.dispose() }
    }
}
