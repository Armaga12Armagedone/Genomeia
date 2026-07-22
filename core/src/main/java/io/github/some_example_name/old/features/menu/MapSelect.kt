package io.github.some_example_name.old.features.menu

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisCheckBox
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisImageTextButton
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.core.ui.makeStyledButton
import io.github.some_example_name.old.core.ui.setupTitleSize
import io.github.some_example_name.old.features.worldeditor.WorldEditorScreen
import io.github.some_example_name.old.game.MyGame
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream


class MapSelect(
    val game: MyGame,
    val title: String
) : VisDialog(title) {
    lateinit var scrollPane: ScrollPane
    private val textures = mutableListOf<Texture>()

    init {
        val scrollContentTable = VisTable()

        setupTitleSize(game)

        setupUI(scrollContentTable)

        scrollPane = ScrollPane(scrollContentTable).apply {
            setFadeScrollBars(false)
            setScrollingDisabled(false, false)
            setForceScroll(false, true)
            setFlickScroll(true)
            setOverscroll(false, true)
        }

        contentTable.add(scrollPane).grow().maxHeight(Gdx.graphics.height * 0.8f)
        contentTable.row()
        closeOnEscape()
        pack()
        centerWindow()
    }

    fun setupUI(scrollContentTable: VisTable) {
        val density = Gdx.graphics.density

        val group = ButtonGroup<VisImageTextButton>()
        group.setMinCheckCount(1) // можно ничего не выбирать
        group.setMaxCheckCount(1) // только один выбран одновременно

        // Используем стиль "radio" для круглых иконок (вместо "default", который для чекбоксов квадратных)
        val radioStyle = VisCheckBox.VisCheckBoxStyle(
            VisUI.getSkin().get("radio", VisCheckBox.VisCheckBoxStyle::class.java)
        )
        val iconSize = if (Gdx.app.type == Application.ApplicationType.Android) 10f else 15f  // Базовый размер иконки (подберите)

        // Устанавливаем размеры для круглой иконки: checkBackground - off (пустой круг), tick - on (точка внутри)
        // checkboxOff не существует; используйте checkBackground и tick из VisCheckBoxStyle
        radioStyle.checkBackground.minWidth = iconSize * density
        radioStyle.checkBackground.minHeight = iconSize * density

        radioStyle.tick.minWidth = iconSize * density  // Размер точки (on state); подберите, чтобы соответствовал background
        radioStyle.tick.minHeight = iconSize * density

        // Для состояний over/down/disabled, если они заданы
        if (radioStyle.checkBackgroundOver != null) {
            radioStyle.checkBackgroundOver.minWidth = iconSize * density
            radioStyle.checkBackgroundOver.minHeight = iconSize * density
        }
        if (radioStyle.checkBackgroundDown != null) {
            radioStyle.checkBackgroundDown.minWidth = iconSize * density
            radioStyle.checkBackgroundDown.minHeight = iconSize * density
        }
        if (radioStyle.tickDisabled != null) {
            radioStyle.tickDisabled.minWidth = iconSize * density
            radioStyle.tickDisabled.minHeight = iconSize * density
        }

        radioStyle.font = if (Gdx.app.type == Application.ApplicationType.Android) game.largeFont else game.extraLargeFont

        val content = VisTable(true)

        val maps = getMaps()
        val btnH = Gdx.graphics.height * 0.055f
        maps.forEachIndexed { index, string ->
            val texture = getTexture(string)
            val iconDrawable: Drawable = TextureRegionDrawable(TextureRegion(texture))

            val button: VisImageTextButton = VisImageTextButton(string, iconDrawable)
            val size = 64f * density
            button.imageCell.size(size, size).padRight(8f * density)

            button.padRight(size + (8f * density))
            content.add(button).height(btnH).center().row()
            group.add(button)
        }

        scrollContentTable.add(content).pad(10f * density).row()


        val bottomButtonTable = VisTable()
        bottomButtonTable.defaults().padRight(8f * density)


        makeStyledButton("New Map", game, textures).also {
            bottomButtonTable.add(it).height(btnH).row()

            it.addListener( object: ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val old = game.screen
                    game.screen = WorldEditorScreen()
                    old.dispose()
                }
            })
        }
        scrollContentTable.add(bottomButtonTable).center().padTop(8f*density)
    }

    fun getTexture(name: String): Texture {
        ZipInputStream(FileInputStream(File(DISimulationContainer.baseMapDir+name))).use { zipIn ->
            var entry = zipIn.nextEntry

            while (entry != null) {
                // Find the specific PNG file
                if (entry.name == name.substringBefore(".")+".png" && !entry.isDirectory) {
                    val outputBuffer = ByteArrayOutputStream()

                    // Copy stream data safely using Kotlin's extension function
                    zipIn.copyTo(outputBuffer)

                    zipIn.closeEntry()
                    val bytes = outputBuffer.toByteArray()
                    val pixmap = Pixmap(bytes, 0, bytes.size)
                    return Texture(pixmap)
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        println("Black")
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)

        pixmap.setColor(Color.BLACK)

        pixmap.fill()
        return Texture(pixmap) // File not found
    }

    fun getMaps(): List<String> {
        val file = File(DISimulationContainer.baseMapDir)
        val fileCount = file.listFiles()?.count { it.isFile } ?: 0
        val maps = mutableListOf<String>()
        if (fileCount > 0)
        {
            file.listFiles().forEachIndexed { index, file ->
                maps.add(file.name)
            }
            return maps
        }
        return maps
    }
}
