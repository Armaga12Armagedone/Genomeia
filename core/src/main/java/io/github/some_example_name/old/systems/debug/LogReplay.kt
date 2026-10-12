package io.github.some_example_name.old.systems.debug

import com.badlogic.gdx.Gdx
import io.github.some_example_name.old.commands.PlayerCommand
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException

/**
 * Воспроизведение сессии, записанной [LogSaver].
 *
 * Потоки: [request] зовётся из UI — читает файл целиком и кладёт готовую [Recording]
 * в volatile-поле. Всё остальное работает только в потоке симуляции: он забирает запись
 * через [takePending] в начале тика, перезапускает мир и дальше в каждом тике подаёт
 * клавиши ([applyKeys]) и команды ([forEachCommandAt]) ровно в те тики, в которые они
 * применились при записи. Пока идёт воспроизведение, живой ввод игрока игнорируется.
 *
 * Дойдя до конца записи, симуляция встаёт на паузу — на том моменте, где игрок
 * остановился (или где игра упала).
 */
class LogReplay {

    class Recording(
        val header: ReplayFormat.Header,
        val commandTicks: IntArray,
        val commands: List<PlayerCommand>,
        val keyTicks: IntArray,
        val keyMasks: IntArray,
        /** Последний тик сессии: TAG_END, а если игра упала — последний тик в файле. */
        val endTick: Int
    )

    @Volatile
    private var pending: Recording? = null

    // --- Дальше только поток симуляции ---

    private var current: Recording? = null
    private var nextCommand = 0
    private var nextKeys = 0

    /** Индекс генома в записи -> индекс в текущем genomeManager.genomes, -1 если нет. */
    private var genomeRemap = IntArray(0)

    val isActive: Boolean get() = current != null

    val endTick: Int get() = current?.endTick ?: -1

    /**
     * UI-поток. Читает файл записи и ставит его в очередь на воспроизведение.
     * @return null если всё хорошо, иначе текст ошибки для игрока.
     */
    fun request(): String? {
        val file = ReplayFormat.file() ?: return "запись недоступна на этой платформе"
        if (!file.exists()) return "нет файла ${file.path()}"

        return try {
            pending = DataInputStream(BufferedInputStream(file.read())).use { parse(it) }
            null
        } catch (e: Exception) {
            Gdx.app?.error(TAG, "не удалось прочитать ${file.path()}", e)
            "не удалось прочитать ${file.name()}: ${e.message}"
        }
    }

    fun takePending(): Recording? {
        val recording = pending ?: return null
        pending = null
        return recording
    }

    /**
     * Мир уже пересоздан, tickCounter = 0. Сверяет запись с текущим миром и включает
     * воспроизведение. Расхождения только логируются: запустить всё равно полезно,
     * а лог объяснит, почему сценарий пошёл иначе.
     */
    fun start(
        recording: Recording,
        gridWidth: Int,
        gridHeight: Int,
        threadCount: Int,
        genomeNames: List<String>,
        initialParticles: Int
    ) {
        val header = recording.header
        if (header.gridWidth != gridWidth || header.gridHeight != gridHeight) {
            warn("мир ${header.gridWidth}x${header.gridHeight}, а сейчас ${gridWidth}x$gridHeight")
        }
        if (header.threadCount != threadCount) {
            warn("записано на ${header.threadCount} потоках, а сейчас $threadCount")
        }
        if (header.initialParticles != initialParticles) {
            warn("на старте было ${header.initialParticles} частиц, а сейчас $initialParticles — другая карта?")
        }

        // Сначала тот же индекс (имена геномов не обязаны быть уникальными), потом поиск
        // по имени — если список геномов с момента записи поменялся.
        genomeRemap = IntArray(header.genomeNames.size) { index ->
            val name = header.genomeNames[index]
            if (genomeNames.getOrNull(index) == name) index else genomeNames.indexOf(name)
        }
        header.genomeNames.forEachIndexed { index, name ->
            if (genomeRemap[index] == -1) warn("нет генома '$name', его организмы не появятся")
        }

        current = recording
        nextCommand = 0
        nextKeys = 0
        Gdx.app?.log(
            TAG,
            "воспроизведение: ${recording.commands.size} команд, до тика ${recording.endTick}, seed=${header.seed}"
        )
    }

