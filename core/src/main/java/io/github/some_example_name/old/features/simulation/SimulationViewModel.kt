package io.github.some_example_name.old.features.simulation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import io.github.some_example_name.old.commands.GameCommand
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.core.log.ActionLog
import io.github.some_example_name.old.features.worldeditor.WorldSpec
import io.github.some_example_name.old.systems.maps.MapSave
import io.github.some_example_name.old.systems.render.RenderSystem
import io.github.some_example_name.old.systems.simulation.SimulationData
import io.github.some_example_name.old.systems.simulation.SimulationSystem
import io.github.some_example_name.render.RenderMode
import java.util.Locale

/**
 * Что игрок делает на экране симуляции, кроме касаний самого мира.
 *
 * Касания мира (поставить организм, схватить клетку) — это PlayerCommand: их применяет
 * поток симуляции в своём тике, и они пишутся в запись сессии с номером тика. Здесь —
 * всё остальное: время, выбор генома, вид, сохранение.
 */
sealed interface SimulationIntent : GameCommand {

    data class SetPaused(val paused: Boolean) : SimulationIntent {
        override val name get() = "SetPaused"
        override val detail get() = paused.toString()
    }

    /** «Поставить организм»: левая кнопка ставит организм, правая — субстанцию; выключено — наоборот. */
    data class SetPutOrganism(val enabled: Boolean) : SimulationIntent {
        override val name get() = "SetPutOrganism"
        override val detail get() = enabled.toString()
    }

    /** Геном для следующих организмов — по имени файла, см. [SimulationViewModel.genomeNames]. */
    data class SelectGenome(val fileName: String) : SimulationIntent {
        override val name get() = "SelectGenome"
        override val detail get() = fileName
    }

    data class SetTargetUps(val value: Int) : SimulationIntent {
        override val name get() = "SetTargetUps"
        override val detail get() = value.toString()
    }

    data class SetMaxSpeed(val enabled: Boolean) : SimulationIntent {
        override val name get() = "SetMaxSpeed"
        override val detail get() = enabled.toString()
    }

    data object Restart : SimulationIntent {
        override val name get() = "Restart"
    }

    data class SetRenderMode(val mode: RenderMode) : SimulationIntent {
        override val name get() = "SetRenderMode"
        override val detail get() = mode.name
    }

    /** Экранная клавиатура контроллера: показать или спрятать. */
    data class SetControllerKeysVisible(val visible: Boolean) : SimulationIntent {
        override val name get() = "SetControllerKeysVisible"
        override val detail get() = visible.toString()
    }

    /**
     * Клавиша контроллера нажата или отпущена — с экранной клавиатуры или с настоящей.
     *
     * Симуляция видит не эти события, а снимок клавиш в начале тика, и именно снимок идёт
     * в запись сессии с номером тика (SimulationSystem.latchControllerKeys). Команда
     * нужна, чтобы нажатие было видно в журнале рядом с остальными действиями.
     */
    data class SetControllerKey(val index: Int, val pressed: Boolean) : SimulationIntent {
        override val name get() = "SetControllerKey"
        override val detail get() = "$index ${if (pressed) "down" else "up"}"
    }

    /** Кнопка «Повтор сессии»: перезапустить мир и проиграть в нём debug.bin. */
    data object ReplaySession : SimulationIntent {
        override val name get() = "ReplaySession"
    }

    data object SaveWorld : SimulationIntent {
        override val name get() = "SaveWorld"
    }

    /** Спрятать или вернуть интерфейс поверх мира (клавиши Z / Y). */
    data class SetUiVisible(val visible: Boolean) : SimulationIntent {
        override val name get() = "SetUiVisible"
        override val detail get() = visible.toString()
    }

    /** Приложение свёрнуто — мир встаёт, пока игра в фоне. */
    data object AppPaused : SimulationIntent {
        override val name get() = "AppPaused"
    }

    /** Приложение развёрнуто — мир идёт, если шёл до сворачивания. */
    data object AppResumed : SimulationIntent {
        override val name get() = "AppResumed"
    }

    /** Сдвиг камеры перетаскиванием пустого места. Смещение — в координатах мира. */
    data class MoveCamera(val dx: Float, val dy: Float) : SimulationIntent {
        override val name get() = "MoveCamera"
    }

    /** Масштаб камеры колесом или щипком: итоговые масштаб и положение, а не приращение. */
    data class ZoomCamera(val zoom: Float, val x: Float, val y: Float) : SimulationIntent {
        override val name get() = "ZoomCamera"
        override val detail get() = String.format(Locale.ROOT, "%.4f", zoom)
    }
}

/** Что экран должен сделать со своей стороны после команды. */
sealed interface SimulationEffect {
    /** Состав или состояние кнопок поменялись — пересобрать панель. */
    data object RebuildMenu : SimulationEffect

