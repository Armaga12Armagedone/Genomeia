package io.github.some_example_name.old.systems.debug
import io.github.some_example_name.old.commands.PlayerCommand
import io.github.some_example_name.old.commands.WorldCommandBuffer
import io.github.some_example_name.old.commands.WorldCommandType
import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.core.DISimulationContainer.simulationData
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.lang.Integer.max
import java.util.Dictionary

class LogReplay {
    val file = File("debug.bin")
    val dataStream = DataInputStream(file.inputStream())
    val tickDictionary = mutableMapOf<Int, MutableList<CommandFull>>()
    val tickDictionarySecond = mutableMapOf<Int, MutableList<CommandFull>>()
    val tickDictionaryLast = mutableMapOf<Int, MutableList<CommandFull>>()
    var currentTick = 0
    var maxTick = 0
    var commandCount = 0 //DEBUG ONLU!

    init {

    }

    fun play() {
        println("WIDTH: ${dataStream.readInt()}")
        println("HEIGHT: ${dataStream.readInt()}")
        parse()
//        readTick()
//        readTick()
    }

    fun parse() {
        try {
//            while (true) {
//                parseTick()
//            }
            while (true) {
                val cmd = dataStream.readInt()
                when (cmd) {
                    -555 -> {
                        val tickTime = dataStream.readFloat() //просто тик, идем дальше.
                        currentTick += 1
                    }
                    -999 -> {
                        println("TICK STOP")
                    }
                    -767 -> {
                        parseUserCommand() //команда пользователя
                    }
                    -404 -> {
                        parseCommand() // обычная окманда
                        commandCount += 1
                    }
                    else -> {
                        println("UNknown codon: ${cmd}")
                        return // Останавливаемся, чтобы не читать мусор
                    }
                }
            }
        }
        catch (e: EOFException) {
            println("file end")
            println(maxTick)
            println("Command Count ${commandCount}")
            DISimulationContainer.worldCommandsManager.replay = true

        }
        finally {
            dataStream.close()
        }
    }


    fun parseUserCommand() {
        val cmd = dataStream.readInt()
        var command: PlayerCommand? = null

        when (cmd) {
            0 -> {
                command = PlayerCommand.StopDrag
                dataStream.readInt() // for split
            }

            1 -> {
                val x = dataStream.readFloat()
                val y = dataStream.readFloat()
                val dx = dataStream.readFloat()
                val dy = dataStream.readFloat()
                command = PlayerCommand.Drag(x, y, dx, dy)

                dataStream.readInt()
            }

            2 -> {
                val x = dataStream.readFloat()
                val y = dataStream.readFloat()
                val isLeftButton = dataStream.readBoolean()

                command = PlayerCommand.Tap(x, y, isLeftButton)

                dataStream.readInt()
            }

            3 -> {
                val x = dataStream.readFloat()
                val y = dataStream.readFloat()
                val isLeftButton = dataStream.readBoolean()

                command = PlayerCommand.TouchDown(x, y, isLeftButton)

                dataStream.readInt()
            }
        }

        if (command != null) {
//            println(command)
            DISimulationContainer.userCommandManager.push(command)
        }
    }

    fun parseCommand() {
        var bools: BooleanArray? = null
        var ints: IntArray? = null
        var floats: FloatArray? = null
        var commandType: WorldCommandType? = null


//        if (command == -767) {
//            parseUserCommand()
//        }

        if (true) {
            val commandTypeInt = dataStream.readInt()
            commandType = WorldCommandType.fromId(commandTypeInt)

            val intsLen = dataStream.readInt()

//            println("INTS ARRAY LENGTH: ${intsLen}")

            ints = IntArray(intsLen)
            for (i in 0..intsLen-1) {
                ints[i] = dataStream.readInt()
            }

            if (dataStream.readInt() != -200) {
                println("something went wrong in ints split")
                return
            }


            val floatLen = dataStream.readInt()
//            println("FLOAT ARRAY LENGTH: ${floatLen}")

            floats = FloatArray(floatLen)

            for (i in 0..floatLen-1) {
                floats[i] = dataStream.readFloat()
            }

            if (dataStream.readInt() != -200) {
                println("something went wrong in floats split")
                return
            }

            val boolLen = dataStream.readInt()
            bools = BooleanArray(boolLen)
            for (i in 0..boolLen-1) {
                bools[i] = dataStream.readBoolean()
            }

            if (dataStream.readInt() != -200) {
                println("something went wrong in bools split")
                return
            }
        }

//        println(commandType)

        if (commandType != null) {
            saveReplay(CommandFull(commandType, ints, floats, bools))
        }

        val endCodon = dataStream.readInt()

        if (endCodon == -666) {
//            println("stop codon")
            return
        }
        else {
            println("something wrong in read end codon: ${endCodon}")
        }
    }

