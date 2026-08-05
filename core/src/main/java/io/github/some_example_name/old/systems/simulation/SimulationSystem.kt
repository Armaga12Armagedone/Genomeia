package io.github.some_example_name.old.systems.simulation

import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.commands.UserCommandManager
import io.github.some_example_name.old.commands.WorldCommandType
import io.github.some_example_name.old.core.DEBUG_CHECKS
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.core.DISimulationContainer.threadCount
import io.github.some_example_name.old.core.SubstrateSettings
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.Entity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.NeuralLinkEntity
import io.github.some_example_name.old.entities.OrganEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.PheromoneEntity
import io.github.some_example_name.old.systems.pheromone.PheromonesManager
import io.github.some_example_name.old.systems.genomics.CellSystem
import io.github.some_example_name.old.systems.genomics.OrganManager
import io.github.some_example_name.old.systems.genomics.genome.GenomeManager
import io.github.some_example_name.old.systems.physics.GridManager
import io.github.some_example_name.old.systems.physics.LinkPhysicsSystem
import io.github.some_example_name.old.systems.physics.ParticlePhysicsSystem
import io.github.some_example_name.old.systems.render.RenderBufferManager
import io.github.some_example_name.old.features.worldeditor.WorldTerrainManager
import io.github.some_example_name.old.systems.genomics.NeuralLinkManager
import io.github.some_example_name.old.systems.physics.MovementManager

