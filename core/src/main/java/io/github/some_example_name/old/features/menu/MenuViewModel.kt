package io.github.some_example_name.old.features.menu

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.MathUtils
import io.github.some_example_name.old.bug.BugReplay
import io.github.some_example_name.old.commands.GameCommand
import io.github.some_example_name.old.commands.GoExit
import io.github.some_example_name.old.commands.GoGenomeEditor
import io.github.some_example_name.old.commands.GoSettings
import io.github.some_example_name.old.commands.GoSimulation
import io.github.some_example_name.old.commands.GoSupport
import io.github.some_example_name.old.commands.GoWorldEditor
import io.github.some_example_name.old.commands.UserCommandManager
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.DISingleThreadSimulationContainer
import io.github.some_example_name.old.core.GameFiles
import io.github.some_example_name.old.features.worldeditor.WorldEditorIntent
import io.github.some_example_name.old.features.worldeditor.WorldGenerator
import io.github.some_example_name.old.features.worldeditor.WorldTerrainManager
import io.github.some_example_name.old.journal.CommandSource
import io.github.some_example_name.old.journal.SessionAttachments
import io.github.some_example_name.old.journal.SessionJournal
import io.github.some_example_name.old.systems.genomics.genome.GenomeManager
import io.github.some_example_name.old.systems.maps.MapSave
import io.github.some_example_name.old.systems.maps.SavedMap
import io.github.some_example_name.old.systems.render.RenderSystem
import io.github.some_example_name.old.systems.simulation.SingleThreadSimulationSystem
import kotlinx.serialization.Serializable

/**
 * Что игрок хочет сделать в главном меню.
 *
 * Просмотр списков (миров, геномов) — не команда: это только окно с выбором, игру оно
 * не меняет. Команда — то, что выбрали.
 */
@Serializable
sealed interface MenuIntent : GameCommand {

    /** Новый мир в редакторе мира. Стартовый сид выбран заранее, см. [openWorldEditor]. */
    @Serializable
    data class OpenWorldEditor(val seed: String) : MenuIntent {
        override val name get() = "OpenWorldEditor"
        override val detail get() = seed
    }

    @Serializable
    data class OpenSavedWorld(val slot: String) : MenuIntent {
        override val name get() = "OpenSavedWorld"
        override val detail get() = slot
    }

    @Serializable
    data class OpenGenomeEditor(val genomeName: String?) : MenuIntent {
        override val name get() = "OpenGenomeEditor"
        override val detail get() = genomeName ?: "new"
    }

    /** Android: открыть системный выбор файла генома. Что выбрали — см. [GenomeImported]. */
    @Serializable
    data object ImportGenome : MenuIntent {
        override val name get() = "ImportGenome"
    }

    /**
     * Файл генома импортирован и лежит в папке геномов под именем [fileName].
     *
     * Какой файл выбрал игрок, решает система, а не игра, поэтому итог — отдельная
     * команда, а сам файл кладётся в приложения сессии ([attachment]): при повторе бага
     * геном берётся оттуда, а не из системного окна выбора.
     */
    @Serializable
    data class GenomeImported(val fileName: String, val attachment: String) : MenuIntent {
        override val name get() = "GenomeImported"
        override val detail get() = fileName
    }

    @Serializable
    data object OpenSettings : MenuIntent {
        override val name get() = "OpenSettings"
    }

    @Serializable
    data object OpenSupport : MenuIntent {
        override val name get() = "OpenSupport"
    }

    @Serializable
    data object Exit : MenuIntent {
        override val name get() = "Exit"
    }

    companion object {
        /** Кнопка нового мира: сид решается здесь, чтобы ехать в команде. */
        fun openWorldEditor(): OpenWorldEditor = OpenWorldEditor(WorldEditorIntent.newSeed().seed)
    }
}

