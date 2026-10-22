package io.github.some_example_name.old.systems.logic

import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.LuaVmImpl
import ai.tenum.lua.vm.errorhandling.LuaException
import ai.tenum.lua.vm.errorhandling.LuaRuntimeError
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.systems.simulation.SimulationData

class EnvRun {
    val globals = LuaVmImpl()
    val env = LogicEnv()

    var logicScript = ""

    init {
        globals.globals.set("env", LuaNativeFunction { args -> listOf(LuaNumber.of(5))})
        globals.globals.set("getCells", env.getCellsCount)
        globals.globals.set("popup", env.popup)
        //globals.set("env", CoerceJavaToLua.coerce(env))
        // run("""print("Cells: ".. env:getCellsCount())""") работает
        //logicScript = """function tick() popup("hi") end"""
        logicScript = """popup("test") popup("hi")"""
        DISimulationContainer.simulationData.hasLua = true
        //run()
        //tick()
    }

    fun run(): String {
        try {
            //globals.load(logicScript).call()
            globals.execute(logicScript)
            return ""
        }
        catch (e: LuaRuntimeError) {
            return e.message ?: ""
        }
        catch (e: LuaException) {
            return e.message ?: ""
        }
    }

    fun tick(): String {
        try {
            env.execute()
            val tick = globals.globals.get("tick")
            if (tick == null) {
                return "Function tick hasn't found"
            }
            else if (tick !is LuaFunction) {
                return "name tick reserved to system function"
            }
            tick.call(listOf())
            return ""
        }
        catch (e: LuaException) {
            return e.message ?: ""
        }
        catch (e: LuaRuntimeError) {
            return e.message ?: ""
        }
    }

    fun setScript(text: String) {
        logicScript = text
    }
}
