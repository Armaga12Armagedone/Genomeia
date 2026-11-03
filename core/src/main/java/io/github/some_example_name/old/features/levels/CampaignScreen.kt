package io.github.some_example_name.old.features.levels

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.JsonReader
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.commands.GoSimulation
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.core.ui.VisDslScreen
import io.github.some_example_name.old.core.ui.visLabel
import io.github.some_example_name.old.core.ui.visScrollPane
import io.github.some_example_name.old.core.ui.visTable
import io.github.some_example_name.old.features.levels.editor.LevelFiles
import io.github.some_example_name.old.features.worldeditor.WorldSpec
import kotlin.math.min

class CampaignScreen: VisDslScreen(isScrollable = false) {
    override fun VisTable.compose() {
        val simData = DISimulationContainer.simulationData
        visLabel("Заголовок") { center(); padBottom(20f); row() }
        // Список уровней — вертикальный, поэтому прокрутка должна идти по вертикали.
        // Раньше было наоборот: длинный список уезжал в сторону, а вниз не листался.
        visScrollPane(cellInit = { expandX().fillX() }, scrollingDisabledX = true) {
            center()
            val levels = getLevels()
            for (level in levels)
            {
                visTable(backgroundColor = Color.GRAY) {
                    visLabel(level.name) {center(); pad(10f)}
                    visLabel("finished: ${level.finished}") {center(); pad(15f)}
                }.also{ it.addListener( object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        super.clicked(event, x, y)
                        println(level.id)
                        simData.isLevel = true
                        //simData.level.hasLua = true
                        val logic = Gdx.files.internal("campaign/lua/0.lua").readString()
                        DIGameGlobalContainer.luaEnv.setScript(logic)
                        navigation.performCommand(GoSimulation(world=null, genomeName = null))
                    }
                } ) }
                row()
            }

            visLabel("Кастомные уровни") { center(); pad(20f) }
            row()

            val customLevels = LevelFiles.names()
            if (customLevels.isEmpty()) {
                visLabel("Уровней пока нет") { center(); pad(10f) }
                row()
            }
            for (name in customLevels) {
                // Ширина по тексту, но не шире экрана: иначе длинное имя вылезает за
                // окно и обрезается его краем (видно на 800×600).
                val textLayout = GlyphLayout(DIGameGlobalContainer.game.largeFont, name)
                val maxTextWidth = Gdx.graphics.width * 0.8f
                val textWidth = min(textLayout.width, maxTextWidth)
                visTable(backgroundColor = Color.GRAY) {
                    visLabel(name) { center(); pad(10f); width(textWidth) }.apply {
                        if (textLayout.width > maxTextWidth) setEllipsis(true)
                    }
                }.also { it.addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        super.clicked(event, x, y)
                        playLevel(name)
                    }
                }) }
                row()
            }
        }
    }

    /**
     * Запустить уровень из папки levels: рецепт рельефа, lua и палитра клеток едут в
     * симуляцию так же, как при «Протестировать» из редактора уровня.
     */
    private fun playLevel(name: String) {
        val level = try {
            LevelFiles.read(name)
        }
        catch (e: Exception) {
            Gdx.app.error("Campaign", "не удалось открыть уровень $name", e)
            return
        }

        // Уровень едет в команде целиком: lua, цель и палитру ставит менеджер навигации
        // при её выполнении. Тогда повтор .bug из кампании восстановит тот же уровень.
        navigation.performCommand(GoSimulation(world = null, genomeName = null, level = level))
    }

    fun getLevels(): List<LevelData> {
        val levels = Gdx.files.internal("campaign/levels/levels.json")
        if (!levels.exists()) return emptyList()
        val jsonLevels = JsonReader().parse(levels.read())
        val levelList = mutableListOf<LevelData>()
        for (level in jsonLevels["levels"]) {
            //levelList.addLast(LevelData(level["id"].asString(), false, level["id"].asInt())) //TODO сделать систему finished уровней
        }

        return levelList
    }
}

data class LevelData(
    val name: String,
    val finished: Boolean,
    val id: Int,
    val world: WorldSpec
)