class MenuViewModel(
    val simulationSystem: SingleThreadSimulationSystem = DISingleThreadSimulationContainer.simulationSystem,
    val renderSystem: RenderSystem = DISingleThreadSimulationContainer.renderSystem,
    val userCommandManager: UserCommandManager = DISingleThreadSimulationContainer.userCommandManager,
    val worldTerrainManager: WorldTerrainManager = DISingleThreadSimulationContainer.worldTerrainManager,
    val worldGenerator: WorldGenerator = WorldGenerator(),
    val genomeManager: GenomeManager = DISingleThreadSimulationContainer.genomeManager
) {

    private val navigation = DIGameGlobalContainer.navigationCommandsManager

    /** Импорт закончился (выбран файл или отменён) — экран закрывает диалог списка. */
    var onGenomeImportFinished: (() -> Unit)? = null

    private var tiltX = 0f
    private var tiltY = 0f

    private val maxTilt = 1.2f // максимальный «угол» в условных единицах
    private val sensitivity = 3.8f // насколько быстро набирается наклон
    private val damping = 0.92f // затухание (чтобы возвращалось в центр)

    /** Сохранённые миры для окна выбора. Запрос, а не команда: игру он не меняет. */
    fun savedWorlds(): List<SavedMap> = MapSave.listMaps()

    /** Геномы для окна выбора. */
    fun genomeNames(): List<String> = DIGameGlobalContainer.genomeJsonReader.getGenomeFileNamesFromFolder()

    /**
     * Единственный вход для действий игрока в меню.
     *
     * В журнале получается две строки на нажатие: намерение ("нажали Настройки") и переход
     * ("экран стал GoSettings"). Это не дубль — это разные слои, и когда переход когда-нибудь
     * окажется заблокирован или перенаправлен, они разойдутся, и в логе это будет видно.
     * Переход — вложенная команда: при повторе его заново делает само намерение.
     */
    fun handle(intent: MenuIntent) = SessionJournal.execute(CommandSource.MENU, intent) { apply(it) }

    private fun apply(intent: MenuIntent) {
        when (intent) {
            is MenuIntent.OpenWorldEditor -> navigation.performCommand(GoWorldEditor(intent.seed))

            is MenuIntent.OpenSavedWorld -> navigation.performCommand(
                GoSimulation(world = null, genomeName = null, savedWorld = intent.slot)
            )

            is MenuIntent.OpenGenomeEditor -> navigation.performCommand(GoGenomeEditor(intent.genomeName))

            is MenuIntent.ImportGenome -> {
                // При повторе окна выбора нет: файл придёт следующей командой из записи.
                if (BugReplay.isActive) return
                DIGameGlobalContainer.fileProvider.importGenome { file ->
                    // Колбэк системного окна приходит не в поток игры.
                    Gdx.app.postRunnable {
                        if (file != null) {
                            val attachment = SessionAttachments.attachImported(file)
                            handle(MenuIntent.GenomeImported(file.name(), attachment))
                        }
                        onGenomeImportFinished?.invoke()
                    }
                }
            }

            is MenuIntent.GenomeImported -> {
                // В игре файл уже скопирован в папку геномов окном выбора. При повторе его
                // там нет — кладём из приложений сессии.
                if (BugReplay.isActive) {
                    SessionAttachments.restore(intent.attachment, GameFiles.genomesDir().child(intent.fileName))
                }
            }

            is MenuIntent.OpenSettings -> navigation.performCommand(GoSettings)

            is MenuIntent.OpenSupport -> navigation.performCommand(GoSupport)

            is MenuIntent.Exit -> navigation.performCommand(GoExit)
        }
    }

    fun startMenuSimulation() {
//        val map = worldGenerator.generateWorld(
//            width = DISingleThreadSimulationContainer.gridWidth,
//            height = DISingleThreadSimulationContainer.gridHeight,
//            seed = Random.nextLong()
//        )
//        worldTerrainManager.map = map
//        worldTerrainManager.initWorld(
//            gridWith = DISingleThreadSimulationContainer.gridWidth,
//            gridHeight = DISingleThreadSimulationContainer.gridHeight,
//        )

        genomeManager.loadGenomes()

//        repeat(5) {
//            genomeManager.genomes.forEachIndexed { index, _ ->
//                userCommandManager.push(
//                    cmd = PlayerCommand.Tap(
//                        x = Random.nextFloat() * DISingleThreadSimulationContainer.gridWidth,
//                        y = Random.nextFloat() * DISingleThreadSimulationContainer.gridHeight,
//                        isLeftButton = true,
//                        genomeIndex = index
//                    )
//                )
//            }
//        }

//        userCommandManager.push(
//            cmd = PlayerCommand.Tap(
//                x = /*Random.nextFloat()*/0.5f * DISingleThreadSimulationContainer.gridWidth,
//                y = /*Random.nextFloat()*/0.5f * DISingleThreadSimulationContainer.gridHeight,
//                isLeftButton = true,
//                genomeIndex = 0
//            )
//        )
    }

    fun updateFrame() {
        simulationSystem.updateTick()
        renderSystem.render()
    }

    fun moveCamera(camera: Camera, delta: Float) {
        if (Gdx.input.isPeripheralAvailable(Input.Peripheral.Gyroscope)) {
            val rawX = Gdx.input.gyroscopeX
            val rawY = Gdx.input.gyroscopeY

            // Переназначаем оси в зависимости от текущей ориентации экрана
            val (gyroX, gyroY) = when (Gdx.input.rotation) {
                0 -> rawY to rawX          // Portrait (нормально)
                90 -> -rawX to rawY         // Landscape
                180 -> -rawY to -rawX        // Portrait перевёрнутый
                270 -> rawX to -rawY         // Landscape в другую сторону
                else -> rawY to rawX
            }

            // Интегрируем
            tiltX += gyroX * sensitivity * delta
            tiltY += gyroY * sensitivity * delta

            // Затухание
            tiltX *= damping
            tiltY *= damping

            // Ограничение
            tiltX = MathUtils.clamp(tiltX, -maxTilt, maxTilt)
            tiltY = MathUtils.clamp(tiltY, -maxTilt, maxTilt)

            camera.position.set(24f + tiltX, 24f - tiltY, 0f)
        } else {
            // fallback на мышь
            val x = (Gdx.input.x.toFloat() / Gdx.graphics.width - 0.5f) * 2f
            val y = (Gdx.input.y.toFloat() / Gdx.graphics.height - 0.5f) * 2f
            camera.position.set(24f + x, 24f - y, 0f)
        }
        camera.update()
    }

}
