package io.github.some_example_name.old.commands

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.utils.Disposable
import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.cells.Zygote
import io.github.some_example_name.old.core.DISimulationContainer.worldCommandsManager
import io.github.some_example_name.old.core.log.ActionLog
import io.github.some_example_name.old.core.utils.collectParticles
import io.github.some_example_name.old.core.utils.distanceTo
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.OrganEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.systems.simulation.SimulationData
import io.github.some_example_name.old.systems.genomics.genome.GenomeManager
import io.github.some_example_name.old.systems.physics.CollisionManager.Companion.PARTICLE_MAX_RADIUS
import io.github.some_example_name.old.systems.physics.GridManager
import io.github.some_example_name.old.systems.debug.LogReplay
import io.github.some_example_name.old.systems.debug.LogSaver
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class UserCommandManager(
    val organEntity: OrganEntity,
    val cellEntity: CellEntity,
    val genomeManager: GenomeManager,
    val cellList: List<Cell>,
    val simulationData: SimulationData,
    val gridManager: GridManager,
    val particleEntity: ParticleEntity,
    val zygote: Zygote,
    val isEditor: Boolean,
    /** Запись и воспроизведение сессии. Есть только у основной симуляции, не у редактора и меню. */
    val logSaver: LogSaver? = null,
    val logReplay: LogReplay? = null
): Disposable {

    private val commandQueue = ConcurrentLinkedQueue<PlayerCommand>()

    @Volatile var grabbedParticleIndex = -1
    @Volatile var tapX = 0f
    @Volatile var tapY = 0f
    @Volatile var isDragging = false

    /**
     * Весь случайный выбор при обработке команд игрока — только отсюда. Seed задаётся
     * на каждую сессию ([startSession]) и пишется в запись, поэтому при воспроизведении
     * распыление вещества ложится в те же точки.
     */
    private val random = RandomXS128()

    private var isAlreadyDragged = false

    /**
     * Поток симуляции, начало сессии (старт мира, рестарт, воспроизведение). Сбрасывает
     * всё, что команды тащат из тика в тик: иначе захват частицы, начатый до рестарта,
     * указывал бы в новом мире на чужую частицу.
     */
    fun startSession(seed: Long) {
        random.setSeed(seed)
        commandQueue.clear()
        grabbedParticleIndex = -1
        isDragging = false
    }

    fun push(cmd: PlayerCommand) {
        // Единственный вход для действий игрока в мире — значит и единственное место,
        // где их надо журналировать. Drag сыпется на каждое движение мыши, но подряд
        // идущие однотипные записи ActionLog схлопывает в одну со счётчиком.
        ActionLog.record(LOG_SOURCE, cmd.name, cmd.detail)
        commandQueue.offer(cmd)
    }

    fun processingCommandsFromUser() {
        isAlreadyDragged = false
        val tick = simulationData.tickCounter

        if (logReplay != null && logReplay.isActive) {
            // Во время воспроизведения команды идут только из записи: живой ввод
            // перемешался бы с записанным и увёл сценарий в сторону.
            commandQueue.clear()
            logReplay.forEachCommandAt(tick) { applyCommand(it) }
        } else {
            while (true) {
                val cmd = commandQueue.poll() ?: break
                // Пишем в момент применения и с этим тиком: ровно здесь же команда
                // будет применена и при воспроизведении.
                logSaver?.recordCommand(tick, cmd, resolveGenomeIndex(cmd))
                applyCommand(cmd)
            }
        }

        if (grabbedParticleIndex != -1 && !isAlreadyDragged && isDragging) {
            val grabDrag = 0.5f // To reduce oscillations

            with(particleEntity) {
                vx[grabbedParticleIndex] = vx[grabbedParticleIndex] * grabDrag + (tapX - x[grabbedParticleIndex]) * 0.02f
                vy[grabbedParticleIndex] = vy[grabbedParticleIndex] * grabDrag + (tapY - y[grabbedParticleIndex]) * 0.02f
            }
        }
    }

    private fun resolveGenomeIndex(cmd: PlayerCommand): Int =
        (cmd as? PlayerCommand.Tap)?.genomeIndex ?: simulationData.currentGenomeIndex

    private fun applyCommand(cmd: PlayerCommand) {
        when (cmd) {
            PlayerCommand.StopDrag -> {
                grabbedParticleIndex = -1
                simulationData.selectedCellIndex = -1
                isDragging = false
            }

            is PlayerCommand.TouchDown -> {
                val neighborsCellIndexes =
                    gridManager.collectParticles(cmd.x.toInt(), cmd.y.toInt(), radius = 1)
                grabbedParticleIndex = neighborsCellIndexes
                    .minByOrNull {
                        distanceTo(cmd.x, cmd.y, particleEntity.x[it], particleEntity.y[it])
                    }?.takeIf {
                        isDragging = false
                        distanceTo(
                            cmd.x,
                            cmd.y,
                            particleEntity.x[it],
                            particleEntity.y[it]
                        ) < particleEntity.radius[it]
                    } ?: -1

                tapX = cmd.x
                tapY = cmd.y
                simulationData.selectedCellIndex = -1
            }

            is PlayerCommand.Drag -> {
                if (grabbedParticleIndex != -1) {
                    isAlreadyDragged = true
                    val grabDrag = 0.5f // To reduce oscillations

                    with(particleEntity) {
                        vx[grabbedParticleIndex] =
                            vx[grabbedParticleIndex] * grabDrag + (cmd.x - x[grabbedParticleIndex]) * 0.02f
                        vy[grabbedParticleIndex] =
                            vy[grabbedParticleIndex] * grabDrag + (cmd.y - y[grabbedParticleIndex]) * 0.02f
                    }
                    isDragging = true

                    tapX = cmd.x
                    tapY = cmd.y
                    simulationData.selectedCellIndex = -1
                }
            }

            is PlayerCommand.Tap -> {
                val neighborsCellIndexes =
                    gridManager.collectParticles(cmd.x.toInt(), cmd.y.toInt(), radius = 1)
                val grabbedParticleIndexTap = neighborsCellIndexes
                    .minByOrNull {
                        distanceTo(cmd.x, cmd.y, particleEntity.x[it], particleEntity.y[it])
                    }?.takeIf {
                        distanceTo(
                            cmd.x,
                            cmd.y,
                            particleEntity.x[it],
                            particleEntity.y[it]
                        ) < particleEntity.radius[it]
                    } ?: -1

                simulationData.selectedCellIndex = if (grabbedParticleIndexTap != -1) {
                    if (particleEntity.isCell[grabbedParticleIndexTap]) {
                        isDragging = false
                        particleEntity.holderEntityIndex[grabbedParticleIndexTap]
                    } else -1
                } else -1

                if (simulationData.selectedCellIndex == -1) {
                    if (cmd.isLeftButton) {
                        if (cmd.x > 0 && cmd.x < gridManager.gridWidth && cmd.y > 0 && cmd.y < gridManager.gridHeight) {
                            val genomeIndex = resolveGenomeIndex(cmd)
                            val genome = genomeManager.genomes[genomeIndex]
                            val organIndex = organEntity.addOrgan(
                                genomeIndex = genomeIndex,
                                genomeSize = genome.genomeStageInstruction.size,
                                dividedTimes = genome.dividedTimes[0],
                                mutatedTimes = genome.mutatedTimes[0]
                            )
                            // Строго до первой addCell: зигота уже должна брать слот
                            // из арены, иначе тело начнётся вне своего диапазона.
                            //
                            // Размеры берутся из генома, снятые при запекании, а не из
                            // констант: константа на все геномы сразу — это либо
                            // перерасход на мелких телах, либо падение на крупных.
                            // У незапечённого генома там нули, и тогда работают
                            // прежние значения по умолчанию.
                            if (genome.cellsAmount > 0) {
                                organEntity.allocateArenas(
                                    organIndex = organIndex,
                                    layout = genome.sortedGraph,
                                    maxCells = genome.cellsAmount,
                                    maxLinks = genome.linksAmount
                                )
                            } else {
                                organEntity.allocateArenas(
                                    organIndex = organIndex,
                                    layout = genome.sortedGraph,
                                    maxCells = genome.cellsAmount,
                                    maxLinks = genome.linksAmount
                                )
//                                    throw Exception("Пустой геном без клеток")
                            }
                            val randomAngle = if (isEditor) 0f else 0f//MathUtils.random(0f, MathUtils.PI2)
                            cellEntity.addCell(
                                x = cmd.x,
                                y = cmd.y,
                                color = zygote.defaultColor.toIntBits(),
                                radius = PARTICLE_MAX_RADIUS,
                                cellType = zygote.cellTypeId,
                                organIndex = organIndex,
                                angleCos = cos(randomAngle),
                                angleSin = sin(randomAngle)
                            )
                        }
                    } else {
                        val radius = 7.0f
                        repeat(10) {
                            val angle = random.nextFloat() * MathUtils.PI2

                            val r = radius * sqrt(random.nextFloat())

                            val x = cmd.x + MathUtils.cos(angle) * r
                            val y = cmd.y + MathUtils.sin(angle) * r

                            if (x > 0 && x < gridManager.gridWidth && y > 0 && y < gridManager.gridHeight) {

                                val neighborsCellIndexes = gridManager.collectParticles(
                                    x.toInt(),
                                    y.toInt(),
                                    radius = 1
                                )
                                val neighborIndex = neighborsCellIndexes.firstOrNull {
                                    distanceTo(
                                        x,
                                        y,
                                        particleEntity.x[it],
                                        particleEntity.y[it]
                                    ) < particleEntity.radius[it]
                                }

                                if (neighborIndex == null) {
                                    val color = Color.RED.toIntBits()
                                    val radius = random.nextFloat() * (0.5f - 0.1f) + 0.1f

                                    // Буфер фиксированный, а не случайный: число буферов
                                    // равно числу потоков и на другой машине другое.
                                    // Фаза команд игрока однопоточная, гонки тут нет.
                                    worldCommandsManager.worldCommandBuffer[0].push(
                                        type = WorldCommandType.ADD_SUBSTANCE,
                                        floats = floatArrayOf(x, y, radius),
                                        ints = intArrayOf(color, 0)
                                    )
                                }
                            }
                        }
                    }
                }

//                    println("Tap (${cmd.x}, ${cmd.y} ${cmd.isLeftButton} ${grabbedParticleIndex})")
            }
        }
    }

    private companion object {
        const val LOG_SOURCE = "Player"
    }

    override fun dispose() {
        commandQueue.clear()
    }
}