    /** Поменялась пауза, но не по кнопке паузы — поправить её отметку. */
    data object PauseChanged : SimulationEffect

    data class ShowReplayError(val message: String) : SimulationEffect

    data class WorldSaved(val success: Boolean) : SimulationEffect
}

/**
 * Вся логика экрана симуляции, кроме вёрстки. Единственный вход для действий игрока —
 * [handle]: экран только собирает команду из нажатия и показывает результат.
 *
 * Отклик экрану идёт через [onEffect], а не возвратом из [handle]: сохранение мира
 * заканчивается только в следующем кадре (превью снимается после отрисовки).
 */
class SimulationViewModel(
    private val genomeName: String?,
    /** Рецепт мира из редактора. null — мир не из редактора (тест генома или сохранение). */
    world: WorldSpec?,
    /** Слот сохранённого мира: мир загружается из него. */
    private val savedWorld: String?,
    private val camera: OrthographicCamera,
    private val simulationData: SimulationData = DISimulationContainer.simulationData,
    private val simulationSystem: SimulationSystem = DISimulationContainer.simulationSystem,
    private val renderSystem: RenderSystem = DISimulationContainer.renderSystem
) {

    var onEffect: ((SimulationEffect) -> Unit)? = null

    /** Рецепт рельефа текущего мира: у загруженного он приходит из сохранения. */
    private var worldSpec = world

    /** Куда сохраняется мир. null — ещё не сохранялся, первое сохранение заведёт новый слот. */
    private var saveSlot = savedWorld

    var putOrganism = true
        private set

    var isUiVisible = true
        private set

    /**
     * Имена ФАЙЛОВ геномов, а не Genome.name.
     *
     * Индекс в этом списке используется как индекс в genomeManager.genomes, а тот
     * наполняется в loadGenomes ровно обходом getGenomeFileNamesFromFolder. Внутреннее
     * имя генома с именем файла совпадать не обязано: файл может быть переименован,
     * а при импорте на Android имя файла вообще приходило с префиксом (см.
     * AndroidFileProvider.sanitizeGenomeFileName). Диалог выбора отдаёт обратно именно
     * имя файла, поэтому и искать индекс надо в списке имён файлов.
     */
    var genomeNames: List<String> = emptyList()
        private set

    val isPaused: Boolean get() = !simulationData.isPlay

    /**
     * Шёл ли мир до сворачивания. Раньше разворачивание включало его всегда — и снимало
     * паузу, которую игрок поставил сам, а кнопка паузы оставалась нажатой.
     */
    private var wasPlayingBeforeAppPause = true

    fun handle(intent: SimulationIntent) {
        ActionLog.record(LOG_SOURCE, intent)

        when (intent) {
            is SimulationIntent.SetPaused -> simulationData.isPlay = !intent.paused

            is SimulationIntent.SetPutOrganism -> putOrganism = intent.enabled

            is SimulationIntent.SelectGenome -> {
                val index = genomeNames.indexOf(intent.fileName)
                if (index >= 0) {
                    simulationData.currentGenomeIndex = index
                } else {
                    // Раньше -1 молча уезжал в currentGenomeIndex, а падало уже потом и
                    // в другом месте — на genomes[-1] в момент постановки клетки, то есть
                    // в потоке симуляции.
                    Gdx.app.error("Genome", "геном '${intent.fileName}' не найден среди $genomeNames")
                }
            }

            is SimulationIntent.SetTargetUps -> simulationData.targetUPS = intent.value

            is SimulationIntent.SetMaxSpeed -> simulationData.maxSpeed = intent.enabled

            is SimulationIntent.Restart -> simulationData.isRestart = true

            is SimulationIntent.SetRenderMode -> renderSystem.mode = intent.mode

            is SimulationIntent.SetControllerKeysVisible -> {
                simulationData.showControllerKeys = intent.visible
                onEffect?.invoke(SimulationEffect.RebuildMenu)
            }

            is SimulationIntent.SetControllerKey ->
                simulationData.controllerKeyTouched[intent.index] = intent.pressed

            is SimulationIntent.ReplaySession -> {
                // Файл читается здесь, а мир перезапускается уже потоком симуляции
                // в начале следующего тика.
                val error = DISimulationContainer.logReplay.request()
                if (error == null) {
                    simulationData.isPlay = true
                    onEffect?.invoke(SimulationEffect.PauseChanged)
                } else {
                    onEffect?.invoke(SimulationEffect.ShowReplayError(error))
                }
            }

            is SimulationIntent.SaveWorld -> saveWorld()

            is SimulationIntent.SetUiVisible -> {
                if (isUiVisible == intent.visible) return
                isUiVisible = intent.visible
                renderSystem.isRenderUi = intent.visible
                onEffect?.invoke(SimulationEffect.RebuildMenu)
            }

            is SimulationIntent.AppPaused -> {
                wasPlayingBeforeAppPause = simulationData.isPlay
                simulationData.isPlay = false
                // Приложение свёрнуто: воркерам разрешаем спать долго. Без этого каждый из
                // них просыпается по таймауту парковки сотни раз в секунду всё время, что
                // игра висит в фоне, — на телефоне это заметный расход батареи впустую.
                DISimulationContainer.threadManager.executor.setBackground(true)
            }

            is SimulationIntent.AppResumed -> {
                simulationData.isPlay = wasPlayingBeforeAppPause
                DISimulationContainer.threadManager.executor.setBackground(false)
            }

            is SimulationIntent.MoveCamera -> renderSystem.moveCamera(intent.dx, intent.dy)

            is SimulationIntent.ZoomCamera -> {
                camera.zoom = intent.zoom
                camera.position.x = intent.x
                camera.position.y = intent.y
                camera.update()
            }
        }
    }

    /** Геномы мира — до первой сборки панели: по их именам работает выбор генома. */
    fun loadGenomes() {
        DISimulationContainer.genomeManager.loadGenomes(genomeName)
        genomeNames = if (genomeName == null) {
            DIGameGlobalContainer.genomeJsonReader.getGenomeFileNamesFromFolder()
        } else {
            listOf(genomeName)
        }
    }

    /**
     * Строит или загружает мир и запускает поток симуляции.
     *
     * @return false — сохранённый мир не загрузился и построен новый; игроку надо
     *   показать ошибку.
     */
    fun startWorld(): Boolean {
        // Чистый лист под любой мир. Прошлая сессия ставит isFinish, но её поток обычно
        // останавливается раньше, чем доходит до тика, который бы его обработал. Тогда
        // первый же тик ЭТОЙ сессии вызвал бы dispose() и стёр только что построенный —
        // или только что загруженный — мир.
        simulationSystem.dispose()
        val loaded = savedWorld != null && loadSavedWorld(savedWorld)
        if (!loaded) {
            DISimulationContainer.worldTerrainManager.map = worldSpec?.buildMap()
            simulationSystem.initMap()
        }

        // Поток симуляции стартует ТОЛЬКО после того, как мир создан.
        //
        // Раньше startThread() стоял раньше, а initMap() — после, и оба вызова идут с
        // GL-потока. То есть initWorld создавал десятки тысяч частиц, пока поток
        // симуляции уже крутил тики: addParticle мутирует aliveList/lastId/deadStack
        // сущности и pending/pendingCount в GridManager, а тик в это же время читает их
        // и в конце rebuild делает pendingCount = 0.
        //
        // Частица, зарегистрированная между захватом pendingSize в rebuild и обнулением
        // счётчика, теряется безвозвратно: в particleIdx она не попала, а из pending её
        // стёрло. Дальше она жива, имеет корректный gridId, рисуется из aliveList — но
        // в сетке её нет, поэтому по ней не считаются столкновения и её не берёт курсор.
        //
        // На экране это горизонтальные полосы, потому что initWorld заполняет карту
        // построчно, а rebuild случается раз в тик: каждый тик терялась очередная пачка
        // подряд идущих строк. Шаг полос — сколько строк GL-поток успевал создать за один
        // тик, и он лишь по случайности похож на высоту чанка.
        //
        // На десктопе заполнение карты укладывалось в пару тиков, поэтому эффект был почти
        // невидим; на Android те же десятки тысяч addParticle идут в разы дольше, и окно
        // гонки растягивалось на десятки тиков.
        simulationSystem.startThread()
        return savedWorld == null || loaded
    }

    private fun loadSavedWorld(slot: String): Boolean = try {
        worldSpec = DISimulationContainer.mapSave.load(slot)
        // Рельеф не строится заново — он уже в загруженных частицах. Карта нужна
        // только «Перезапуску».
        DISimulationContainer.worldTerrainManager.map = worldSpec?.buildMap()
        true
    } catch (e: Exception) {
        Gdx.app.error("MapSave", "не удалось загрузить мир $slot", e)
        // Битый файл не перезаписываем: следующее сохранение уйдёт в новый слот.
        saveSlot = null
        false
    }

    /**
     * Превью снимается в следующем кадре (см. RenderSystem.requestPreview), поэтому и само
     * сохранение идёт оттуда же: в одном кадре с превью, чтобы оно показывало ровно
     * сохранённый мир.
     */
    private fun saveWorld() {
        renderSystem.requestPreview(MapSave.PREVIEW_SIZE) { preview ->
            val saved = try {
                saveSlot = DISimulationContainer.mapSave.save(saveSlot, worldSpec, preview)
                true
            } catch (e: Exception) {
                Gdx.app.error("MapSave", "не удалось сохранить мир", e)
                false
            }
            onEffect?.invoke(SimulationEffect.WorldSaved(saved))
        }
    }

    private companion object {
        const val LOG_SOURCE = "Simulation"
    }
}