class SimulationSystem(
    val gridManager: GridManager,
    val worldCommandsManager: WorldCommandsManager,
    val organManager: OrganManager,
    val organEntity: OrganEntity,
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val neuralLinkEntity: NeuralLinkEntity,
    val neuralLinkManager: NeuralLinkManager,
    val particleEntity: ParticleEntity,
    val pheromoneEntity: PheromoneEntity,
    val substrateSettings: SubstrateSettings,
    val threadManager: ThreadManager,
    val genomeManager: GenomeManager,
    val particlePhysicsSystem: ParticlePhysicsSystem,
    val linkPhysicsSystem: LinkPhysicsSystem,
    val simulationData: SimulationData,
    val cellSystem: CellSystem,
    val userCommandManager: UserCommandManager,
    val entityList: List<Entity>,
    val renderBufferManager: RenderBufferManager,
    val pheromonesManager: PheromonesManager,
    val movementManager: MovementManager,
    val worldTerrainManager: WorldTerrainManager
) {

    private var simulationThread: Thread? = null

    fun startThread() {
        if (!threadManager.isRunning) {
            threadManager.isRunning = true

            simulationThread = Thread { threadManager.runUpdateLoop { updateTick() } }.apply {
                isDaemon = true
                name = "Simulation-Main-Thread"
            }
            simulationThread?.start()
        }
    }

    // --- Performance Profiler ---
    //
    // Замер идёт всегда (два nanoTime на фазу — ~0.6 мкс на тик), вывод в CSV включается
    // флагом PROFILE_LOG. Накопители — обычные LongArray по номеру фазы: ни строк,
    // ни хэш-мапы, ни боксинга Double в цикле симуляции.
    private val profiler = PhaseProfiler()
    private val worldStats = WorldStats()

    /** Только для отладочного стресс-теста, см. applyDebugStress. */
    private val debugRandom = java.util.Random()

    fun updateTick() {
        val tickStart = System.nanoTime()

        if (simulationData.isFinish) {
            dispose()
            return
        }
        if (simulationData.isRestart) {
            restartSim()
            return
        }

        simulationData.tickCounter++
        simulationData.timeSimulation += DELTA_SIM_TICK_TIME

        if (DEBUG_CHECKS) applyDebugStress()

        // --- Измерения ---
        profiler.measure(Phase.LINKS) { linkPhysicsSystem.iterateLinksInParallel() }
        profiler.measure(Phase.NEURAL) { neuralLinkManager.iterate() }
        profiler.measure(Phase.COLLIDE) { processParticleCollision() }
        profiler.measure(Phase.CELLS) { cellSystem.iterateCellInParallel() }
        profiler.measure(Phase.PHEROMONE) { pheromonesManager.iterate() }
        profiler.measure(Phase.ARRANGE) { arrangementOfPositionsInTheGrid() }
        profiler.measure(Phase.WORLD_CMD) { worldCommandsManager.executingCommandsFromTheWorld() }
        profiler.measure(Phase.ORGANS) { organManager.performOrgansNextStage() }
        profiler.measure(Phase.USER_CMD) { userCommandManager.processingCommandsFromUser() }
        profiler.measure(Phase.LAST_WORLD_CMD) { worldCommandsManager.executingLastCommandsFromTheWorld() }
        // Единственное место, где меняется структура пространственной сетки. Стоит здесь,
        // потому что к этому моменту уже применены все перемещения (фаза 6) и все
        // создания/удаления частиц (фазы 7-10), то есть сетка собирается один раз по
        // финальному состоянию тика. Фазы 1-6 следующего тика читают её только на чтение,
        // поэтому многопоточным фазам не нужно ни блокировок, ни чётно-нечётных ограничений
        // из-за мутации сетки.
        profiler.measure(Phase.REBUILD) { gridManager.rebuild(particleEntity.isAlive, particleEntity.gridId) }

        profiler.measure(Phase.RENDER) { renderBufferManager.updateBuffer(profiler.text) }

        // Полный тик меряется отдельно, а не суммой фаз: разница между ним и суммой —
        // это неучтённое время (паузы GC, вытеснение потока планировщиком), и она сама
        // по себе диагностична.
        profiler.record(Phase.TICK, System.nanoTime() - tickStart)

        if (profiler.endTick()) {
            fillWorldStats()
            profiler.flush(worldStats)
        }
    }

    /**
     * Отладочный стресс-тест жизненного цикла клеток.
     *
     * Пока зажата A — несколько случайных клеток за тик умирают; пока зажата D — несколько
     * получают предельную скорость в случайном направлении и улетают, разрывая свои связи.
     *
     * Смысл в том, чтобы гонки и битые ссылки в связях воспроизводились сами: они всплывают
     * именно при активной смерти клеток и разрыве связей, а руками такой сценарий держать
     * долго и ненадёжно.
     *
     * Стоит в самом начале тика, до всех фаз: команда DELETE_CELL попадёт в буфер и будет
     * применена этим же тиком в фазе 7, а запись скорости идёт в однопоточном участке,
     * пока ни один воркер не запущен.
     */
    private fun applyDebugStress() {
        if (simulationData.debugKillCells) {
            repeat(DEBUG_STRESS_CELLS_PER_TICK) { killRandomCell() }
        }
        if (simulationData.debugFlingCells) {
            repeat(DEBUG_STRESS_CELLS_PER_TICK) { flingRandomCell() }
        }
    }

    private fun killRandomCell() {
        val alive = cellEntity.aliveList
        if (alive.isEmpty()) return

        val cellIndex = alive.getInt(debugRandom.nextInt(alive.size))
        // Через обычную команду, а не напрямую: удаление клетки тянет за собой снятие
        // связей, освобождение частицы и органа, и всё это должно пройти штатным путём.
        worldCommandsManager.worldCommandBuffer[0].push(
            WorldCommandType.DELETE_CELL,
            cellIndex,
            cellEntity.getGeneration(cellIndex)
        )
    }

    private fun flingRandomCell() {
        val alive = cellEntity.aliveList
        if (alive.isEmpty()) return

        val cellIndex = alive.getInt(debugRandom.nextInt(alive.size))
        val particleIndex = cellEntity.getParticleIndex(cellIndex)
        if (particleIndex == -1) return

        // Ровно предел скорости: больше ставить бессмысленно, seedBump всё равно зажмёт.
        // При MAX_SPEED смещение за тик равно HALF_CHUNK_HEIGHT, то есть связи длиной до
        // sqrt(linkMaxLength2) рвутся за пару тиков.
        val angle = debugRandom.nextFloat() * 2f * PI.toFloat()
        particleEntity.vx[particleIndex] = cos(angle) * MovementManager.MAX_SPEED
        particleEntity.vy[particleIndex] = sin(angle) * MovementManager.MAX_SPEED
    }

    /**
     * Снимок объёма мира на момент закрытия окна усреднения.
     *
     * Считается раз в PROFILE_WINDOW_TICKS тиков, поэтому дороговизна не важна — но всё
     * равно это только вычитания и сумма по 16 спискам.
     *
     * Количества живых сущностей берутся так же, как их считает оверлей: lastId минус
     * размер стека переиспользуемых индексов.
     */
    private fun fillWorldStats() {
        val stats = worldStats

        stats.tick = simulationData.tickCounter.toLong()
        stats.ups = simulationData.ups

        stats.cells = cellEntity.lastId - cellEntity.deadStack.size + 1
        stats.particles = particleEntity.lastId - particleEntity.deadStack.size + 1
        stats.links = linkEntity.lastId - linkEntity.deadStack.size + 1

        stats.gridSize = gridManager.gridSize
        stats.occupiedCells = gridManager.occupiedCells
        stats.maxParticlesInCell = gridManager.maxParticlesInCell

        stats.workers = threadManager.executor.workerCount

        stats.pairCandidatesTotal = SimCounters.take(SimCounters.PAIR_CANDIDATES)
        stats.collisionsTotal = SimCounters.take(SimCounters.COLLISIONS)
        stats.linkedSkipsTotal = SimCounters.take(SimCounters.LINKED_SKIPS)
        stats.contactsTotal = SimCounters.take(SimCounters.CONTACTS)
        stats.linkBreaksTotal = SimCounters.take(SimCounters.LINK_BREAKS)
        stats.linkAnglesTotal = SimCounters.take(SimCounters.LINK_ANGLES)

        // Занятость снимается по всем фазам разом: takeStage* обнуляет накопители,
        // поэтому пропущенная фаза копила бы данные до следующего окна и врала.
        val executor = threadManager.executor
        for (phase in 0 until Phase.COUNT) {
            stats.stageBusyNanos[phase] = executor.takeStageBusyNanos(phase)
            stats.stageWallNanos[phase] = executor.takeStageWallNanos(phase)
        }

        // Сколько связей реально обходит фаза 1. Это НЕ то же самое, что число живых
        // связей: в списки слотов попадают только те, что зарегистрированы через
        // registerNewLink, и по ним же идёт обход.
        var processed = 0
        for (list in worldCommandsManager.oddLinkLists) processed += list.size
        for (list in worldCommandsManager.evenLinkLists) processed += list.size
        stats.linksProcessed = processed
    }

    fun processParticleCollision() {
        threadManager.runChunkStage(isOdd = true, stageId = Phase.COLLIDE) { start, end, threadId ->
            particlePhysicsSystem.processGridChunkPhysics(start, end, threadId, isOdd = true)
        }
        threadManager.runChunkStage(isOdd = false, stageId = Phase.COLLIDE) { start, end, threadId ->
            particlePhysicsSystem.processGridChunkPhysics(start, end, threadId, isOdd = false)
        }
    }

    /**
     * Интегрирование позиций. Работы здесь мало (позиция += скорость, ограничение
     * по границам мира, пересчёт gridId), поэтому фаза почти целиком состояла из
     * накладных расходов: два барьера на ExecutorService по ~8 submit + 8 Future.get
     * каждый — это десятки микросекунд на десяток микросекунд полезной работы.
     * Теперь барьер спиновый, а слоты раздаются динамически: в стеке одного чанка
     * могут быть тысячи частиц, в соседнем — единицы.
     *
     * Две стадии (odd/even) пока сохранены, потому что буферы отложенных команд
     * индексируются номером чанка, и слот odd-чанка совпал бы со слотом even-чанка.
     * Само движение сетку больше не мутирует, так что после разведения буферов по
     * чанкам обе стадии можно будет слить в одну.
     */
    fun arrangementOfPositionsInTheGrid() {
        threadManager.runSlotStage(threadCount, Phase.ARRANGE) { chunk ->
            val stack = worldCommandsManager.oddCellChunkPositionStack[chunk]
            for (i in 0..<worldCommandsManager.oddCellCounter[chunk]) {
                movementManager.moveParticle(stack[i], chunk)
            }
        }

        threadManager.runSlotStage(threadCount, Phase.ARRANGE) { chunk ->
            val stack = worldCommandsManager.evenCellChunkPositionStack[chunk]
            for (i in 0..<worldCommandsManager.evenCellCounter[chunk]) {
                movementManager.moveParticle(stack[i], chunk)
            }
        }

        worldCommandsManager.oddCellCounter.fill(0)
        worldCommandsManager.evenCellCounter.fill(0)
    }

    fun stopUpdateThread() {
        threadManager.stopSimulationLoop()

        simulationThread?.let { thread ->
            thread.interrupt()
            try {
                thread.join(1000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    fun dispose() {
        gridManager.clearAll()
        entityList.forEach { it.clear() }
        simulationData.clear()
        worldCommandsManager.dispose()
    }

    private fun restartSim() {
        dispose()
        simulationData.isRestart = false
        worldTerrainManager.initWorld(
            gridWith = DISimulationContainer.gridWidth,
            gridHeight = DISimulationContainer.gridHeight
        )
    }

    fun initMap() {
        worldTerrainManager.initWorld(
            gridWith = DISimulationContainer.gridWidth,
            gridHeight = DISimulationContainer.gridHeight
        )
    }

    companion object {
        const val DELTA_SIM_TICK_TIME = 0.016666666f

        /** Сколько клеток за тик убивает / разгоняет отладочный стресс-тест. */
        private const val DEBUG_STRESS_CELLS_PER_TICK = 55
    }
}
