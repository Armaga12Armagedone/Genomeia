package io.github.some_example_name.lwjgl3.demo

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import io.github.some_example_name.lwjgl3.DesktopFileProvider
import io.github.some_example_name.old.bug.BugImage
import io.github.some_example_name.old.bug.BugKind
import io.github.some_example_name.old.bug.BugReplay
import io.github.some_example_name.old.bug.BugReports
import io.github.some_example_name.old.bug.ReplayTarget
import io.github.some_example_name.old.commands.GameCommand
import io.github.some_example_name.old.commands.GoGenomeEditor
import io.github.some_example_name.old.commands.GoMenu
import io.github.some_example_name.old.commands.GoReplace
import io.github.some_example_name.old.commands.GoSimulation
import io.github.some_example_name.old.commands.PlayerCommand
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.core.PlatformTuning
import io.github.some_example_name.old.editor.system.Axial
import io.github.some_example_name.old.editor.system.logic.GoToEndOfTimeLine
import io.github.some_example_name.old.editor.system.logic.NextTickButtonTap
import io.github.some_example_name.old.editor.system.logic.SetNeuralLinkColor
import io.github.some_example_name.old.editor.system.logic.SetSymmetry
import io.github.some_example_name.old.features.menu.MenuIntent
import io.github.some_example_name.old.features.simulation.SimulationIntent
import io.github.some_example_name.old.features.worldeditor.WorldEdit
import io.github.some_example_name.old.features.worldeditor.WorldEditorIntent
import io.github.some_example_name.old.features.worldeditor.WorldSpec
import io.github.some_example_name.old.game.MyGame
import io.github.some_example_name.old.systems.maps.MapSave
import io.github.some_example_name.render.RenderMode
import java.io.File
import kotlin.system.exitProcess

/**
 * Сквозная проверка записи и повтора бага: настоящая игра в окне, два запуска.
 *
 *  record — игра проходит сценарий ТОЛЬКО через обработчики команд, как игрок: меню,
 *           редактор генома, редактор мира, мир с организмами, клавиши контроллера,
 *           сохранение мира, рестарт. В конце — отчёт кнопкой-жуком (без окна).
 *  replay — новый процесс проигрывает этот .bug и сверяет: все команды применены,
 *           контрольные суммы журнала сошлись, хэши состояния мира совпали.
 *
 * Запуск: gradlew :lwjgl3:bugReplayCheck (record выполняется сам, перед ним).
 * Ненулевой код — регрессия: повтор разошёлся с записью.
 */
fun main(args: Array<String>) {
    val phase = args.firstOrNull() ?: "record"
    val pathFile = File(System.getProperty("bugReplayCheck.dir", "lwjgl3/build/bugReplayCheck"), "last-bug.txt")

    // Число воркеров симуляции. С одним порядок обработки чанков фиксирован — так проверяется
    // сам повтор, отдельно от недетерминизма параллельных фаз (см. итог проверки).
    System.getProperty("bugReplayCheck.workers")?.toIntOrNull()?.let { PlatformTuning.performanceCoreCount = it }

    // До создания игры: BugSession читает это свойство в самом начале create().
    val listener: (MyGame) -> ApplicationListener
    when (phase) {
        "record" -> {
            System.setProperty("genomeia.replay", "off")
            listener = { game -> Recorder(game, pathFile) }
        }
        "replay" -> {
            val bug = pathFile.readText().trim()
            println("Повтор: $bug")
            System.setProperty("genomeia.replay", bug)
            listener = { game -> Replayer(game) }
        }
        else -> error("фаза $phase: record или replay")
    }

    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Genomeia — проверка повтора бага ($phase)")
        setWindowedMode(900, 700)
        useVsync(false)
        setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3)
    }
    Lwjgl3Application(listener(MyGame(DesktopFileProvider())), config)
    exitProcess(exitCode)
}

private var exitCode = 1

/** Сценарий игрока. Каждый шаг — команда через экран (ReplayTarget) или навигацию. */
private class Recorder(private val game: MyGame, private val pathFile: File) : ApplicationListener by game {

    private var frames = 0
    private var step = 0
    private var waitUntilFrame = 0
    private var waitUntilTick = -1
    private var savedSlot: String? = null
    private var startedAt = System.nanoTime()

    override fun render() {
        try {
            game.render()
            frames++
            if (frames < waitUntilFrame) return
            if (waitUntilTick >= 0 && DISimulationContainer.simulationData.tickCounter < waitUntilTick) {
                check(System.nanoTime() - startedAt < TIMEOUT_NS) { "сценарий завис на шаге $step" }
                return
            }
            waitUntilTick = -1
            advance()
        } catch (e: Throwable) {
            e.printStackTrace()
            exitCode = 1
            Gdx.app.exit()
        }
    }

