package io.github.some_example_name.old.systems.logic

import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.DISimulationContainer
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import com.badlogic.gdx.Gdx
import io.github.some_example_name.old.features.levels.dialogs.PopUp
import io.github.some_example_name.old.features.simulation.SimulationScreen

class LogicEnv {
    val popups = com.badlogic.gdx.utils.Queue<PopUp>()
    var popupActive = false

    val getCellsCount = LuaNativeFunction {
        listOf(LuaNumber.of(DISimulationContainer.simulationSystem.getStats().cells))
    }

    fun win() {//при isLevel у каждой map есть свой id, при win мы отображаем popup а затем засчитываем json файл с levelam'и как пройденный

    }

    fun loose() {

    }

    fun distanceToFinalPoint(): Float {
        return 0f
    }

    val popup = LuaNativeFunction {
        args -> val text = (args.getOrNull(0) as? LuaString)?.value ?: ""
        //println(text)
        Gdx.app.postRunnable {
            val popuk = PopUp("popup", "test", text)//.show((DIGameGlobalContainer.game.screen as SimulationScreen).getStage())
            popups.addLast(popuk)
        }
        listOf()
    }

    fun onTick() {

    }

    fun execute() {
        if (!popupActive && !popups.isEmpty && DIGameGlobalContainer.game.screen is SimulationScreen) {
            popups.get(0).show((DIGameGlobalContainer.game.screen as SimulationScreen).getStage())
            popups.removeFirst()
            popupActive = true
        }
    }
}