    fun saveReplay(command: CommandFull) {
        if (command.command == WorldCommandType.ADD_LINK_BY_ID) {
            //DISimulationContainer.worldCommandsManager.worldCommandSecondBuffer[0].push(command.command, command.ints, command.floats, command.bools)
            tickDictionarySecond.getOrPut(currentTick) { mutableListOf() }.add(command)
        }
        else if (command.command == WorldCommandType.ADD_ORGAN) {
            //DISimulationContainer.worldCommandsManager.worldCommandLastBuffer.push(command.command, command.ints, command.floats, command.bools)
            tickDictionaryLast.getOrPut(currentTick) { mutableListOf() }.add(command)
        }
        else {
//            DISimulationContainer.worldCommandsManager.worldCommandBuffer[0].push(command, ints, floats, bools)
//            if (!tickDictionary.containsKey(currentTick)) {
//                tickDictionary[currentTick] = mutableListOf<CommandFull>()
//            }
//            tickDictionary[currentTick]?.add(command)
            tickDictionary.getOrPut(currentTick) { mutableListOf() }.add(command)

        }
        maxTick = currentTick

//        if (command == WorldCommandType.DIVIDE_ALIVE_CELL_ACTION_COUNTER) {
//            println(ints?.get(0) ?: -1)
//            val itsis = IntArray(10) {0; 1; 0; 4;1;1;1;1;-1;-1;1}
//            val floatis = FloatArray(10) {5f; 5f; 0.5f; 0f;0f;0f;0f;0f;0f;0f;0f;}
//            val boolits = BooleanArray(2) {false; false; false;}
//            DISimulationContainer.worldCommandsManager.worldCommandBuffer[0].push(WorldCommandType.ADD_CELL, itsis, floatis, boolits)
//        }
    }

    fun replyTick(tick: Int, buffer: Int): Boolean {
        when(buffer) {
            0-> {
                if (tick <= tickDictionary.size) {
                    tickDictionary[tick]?.forEach {
                        DISimulationContainer.worldCommandsManager.worldCommandBuffer[0].push(
                            it.command,
                            it.ints,
                            it.floats,
                            it.bools,  true
                        )
                    }
                }
            }
            1-> {
                if (tick <= tickDictionarySecond.size) {
                    tickDictionarySecond[tick]?.forEach {
                        DISimulationContainer.worldCommandsManager.worldCommandSecondBuffer[0].push(
                            it.command,
                            it.ints,
                            it.floats,
                            it.bools, true
                        )
                    }
                }
            }
            2-> {
                if (tick <= tickDictionaryLast.size) {
                    tickDictionaryLast[tick]?.forEach {
                        DISimulationContainer.worldCommandsManager.worldCommandLastBuffer.push(
                            it.command,
                            it.ints,
                            it.floats,
                            it.bools, true
                        )
                    }
                }
            }
        }
        if (tick <= maxTick) {
            return true
        }

        DISimulationContainer.worldCommandsManager.replay = false
        return false
    }

    data class CommandFull(
        val command: WorldCommandType,
        val ints: IntArray?,
        val floats: FloatArray?,
        val bools: BooleanArray?
    )
}
