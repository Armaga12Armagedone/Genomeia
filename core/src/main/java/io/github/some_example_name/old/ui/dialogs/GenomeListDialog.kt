package io.github.some_example_name.old.ui.dialogs

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisCheckBox
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisRadioButton
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.ui.screens.MyGame
import io.github.some_example_name.old.ui.screens.applyCustomFont
import io.github.some_example_name.old.ui.screens.makeStyledButton

class GenomeListDialog(
    val genomesList: List<String>,
    val selectedGenomeIndex: Int?,
    title: String,
    val new: String,
    val select: String,
    val import: String,
    val onNew: () -> Unit,
    val onNext: (String) -> Unit,
    val onRestart: () -> Unit,
    val game: MyGame,
    val onResize: (() -> Unit) -> Unit,
    val isMenu: Boolean
) : VisDialog(title) {
    var selectedIndex = selectedGenomeIndex ?: 0
    val scrollPane: ScrollPane
    private val textures = mutableListOf<Texture>()

    init {
        isModal = true
        isMovable = true

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
        onResize.invoke { centerWindow() }
        pack()
        centerWindow()
    }

    private fun setupUI(scrollContentTable: VisTable) {
        val density = Gdx.graphics.density
        val group = ButtonGroup<VisRadioButton>()
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

        // Шрифт, как в оригинале
        radioStyle.font = if (Gdx.app.type == Application.ApplicationType.Android) game.largeFont else game.extraLargeFont

        val content = VisTable(true)

        genomesList.forEachIndexed { index, string ->
            val rb = VisRadioButton(string, radioStyle)
            game.applyCustomFont(rb)
            group.add(rb)
            content.add(rb).left().row()
            if (selectedIndex == index) rb.isChecked = true

            // Обрабатываем именно клик
            rb.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (rb.isChecked) {
                        selectedIndex = index
                        if (selectedIndex != -1) {
                            onNext.invoke(genomesList[selectedIndex])
                        }
                        fadeOut()
                    }
                }
            })
        }

        scrollContentTable.add(content).pad(10f * density).row()

        val btnH = Gdx.graphics.height * 0.055f
        val bottomButtonTable = VisTable()
        bottomButtonTable.defaults().padRight(8f * density)

        makeStyledButton(new, game, textures).also {
            it.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onNew.invoke(); fadeOut()
                }
            })
            bottomButtonTable.add(it).height(btnH)
        }

        makeStyledButton(select, game, textures).also {
            it.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (selectedIndex != -1) onNext.invoke(genomesList[selectedIndex])
                    fadeOut()
                }
            })
            bottomButtonTable.add(it).height(btnH)
        }

        if (Gdx.app.type == Application.ApplicationType.Android && isMenu) {
            makeStyledButton(import, game, textures).also {
                it.addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        game.multiPlatformFileProvider.importGenome { _ -> onRestart.invoke(); fadeOut() }
                    }
                })
                bottomButtonTable.add(it).height(btnH)
            }
        }

        scrollContentTable.add(bottomButtonTable).center().padTop(8f * density)
    }

    override fun close() {
        super.close()
        onResize.invoke {}
        addAction(Actions.sequence(
            Actions.delay(0.3f),
            Actions.run { textures.forEach { it.dispose() }; textures.clear() }
        ))
    }
}