    private fun advance() {
        val data = DISimulationContainer.simulationData
        val navigation = DIGameGlobalContainer.navigationCommandsManager
        when (step++) {
            0 -> pause(10)

            // Редактор генома: первый геном из папки, листаем стадии, симметрия, цвет связей.
            1 -> {
                val genome = DIGameGlobalContainer.genomeJsonReader.getGenomeFileNamesFromFolder().first()
                screen(MenuIntent.OpenGenomeEditor(genome))
                pause(5)
            }
            2 -> {
                repeat(3) { screen(NextTickButtonTap) }
                screen(SetSymmetry(Axial))
                screen(SetNeuralLinkColor(0x33cc99ff))
                screen(GoToEndOfTimeLine)
                navigation.performCommand(GoReplace(GoMenu))
                pause(5)
            }

            // Редактор мира: свой сид, кисть, пара мазков.
            3 -> {
                screen(MenuIntent.OpenWorldEditor("31415926"))
                pause(5)
            }
            4 -> {
                screen(WorldEditorIntent.SetDayNight(9))
                screen(WorldEditorIntent.SetBrushSize(4))
                screen(WorldEditorIntent.Paint(40, 40))
                screen(WorldEditorIntent.Paint(41, 41))
                screen(WorldEditorIntent.SetErasing(true))
                screen(WorldEditorIntent.Paint(90, 90))
                // Кнопка «Создать мир» — сама навигация, рецепт — то, что сейчас в редакторе.
                val world = WorldSpec(192, 192, "31415926", 9, 0, listOf(
                    WorldEdit.Stroke(40, 40, 4, erase = false, circle = true),
                    WorldEdit.Stroke(41, 41, 4, erase = false, circle = true),
                    WorldEdit.Stroke(90, 90, 4, erase = true, circle = true),
                ))
                navigation.performCommand(GoSimulation(world, null))
                pause(5)
            }

            // Мир: максимальная скорость, организмы, клавиши, камера.
            5 -> {
                screen(SimulationIntent.SetMaxSpeed(true))
                screen(SimulationIntent.SetRenderMode(RenderMode.DEBUG))
                val genomes = DISimulationContainer.genomeManager.genomes
                val baked = genomes.indices.filter { genomes[it].cellsAmount > 0 }
                check(baked.isNotEmpty()) { "в папке genomes нет запечённых геномов" }
                screen(SimulationIntent.SelectGenome(DIGameGlobalContainer.genomeJsonReader.getGenomeFileNamesFromFolder()[baked.first()]))
                SPAWN.forEach { (x, y) -> DISimulationContainer.userCommandManager.push(PlayerCommand.Tap(x, y)) }
                screen(SimulationIntent.ZoomCamera(0.05f, 90f, 90f))
                untilTick(data.tickCounter + GROW_TICKS)
            }
            6 -> {
                screen(SimulationIntent.SetControllerKey(10, pressed = true))
                screen(SimulationIntent.SetControllerKey(14, pressed = true))
                untilTick(data.tickCounter + 120)
            }
            7 -> {
                screen(SimulationIntent.SetControllerKey(10, pressed = false))
                screen(SimulationIntent.SetControllerKey(14, pressed = false))
                // Перетаскивание частицы и вещество правой кнопкой.
                DISimulationContainer.userCommandManager.push(PlayerCommand.TouchDown(60f, 60f, true))
                DISimulationContainer.userCommandManager.push(PlayerCommand.Drag(61f, 61f, 1f, 1f))
                DISimulationContainer.userCommandManager.push(PlayerCommand.StopDrag)
                DISimulationContainer.userCommandManager.push(PlayerCommand.Tap(120f, 120f, isLeftButton = false))
                untilTick(data.tickCounter + 200)
            }

            // Отладочный стресс-тест (только при DEBUG_CHECKS): D разгоняет клетки, A убивает.
            // Тела рвутся, внутренние клетки становятся краевыми — то, на чём падала сетка.
            // -PstressTicks=N, по умолчанию выключен.
            8 -> {
                if (STRESS_TICKS > 0) {
                    screen(SimulationIntent.SetControllerKey(KEY_D, pressed = true))
                    untilTick(data.tickCounter + STRESS_TICKS)
                }
            }
            9 -> {
                if (STRESS_TICKS > 0) {
                    screen(SimulationIntent.SetControllerKey(KEY_D, pressed = false))
                    screen(SimulationIntent.SetControllerKey(KEY_A, pressed = true))
                    untilTick(data.tickCounter + STRESS_TICKS)
                }
            }
            10 -> {
                if (STRESS_TICKS > 0) screen(SimulationIntent.SetControllerKey(KEY_A, pressed = false))
                pause(2)
            }

            // Сохранение мира — повтор должен сделать его на том же тике.
            11 -> {
                val slot = DISimulationContainer.mapSave.nextSlot()
                savedSlot = slot
                screen(SimulationIntent.SaveWorld(slot))
                untilTick(data.tickCounter + 240)
            }

            // Рестарт: новая сессия мира со своим сидом.
            12 -> {
                screen(SimulationIntent.Restart)
                pause(10)
            }
            13 -> {
                SPAWN.take(2).forEach { (x, y) -> DISimulationContainer.userCommandManager.push(PlayerCommand.Tap(x + 5f, y + 5f)) }
                untilTick(data.tickCounter + GROW_TICKS)
            }

            // Отчёт кнопкой-жуком (без окна) — и конец.
            14 -> {
                val bug = BugReports.create(BugKind.REPORT, "bugReplayCheck: сквозная проверка")
                // Как в окне отчёта: игрок дописал пояснение и приложил картинку после
                // первой кнопки — перепаковка не должна задеть журнал (повтор это проверит).
                BugReports.rewrite(bug, "bugReplayCheck: перепаковано", listOf(BugImage("1-test.png", TEST_PNG)))
                pathFile.parentFile.mkdirs()
                pathFile.writeText(bug.file().absolutePath)
                println("Записан ${bug.file().absolutePath} (${bug.length() / 1024} КБ) на тике ${data.tickCounter}")
                savedSlot?.let { MapSave.mapFile(it).delete() }
                exitCode = 0
                Gdx.app.exit()
            }
        }
    }