    fun stop() {
        current = null
    }

    /** Выставляет клавиши контроллера такими, какими они были в этом тике при записи. */
    fun applyKeys(tick: Int, keys: BooleanArray) {
        val recording = current ?: return
        while (nextKeys < recording.keyTicks.size && recording.keyTicks[nextKeys] <= tick) {
            val mask = recording.keyMasks[nextKeys]
            for (i in keys.indices) keys[i] = mask and (1 shl i) != 0
            nextKeys++
        }
    }

    inline fun forEachCommandAt(tick: Int, action: (PlayerCommand) -> Unit) {
        while (true) {
            val cmd = nextCommandAt(tick) ?: return
            action(cmd)
        }
    }

    fun nextCommandAt(tick: Int): PlayerCommand? {
        val recording = current ?: return null
        while (nextCommand < recording.commandTicks.size && recording.commandTicks[nextCommand] <= tick) {
            val cmd = recording.commands[nextCommand++]
            if (cmd is PlayerCommand.Tap && cmd.isLeftButton) {
                val genomeIndex = cmd.genomeIndex ?: continue
                val mapped = genomeRemap.getOrElse(genomeIndex) { -1 }
                if (mapped == -1) continue
                if (mapped != genomeIndex) {
                    return PlayerCommand.Tap(cmd.x, cmd.y, cmd.isLeftButton, mapped)
                }
            }
            return cmd
        }
        return null
    }

    private fun parse(input: DataInputStream): Recording {
        val header = ReplayFormat.readHeader(input)
        val commandTicks = IntArrayList()
        val commands = ArrayList<PlayerCommand>()
        val keyTicks = IntArrayList()
        val keyMasks = IntArrayList()
        var lastTick = 0
        var endTick = -1

        try {
            loop@ while (true) {
                val tag = input.readUnsignedByte()
                val tick = input.readInt()
                when (tag) {
                    ReplayFormat.TAG_COMMAND -> {
                        commands += readCommand(input)
                        commandTicks.add(tick)
                    }
                    ReplayFormat.TAG_KEYS -> {
                        keyMasks.add(input.readInt())
                        keyTicks.add(tick)
                    }
                    ReplayFormat.TAG_TICK -> Unit
                    ReplayFormat.TAG_END -> {
                        endTick = tick
                        break@loop
                    }
                    else -> {
                        warn("неизвестная запись $tag, файл повреждён — читаем до неё")
                        break@loop
                    }
                }
                lastTick = maxOf(lastTick, tick)
            }
        } catch (_: EOFException) {
            // Сессия оборвалась (игра упала или ещё пишется): последняя запись могла
            // не дописаться, всё до неё валидно.
        }

        return Recording(
            header = header,
            commandTicks = commandTicks.toIntArray(),
            commands = commands,
            keyTicks = keyTicks.toIntArray(),
            keyMasks = keyMasks.toIntArray(),
            endTick = if (endTick >= 0) endTick else lastTick
        )
    }

    private fun readCommand(input: DataInputStream): PlayerCommand =
        when (val type = input.readUnsignedByte()) {
            ReplayFormat.CMD_STOP_DRAG -> PlayerCommand.StopDrag
            ReplayFormat.CMD_DRAG -> PlayerCommand.Drag(
                x = input.readFloat(),
                y = input.readFloat(),
                dx = input.readFloat(),
                dy = input.readFloat()
            )
            ReplayFormat.CMD_TAP -> PlayerCommand.Tap(
                x = input.readFloat(),
                y = input.readFloat(),
                isLeftButton = input.readBoolean(),
                genomeIndex = input.readInt()
            )
            ReplayFormat.CMD_TOUCH_DOWN -> PlayerCommand.TouchDown(
                x = input.readFloat(),
                y = input.readFloat(),
                isLeftButton = input.readBoolean()
            )
            else -> throw IllegalStateException("неизвестная команда $type")
        }

    private fun warn(message: String) {
        Gdx.app?.error(TAG, message)
    }

    private companion object {
        const val TAG = "LogReplay"
    }
}
