package io.github.some_example_name.old.systems.debug

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.cells.Zygote
import io.github.some_example_name.old.commands.PlayerCommand
import io.github.some_example_name.old.commands.WorldCommandType
import io.github.some_example_name.old.ui.screens.GlobalSettings
import io.github.some_example_name.old.ui.screens.GlobalSettings.DEBUG_MODE
import java.io.DataOutputStream
import java.io.File
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.core.DISimulationContainer.simulationData
import io.github.some_example_name.old.core.utils.collectParticles
import io.github.some_example_name.old.systems.simulation.SimulationData
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class LogSaver { //TODO: Проверить в будущем что ВСЯ симуляция использует только 1 вид рандома.

    var file: File? = null
    var dataStream: DataOutputStream? = null
    var commandCount = 0 //DEBUG ONLY REMOVE IF RELEASE

    private val isClosed = AtomicBoolean(false)

    fun initLog() {
        if (DEBUG_MODE) {
            file = File("debug.bin")
            dataStream = DataOutputStream(file?.outputStream())

            dataStream?.writeInt(DISimulationContainer.gridWidth)
            dataStream?.writeInt(DISimulationContainer.gridHeight)
            println("size writed")
            dataStream?.writeLong(DISimulationContainer.seed)
            println("Seed writed: ${DISimulationContainer.seed}")

            Runtime.getRuntime().addShutdownHook(Thread {
                close() // запишет -050 и текущий тик, затем закроет поток
            })
        }
    }

    fun saveTick() {
        dataStream?.flush()
        dataStream?.writeInt(-555) // начальный кодон тика
//        dataStream?.writeInt(simulationData.tickCounter)
        println("tick saved")
    }

    fun closeTick() {
        dataStream?.writeInt(-999) //кодон конца тика
        println("tick closed")
    }

    fun saveUserCommand(type: PlayerCommand) {
//        println("got data")
//        println("Command: ${type.toString()}")
        synchronized(this) {
            dataStream?.writeInt(-767)//split codon for user command
            dataStream?.writeInt(simulationData.tickCounter)

            when (type) {
                PlayerCommand.StopDrag -> {
                    println("stop dragging")
                    dataStream?.writeInt(0) // command type

                    dataStream?.writeInt(-676) //end codon
                }

                is PlayerCommand.Drag -> {
                    println("dragging")
                    dataStream?.writeInt(1)
                    dataStream?.writeFloat(type.x)
                    dataStream?.writeFloat(type.y)
                    dataStream?.writeFloat(type.dx)
                    dataStream?.writeFloat(type.dy)

                    dataStream?.writeInt(-676)
                }

                is PlayerCommand.Tap -> {
                    println("tapped")
                    dataStream?.writeInt(2)

                    dataStream?.writeFloat(type.x)
                    dataStream?.writeFloat(type.y)
                    dataStream?.writeBoolean(type.isLeftButton)
                    dataStream?.writeInt(type.genomeIndex)//DISimulationContainer.simulationData.currentGenomeIndex)
                    //dataStream?.writeFloat(type.angle)

                    dataStream?.writeInt(-676)
                }

                is PlayerCommand.TouchDown -> {
                    println("touch downed")
                    dataStream?.writeInt(3)

                    dataStream?.writeFloat(type.x)
                    dataStream?.writeFloat(type.y)
                    dataStream?.writeBoolean(type.isLeftButton)

                    dataStream?.writeInt(-676)
                }
            }
            commandCount += 1

//            dataStream?.writeInt(-858) //end codon of user commands
        }
        println("Command Count: ${commandCount}")
    }


    fun save(type: WorldCommandType,ints: IntArray, floats: FloatArray, booleans: BooleanArray) {
        if (DEBUG_MODE && false) { //false-установлен что бы не сохраняло, попробую переделать систему.
            commandCount += 1
            synchronized(this) {
                dataStream?.writeInt(-404) // Стартовый кодон
                val intCount = type.intParamsCount
                val floatCount = type.floatParamsCount
                val boolCount = type.booleanParamsCount

                dataStream?.writeInt(type.id) // тип команы

                 println(type)

                // Сначала писать размер массива а затем записывать
                dataStream?.writeInt(intCount)
                for (value in ints.slice(0 until intCount)) {
                    dataStream?.writeInt(value) //массив int
                }
                dataStream?.writeInt(-200) //разделительный кодон, HTTP STATUS CODE 200 SUCCESFULL :)

                dataStream?.writeInt(floatCount)
                for (value in floats.slice(0 until floatCount)) {
                    dataStream?.writeFloat(value) // массив float
                }
                dataStream?.writeInt(-200)

                dataStream?.writeInt(boolCount)
                for (value in booleans.slice(0 until boolCount)) {
                    dataStream?.writeBoolean(value) // массив bool
                }
                dataStream?.writeInt(-200)

                dataStream?.writeInt(-666) // стоп кодон

            //            dataStream?.flush()
            }
        }
    }

    fun close() {
        // Гарантируем однократную запись маркера и закрытие потока
        if (isClosed.compareAndSet(false, true)) {
            try {
                // Записываем значение -050 и текущий тик перед закрытием
                dataStream?.writeInt(-50)                // значение -050
                dataStream?.writeInt(simulationData.tickCounter) // текущий тик
                dataStream?.flush()
            } catch (_: Exception) {
                // Игнорируем, если поток уже повреждён
            } finally {
                try {
                    dataStream?.close()
                } catch (_: Exception) {
                    // Уже закрыт или ошибка
                }
            }
        }
    }
}
