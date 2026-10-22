package io.github.some_example_name.old.features.levels

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.JsonReader
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.commands.GoSimulation
import io.github.some_example_name.old.commands.NavigationCommands
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.core.ui.VisDslScreen
import io.github.some_example_name.old.core.ui.visLabel
import io.github.some_example_name.old.core.ui.visScrollPane
import io.github.some_example_name.old.core.ui.visTable
import io.github.some_example_name.old.features.worldeditor.WorldSpec

class CampaignScreen: VisDslScreen(isScrollable = false) {
    override fun VisTable.compose() {
        val simData = DISimulationContainer.simulationData
        visLabel("Заголовок") { center(); padBottom(20f); row() }
        visScrollPane(cellInit = { expandX().fillX() }, scrollingDisabledY = true) {
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
                        simData.hasLua = true
                        val logic = Gdx.files.internal("campaign/lua/0.lua").readString()
                        DIGameGlobalContainer.luaEnv.setScript(logic)
                        navigation.performCommand(GoSimulation(world=null, genomeName = null))
                    }
                } ) }
            }
        }
    }

    fun getLevels(): List<LevelData> {
        val levels = Gdx.files.internal("campaign/levels/levels.json")
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
