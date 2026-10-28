package io.github.some_example_name.old.systems.logic

import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.ObjectMap
import io.github.some_example_name.old.commands.GoBack
import io.github.some_example_name.old.commands.GoMenu
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.features.levels.dialogs.PopUp
import io.github.some_example_name.old.features.simulation.SimulationScreen
import kotlin.math.abs


class LogicEnv {
    val popups = com.badlogic.gdx.utils.Queue<PopUp>()
    var popupActive = false

    val getCellsCount = LuaNativeFunction {
        listOf(LuaNumber.of(DISimulationContainer.simulationSystem.getStats().cells))
    }

    val win = LuaNativeFunction {//при isLevel у каждой map есть свой id, при win мы отображаем popup а затем засчитываем json файл с levelam'и как пройденный
        //popup.call(listOf())
        if (DISimulationContainer.simulationData.isGuide) {
            //val levels = Gdx.files.internal("campaign/levels/levels.json")
            val file = Gdx.files.local("campaign/levels/levels.json")
            val json = Json()

            // 2. Читаем существующий файл или создаем новую карту, если файла нет
            val settings: ObjectMap<String, Boolean> = if (file.exists()) {
                // Передаем ObjectMap::class.java, а затем типы для String и Boolean
                json.fromJson(ObjectMap::class.java, java.lang.Boolean::class.java, file)
                    as ObjectMap<String, Boolean>
            } else {
                ObjectMap()
            }


            // 3. Перезаписываем или добавляем значения
            settings.put(DISimulationContainer.simulationData.level.id.toString(), true)


            // 4. Перезаписываем файл целиком (false означает "не добавлять в конец", а стереть и записать заново)
            file.writeString(json.prettyPrint(settings), false)
        }

        Gdx.app.postRunnable {
            DIGameGlobalContainer.navigationCommandsManager.performCommand(GoBack)
        }

        listOf()
    }

    fun loose() {
        Gdx.app.postRunnable {
            DIGameGlobalContainer.navigationCommandsManager.performCommand(GoBack)
        }
    }


    val popup = LuaNativeFunction {
        args -> val text = (args.getOrNull(0) as? LuaString)?.value ?: ""
        val title = (args.getOrNull(1) as? LuaString)?.value ?: "title"
        //println(text)
        Gdx.app.postRunnable {
            val popuk = PopUp(title, text)//.show((DIGameGlobalContainer.game.screen as SimulationScreen).getStage())
            popups.addLast(popuk)
        }
        listOf()
    }


    fun execute() {
        if (!popupActive && !popups.isEmpty && DIGameGlobalContainer.game.screen is SimulationScreen) {
            popups.get(0).show((DIGameGlobalContainer.game.screen as SimulationScreen).getStage())
            popups.removeFirst()
            popupActive = true
        }
    }


    //for level play:
    fun dist(): Boolean {
        val xs = mutableListOf<Float>()
        val ys = mutableListOf<Float>()

        for (ind in DISimulationContainer.cellEntity.particleIndexes) {
            if (ind < 0) {
                continue
            }
            //print(ind)
            val x = DISimulationContainer.particleEntity.x[ind]
            val y = DISimulationContainer.particleEntity.y[ind]
            xs.add(x)
            ys.add(y)
        }

        val xc = xs.sum()/xs.size
        val yc = ys.sum()/ys.size

        println("x: ${xc}, y: ${yc}")

        val destx = DISimulationContainer.simulationData.level.destination.first
        val desty = DISimulationContainer.simulationData.level.destination.second

        val ds1 = destx-xc
        if (abs(ds1)<=10) {
            val ys1 = desty-yc
            DIGameGlobalContainer.luaEnv.terrain()
            return ds1*ds1+ys1*ys1 <= 100f
        }
        return false
    }
}

