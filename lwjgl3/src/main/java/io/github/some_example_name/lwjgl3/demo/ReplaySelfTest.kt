package io.github.some_example_name.lwjgl3.demo

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import io.github.some_example_name.old.commands.PlayerCommand
import io.github.some_example_name.old.systems.debug.LogReplay
import io.github.some_example_name.old.systems.debug.LogSaver
import io.github.some_example_name.old.systems.debug.ReplayFormat
import java.io.File
import kotlin.system.exitProcess

/**
 * САМОПРОВЕРКА ЗАПИСИ СЕССИИ ИГРЫ (LogSaver / LogReplay), без окна и без мира.
 *
 *   gradlew :lwjgl3:replaySelfTest
 *
 * Пишет сессию с командами и клавишами контроллера, читает её обратно и проверяет, что
 * каждая команда и каждое состояние клавиш возвращаются в тот же тик, что и были
 * записаны. Отдельно — что переживаются оборванный файл (игра упала посреди записи),
 * чужой файл и переставленный список геномов, и что новая сессия не затирает прошлую.
 *
 * Сама симуляция тут не крутится: её детерминизм — отдельный вопрос, а здесь проверяется,
 * что запись не теряет и не сдвигает входы.
 */
fun main() {
    Gdx.app = java.lang.reflect.Proxy.newProxyInstance(
        Application::class.java.classLoader, arrayOf(Application::class.java)
    ) { _, m, args ->
        when (m.name) {
            "getType" -> Application.ApplicationType.Desktop
            "log", "error" -> {
                println("  [${args[0]}] ${args[1]}")
                null
            }
            else -> null
        }
    } as Application
    Gdx.files = Lwjgl3Files()

    val file = File(File("").absoluteFile, "debug.bin")
    val previous = File(File("").absoluteFile, ReplayFormat.PREVIOUS_FILE_NAME)
    file.delete()
    previous.delete()

    val failures = mutableListOf<String>()
    fun check(condition: Boolean, message: String) {
        if (!condition) failures += message
    }

    // --- 1. Запись ---
    val keyChanges = mapOf(3 to 0b101, 17 to 0b1_0000_0000_0000_0000, 40 to 0)
    val commands = listOf(
        5 to PlayerCommand.Tap(10.5f, 20.25f, isLeftButton = true, genomeIndex = 1),
        10 to PlayerCommand.TouchDown(11f, 21f, isLeftButton = true),
        10 to PlayerCommand.Drag(12f, 22f, 1f, -1f),
        11 to PlayerCommand.Drag(13f, 23f, 1f, 1f),
        12 to PlayerCommand.StopDrag,
        30 to PlayerCommand.Tap(50f, 60f, isLeftButton = false, genomeIndex = 0),
        55 to PlayerCommand.Tap(1f, 2f, isLeftButton = true, genomeIndex = 0)
    )
    val endTick = 70

    val saver = LogSaver()
    saver.begin(header(seed = 42L, genomes = listOf("Alpha", "Beta")))
    check(saver.isRecording, "запись не началась")
    for (tick in 1..endTick) {
        keyChanges[tick]?.let { saver.recordKeys(tick, it) }
        commands.filter { it.first == tick }.forEach { (_, cmd) ->
            saver.recordCommand(tick, cmd, (cmd as? PlayerCommand.Tap)?.genomeIndex ?: 0)
        }
        saver.endTick(tick)
    }
    saver.close(endTick)
    check(!saver.isRecording, "запись не закрылась")

    // --- 2. Воспроизведение со списком геномов в другом порядке ---
    val replay = LogReplay()
    check(replay.request() == null, "файл записи не прочитался")
    val recording = replay.takePending()
    check(recording != null, "запись не встала в очередь")
    check(replay.takePending() == null, "запись выдаётся дважды")
    recording!!
    check(recording.header.seed == 42L, "seed ${recording.header.seed} вместо 42")
    check(recording.endTick == endTick, "конец ${recording.endTick} вместо $endTick")

    replay.start(recording, 256, 256, 32, genomeNames = listOf("Beta", "Alpha"), initialParticles = 100)
    val replayed = mutableListOf<Pair<Int, PlayerCommand>>()
    val keys = BooleanArray(19)
    var expectedMask = 0
    for (tick in 1..endTick) {
        keyChanges[tick]?.let { expectedMask = it }
        replay.applyKeys(tick, keys)
        val mask = keys.foldIndexed(0) { i, acc, pressed -> if (pressed) acc or (1 shl i) else acc }
        check(mask == expectedMask, "тик $tick: клавиши ${mask.toString(2)} вместо ${expectedMask.toString(2)}")
        replay.forEachCommandAt(tick) { replayed += tick to it }
    }

    check(replayed.size == commands.size, "команд ${replayed.size} вместо ${commands.size}")
    commands.zip(replayed).forEach { (expected, actual) ->
        check(expected.first == actual.first, "${describe(expected.second)}: тик ${actual.first} вместо ${expected.first}")
        val want = expected.second.let {
            // Геномы в текущем списке переставлены: Alpha(0) -> 1, Beta(1) -> 0.
            if (it is PlayerCommand.Tap && it.isLeftButton) {
                PlayerCommand.Tap(it.x, it.y, it.isLeftButton, 1 - it.genomeIndex!!)
            } else it
        }
        check(describe(want) == describe(actual.second), "ожидалось ${describe(want)}, пришло ${describe(actual.second)}")
    }

    // --- 3. Оборванный файл: игра упала посреди записи ---
    val bytes = file.readBytes()
    // Минус TAG_END (5 байт) и хвост метки тика 60: последней целой записью остаётся
    // Tap на тике 55 — до него и должно дойти воспроизведение.
    file.writeBytes(bytes.copyOf(bytes.size - 7))
    check(replay.request() == null, "оборванный файл не прочитался")
    val truncated = replay.takePending()!!
    check(truncated.commands.size == commands.size, "в оборванном файле потерялись команды")
    check(truncated.endTick == 55, "конец оборванного файла ${truncated.endTick} вместо 55")

    // --- 4. Чужой файл ---
    file.writeText("not a replay")
    check(replay.request() != null, "чужой файл принят за запись")
    check(replay.takePending() == null, "чужой файл встал в очередь")

    // --- 5. Новая сессия сохраняет прошлую в debug.prev.bin ---
    file.writeBytes(bytes)
    saver.begin(header(seed = 7L, genomes = emptyList()))
    saver.close(0)
    check(previous.exists() && previous.readBytes().contentEquals(bytes), "прошлая запись не сохранилась в ${previous.name}")

    file.delete()
    previous.delete()

    if (failures.isEmpty()) {
        println("replaySelfTest: OK — ${commands.size} команд и ${keyChanges.size} смен клавиш вернулись в свои тики")
    } else {
        println("replaySelfTest: ОШИБКИ (${failures.size}):")
        failures.forEach { println("  - $it") }
        exitProcess(1)
    }
}

private fun header(seed: Long, genomes: List<String>) = ReplayFormat.Header(
    seed = seed,
    gridWidth = 256,
    gridHeight = 256,
    threadCount = 32,
    genomeNames = genomes,
    initialParticles = 100
)

private fun describe(cmd: PlayerCommand): String = when (cmd) {
    is PlayerCommand.Tap -> "Tap(${cmd.x}, ${cmd.y}, ${cmd.isLeftButton}, ${cmd.genomeIndex})"
    else -> cmd.toString()
}
