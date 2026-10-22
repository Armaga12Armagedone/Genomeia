package io.github.some_example_name.old.features.levels.editor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextField
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.ui.LineNumberTextArea
import io.github.some_example_name.old.core.ui.VisDslScreen
import io.github.some_example_name.old.core.ui.dp
import io.github.some_example_name.old.core.ui.makeStyledNP
import io.github.some_example_name.old.core.ui.visLabel
import io.github.some_example_name.old.core.ui.visLineNumberTextArea
import io.github.some_example_name.old.core.ui.visScrollPane
import io.github.some_example_name.old.core.ui.visTable
import io.github.some_example_name.old.core.ui.visTextButton
import io.github.some_example_name.old.core.ui.visTextField
import io.github.some_example_name.old.core.ui.visToggleButton
import io.github.some_example_name.old.features.levels.dialogs.AllowCellsDialog

class LevelEditorScreen : VisDslScreen() {

    private val panel: Drawable by lazy { makeStyledNP(PANEL_BG, PANEL_BORDER, mutableListOf()) }
    private lateinit var textEditor: LineNumberTextArea
    private lateinit var logLabel: VisLabel

    //Свойства
    private lateinit var needCells: VisTextField
    private lateinit var destX: VisTextField
    private lateinit var destY: VisTextField
    private lateinit var levelName: VisTextField

    override fun VisTable.compose() {
        val sideWidth = (Gdx.graphics.width * 0.32f).coerceAtLeast(280.dp())
        visTable(cellInit = { expand().fill() }, backgroundColor = BG) {
            visTable(cellInit = { grow().top().left().pad(16.dp()) }) {
                visLabel("Скрипт уровня", textColor = ACCENT) { top().left().growX().padBottom(8.dp()) }
                row()
                visTextButton("Загрузить", onClick = {
                    DIGameGlobalContainer.fileProvider.pickTextFile(listOf("lua")) { _, text ->
                        if (text != null) Gdx.app.postRunnable { textEditor.setText(text) }
                    }
                }) { top().right().growX().padBottom(6.dp()) }.apply { pad(10.dp(), 8.dp(), 10.dp(), 8.dp()) }
                visTextButton("Сохранить", onClick = {
                    DIGameGlobalContainer.fileProvider.saveTextFile("level.lua", textEditor.text)
                }) { top().right().growX().padBottom(6.dp()) }.apply { pad(10.dp(), 8.dp(), 10.dp(), 8.dp()) }
                visTextButton("Проверить") { top().right().growX().padBottom(6.dp()) }.apply { pad(10.dp(), 8.dp(), 10.dp(), 8.dp()) } //TODO: сделать проверку
                row()
                textEditor = visLineNumberTextArea(text = "", cellInit = { grow().colspan(4).padTop(8.dp()) })
                textEditor.setCodeColor(CODE)
                textEditor.gutterTextColor.set(ACCENT.r, ACCENT.g, ACCENT.b, 1f)
            }.background = panel

            visTable(cellInit = { growY().fill().prefWidth(sideWidth).top().right().pad(16.dp()) }) {
                visLabel("Свойства", textColor = ACCENT) { top().left().growX().padBottom(8.dp()) }
                row()
                visScrollPane(cellInit = { grow() }, scrollingDisabledX = true) {
                    visTable(cellInit = {growX()}) {
                        visLabel("Имя уровня") { growX().padBottom(6.dp()) }
                            .apply { setWrap(true) }
                        row()
                        levelName = visTextField("Rayan01") { growX() }
                    }.background = panel

                    row()

                    visTable(cellInit = {growX()}) {
                        visLabel("Необходимое кол-во клеток(-1 если не нужно)") { growX().padBottom(6.dp()) }
                            .apply { setWrap(true) }
                        row()
                        needCells = visTextField("-1") { growX() }
                    }.background = panel

                    row()

                    visTable(cellInit = {growX()}) {
                        visLabel("Конечная цель(-1 если не нужно)") { growX().padBottom(6.dp()); colspan(2)}
                            .apply { setWrap(true) }
                        row()
                        visLabel("X: ")
                        destX = visTextField("-1", hint = "X") { growX() }
                        row()
                        visLabel("Y: ")
                        destY = visTextField("-1", hint = "Y") { growX() }
                    }.background = panel
                }
            }.background = panel

            row()

            visTable(cellInit = { grow().bottom().left().pad(16.dp()) }) {
                logLabel = visLabel("No errors")
            }.background = panel

            row()

            visTable(cellInit = { grow().bottom().left().pad(16.dp()) }) {
            }.background = panel

            visTable(cellInit = { growY().fill().prefWidth(sideWidth).bottom().right().pad(16.dp()) }) {
                //listOf("Разрешенные клетки", "Редактор карты", "Сохранить уровень", "Протестировать уровень").forEach { text ->
                listOf("Разрешенные клетки" to ::allowedCells, "Редактор карты" to ::mapEditor, "Сохранить уровень" to ::saveMap, "Протестировать уровень" to ::playLevel).forEach { btnPair ->
                    visTextButton(btnPair.first) { row(); growX().padBottom(6.dp()) }.apply {
                        pad(10.dp(), 8.dp(), 10.dp(), 8.dp())
                        label.setWrap(true)
                        label.setAlignment(Align.center)

                        addListener(object : ClickListener() {
                            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                                super.clicked(event, x, y)
                                btnPair.second()
                            }
                        })
                    }
                }
                visTextButton("Выйти") { growX() }
            }.background = panel
        }
    }

    fun allowedCells() {
        AllowCellsDialog().show(stage)
    }

    fun mapEditor() {

    }

    fun saveMap() {

    }

    fun playLevel() { //при этом менять настройки SimulationData

    }

    fun loadText() { //ну вроде уже и не нужно..
        DIGameGlobalContainer.fileProvider.pickTextFile(listOf("lua")) { name, text -> /* ... */ }
    }

    companion object {
        private val BG = Color(0.07f, 0.05f, 0.04f, 1f)
        private val PANEL_BG = Color(0.14f, 0.10f, 0.07f, 1f)
        private val PANEL_BORDER = Color(0.85f, 0.50f, 0.20f, 0.55f)
        private val ACCENT = Color(0.92f, 0.55f, 0.22f, 1f)
        private val CODE = Color(0.95f, 0.84f, 0.70f, 1f)
    }
}

data class LevelInfo(
    val id: Int,
    val nameKey: String,
    val logic: String,
    val allowedCells: List<String>,
    val cellsNeed: Int,
    val needDestination: Boolean,
    val destination: Pair<Float, Float>,
    val world: WorldInfo
)

data class WorldInfo(
    val width: Int,
    val height: Int,
    val seed: String,
    val dayNight: Int,
    val smoothing: Int,
    val edits: List<Int> //TODO заглушка поменять на реальные изменения
)
