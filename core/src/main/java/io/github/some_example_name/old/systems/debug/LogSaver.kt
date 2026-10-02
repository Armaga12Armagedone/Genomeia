package io.github.some_example_name.old.systems.debug

import com.badlogic.gdx.Gdx
import io.github.some_example_name.old.commands.PlayerCommand
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Запись сессии игрока для воспроизведения бага (см. [LogReplay]).
 *
 * Пишутся не состояния, а входы симуляции: seed, команды игрока и клавиши контроллера,
 * каждая с номером тика, в котором она ПРИМЕНИЛАСЬ. Поэтому запись идёт из потока
 * симуляции в момент применения, а не из UI-потока в момент клика: клик и тик, который
 * его обработает, связаны случайно (зависят от FPS и загрузки), а тик применения — нет.
 *
 * Все методы вызываются только из потока симуляции, поэтому никакой синхронизации тут нет.
 * Ошибка ввода-вывода не должна ронять симуляцию: запись просто выключается.
 */
class LogSaver {

    private var stream: DataOutputStream? = null

    /** Есть ли что сбросить на диск: пишем часто, но сбрасываем только по делу. */
    private var dirty = false

    private var lastHeartbeatTick = 0

    val isRecording: Boolean get() = stream != null

    fun begin(header: ReplayFormat.Header) {
        close(lastHeartbeatTick)

        val file = ReplayFormat.file() ?: return
        try {
            // Каждая сессия (и каждый «Перезапустить») начинает файл заново. Предыдущую
            // запись держим рядом: баг обычно замечают уже после рестарта.
            if (file.exists()) file.moveTo(file.sibling(ReplayFormat.PREVIOUS_FILE_NAME))

            val out = DataOutputStream(BufferedOutputStream(file.write(false)))
            ReplayFormat.writeHeader(out, header)
            out.flush()
            stream = out
            lastHeartbeatTick = 0
            Gdx.app?.log(TAG, "запись сессии: ${file.file().absolutePath}, seed=${header.seed}")
        } catch (e: Exception) {
            fail(e)
        }
    }

    fun recordCommand(tick: Int, cmd: PlayerCommand, resolvedGenomeIndex: Int) {
        val out = stream ?: return
        try {
            out.writeByte(ReplayFormat.TAG_COMMAND)
            out.writeInt(tick)
            when (cmd) {
                PlayerCommand.StopDrag -> out.writeByte(ReplayFormat.CMD_STOP_DRAG)

                is PlayerCommand.Drag -> {
                    out.writeByte(ReplayFormat.CMD_DRAG)
                    out.writeFloat(cmd.x)
                    out.writeFloat(cmd.y)
                    out.writeFloat(cmd.dx)
                    out.writeFloat(cmd.dy)
                }

                is PlayerCommand.Tap -> {
                    out.writeByte(ReplayFormat.CMD_TAP)
                    out.writeFloat(cmd.x)
                    out.writeFloat(cmd.y)
                    out.writeBoolean(cmd.isLeftButton)
                    // Индекс уже разрешённый: у команды он может быть null ("текущий"),
                    // а текущий геном при воспроизведении будет другим.
                    out.writeInt(resolvedGenomeIndex)
                }

                is PlayerCommand.TouchDown -> {
                    out.writeByte(ReplayFormat.CMD_TOUCH_DOWN)
                    out.writeFloat(cmd.x)
                    out.writeFloat(cmd.y)
                    out.writeBoolean(cmd.isLeftButton)
                }
            }
            dirty = true
        } catch (e: IOException) {
            fail(e)
        }
    }

    fun recordKeys(tick: Int, mask: Int) {
        val out = stream ?: return
        try {
            out.writeByte(ReplayFormat.TAG_KEYS)
            out.writeInt(tick)
            out.writeInt(mask)
            dirty = true
        } catch (e: IOException) {
            fail(e)
        }
    }

    /**
     * Конец тика. Сбрасываем на диск, если в тике что-то записалось, и раз в
     * [HEARTBEAT_TICKS] пишем метку тика — по ней [LogReplay] знает, докуда шла сессия,
     * даже если игра упала и [close] так и не позвали.
     */
    fun endTick(tick: Int) {
        val out = stream ?: return
        try {
            if (tick - lastHeartbeatTick >= HEARTBEAT_TICKS) {
                out.writeByte(ReplayFormat.TAG_TICK)
                out.writeInt(tick)
                lastHeartbeatTick = tick
                dirty = true
            }
            if (dirty) {
                out.flush()
                dirty = false
            }
        } catch (e: IOException) {
            fail(e)
        }
    }

    /** [tick] — последний отработавший тик сессии: до него и пойдёт воспроизведение. */
    fun close(tick: Int) {
        val out = stream ?: return
        stream = null
        try {
            out.writeByte(ReplayFormat.TAG_END)
            out.writeInt(tick)
            out.flush()
        } catch (_: IOException) {
        } finally {
            try {
                out.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun fail(e: Exception) {
        Gdx.app?.error(TAG, "запись сессии выключена", e)
        try {
            stream?.close()
        } catch (_: IOException) {
        }
        stream = null
    }

    private companion object {
        const val TAG = "LogSaver"
        const val HEARTBEAT_TICKS = 30
    }
}