    /** Команда текущему экрану — тем же путём, что нажатие. */
    private fun screen(command: GameCommand) {
        val target = DIGameGlobalContainer.game.screen as? ReplayTarget
            ?: error("экран ${DIGameGlobalContainer.game.screen} не принимает команды")
        check(target.replay(command)) { "экран ${target::class.simpleName} не принял ${command.name}" }
    }

    private fun pause(frameCount: Int) {
        waitUntilFrame = frames + frameCount
    }

    private fun untilTick(tick: Int) {
        waitUntilTick = tick
    }

    companion object {
        /**
         * Организмы стоят плотно: столкновения тел — то место, где порядок потоков мог бы
         * просочиться в мир. -PspawnGrid=N ставит их сеткой N×N.
         */
        /** Картинка 1×1 PNG для проверки перепаковки. */
        val TEST_PNG: ByteArray = java.util.Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")

        val SPAWN: List<Pair<Float, Float>> = run {
            val n = System.getProperty("bugReplayCheck.spawnGrid")?.toIntOrNull() ?: 2
            val step = 150f / n
            List(n * n) { i -> (25f + step * (i % n) + step / 2) to (25f + step * (i / n) + step / 2) }
        }

        /** Сколько тиков держать каждую клавишу стресс-теста (D, потом A): -PstressTicks=300. */
        val STRESS_TICKS = System.getProperty("bugReplayCheck.stressTicks")?.toIntOrNull() ?: 0
        const val KEY_A = 11
        const val KEY_D = 13

        /** Сколько тиков мир растёт после посадки: -PgrowTicks=3000 — длинный прогон. */
        val GROW_TICKS = System.getProperty("bugReplayCheck.growTicks")?.toIntOrNull() ?: 300

        const val TIMEOUT_NS = 300_000_000_000L
    }
}

/** Повтор: ждём итог BugReplay и выходим с его кодом. */
private class Replayer(private val game: MyGame) : ApplicationListener by game {

    private val startedAt = System.nanoTime()

    override fun render() {
        game.render()
        val result = BugReplay.result
        when {
            result != null -> {
                println(if (result) "PASS: повтор совпал с записью" else "FAIL: повтор не дал ожидаемого — см. итог выше")
                exitCode = if (result) 0 else 1
                Gdx.app.exit()
            }
            System.nanoTime() - startedAt > TIMEOUT_NS -> {
                println("FAIL: повтор не закончился за ${TIMEOUT_NS / 1_000_000_000} с")
                exitCode = 1
                Gdx.app.exit()
            }
        }
    }

    private companion object {
        const val TIMEOUT_NS = 300_000_000_000L
    }
}
