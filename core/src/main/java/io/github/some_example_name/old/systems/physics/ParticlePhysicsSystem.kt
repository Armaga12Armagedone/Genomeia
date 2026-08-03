package io.github.some_example_name.old.systems.physics

import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.core.SubstrateSettings
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.SubstancesEntity
import io.github.some_example_name.old.systems.pheromone.PheromonesManager
import io.github.some_example_name.old.systems.simulation.SimulationData

class ParticlePhysicsSystem(
    val entity: ParticleEntity,
    val gridManager: GridManager,
    val substrateSettings: SubstrateSettings,
    val worldCommandsManager: WorldCommandsManager,
    val simulationData: SimulationData,
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val cellList: List<Cell>,
    val substancesEntity: SubstancesEntity,
    val pheromonesManager: PheromonesManager,
    val collisionManager: CollisionManager
) {

    /**
     * Многопоточная фаза: обрабатывает клетки сетки [start, end) и складывает индексы
     * частиц в стек чанка для последующей фазы движения.
     */
    fun processGridChunkPhysics(start: Int, end: Int, threadId: Int, isOdd: Boolean) {
        processGridRangePhysics(start, end, threadId, isOdd, distributeIndices = true)
    }

    /**
     * Ядро широкой фазы.
     *
     * Ключевые моменты по производительности:
     *  - НЕТ аллокаций: частицы клетки читаются прямо из gridManager.grid по слотам,
     *    без copyOfRange (раньше это было ~150k аллокаций массивов за тик).
     *  - НЕТ целочисленного деления на клетку: x/y ведутся инкрементально
     *    (раньше было i % gridWidth и i / gridWidth = 2 idiv на каждую из 16k клеток).
     *  - Ссылки на массивы подняты в локальные переменные: поля grid/particleCounts
     *    объявлены как var, поэтому JIT обязан перечитывать их после каждой записи в память.
     *
     * Прямой обход сетки безопасен, потому что в этой фазе сетка не мутирует:
     * repulse и onContact пишут только в vx/vy/energy/radius и в отложенные команды.
     */
    fun processGridRangePhysics(
        start: Int,
        end: Int,
        threadId: Int,
        isOdd: Boolean,
        distributeIndices: Boolean
    ) {
        val grid = gridManager.grid
        val counts = gridManager.particleCounts
        val maxPerCell = gridManager.maxAmountOfParticles
        val width = gridManager.gridWidth

        var x = start % width
        var y = start / width

        for (cellIndex in start until end) {
            val count = counts[cellIndex]

            if (count > 0) {
                if (count <= maxPerCell) {
                    val base = cellIndex * maxPerCell

                    // Пары внутри одной клетки сетки: каждая пара ровно один раз (i < j).
                    for (i in 0 until count) {
                        val particleA = grid[base + i]
                        for (j in i + 1 until count) {
                            collisionManager.repulse(particleA, grid[base + j], threadId)
                        }
                    }

                    // Соседние клетки + раскладка по чанкам.
                    for (i in 0 until count) {
                        val particleIndex = grid[base + i]
                        processNeighborsCellsCollision(particleIndex, x, y, threadId)
                        if (distributeIndices) {
                            distributeParticleIndicesAcrossChunks(particleIndex, threadId, isOdd)
                        }
                    }
                } else {
                    // Редкий путь: клетка переполнена, часть частиц лежит в списке-хвосте.
                    processOverflowedGridCell(cellIndex, x, y, threadId, isOdd, distributeIndices)
                }
            }

            x++
            if (x == width) {
                x = 0
                y++
            }
        }
    }

    /**
     * Холодный путь для переполненных клеток сетки (count > maxAmountOfParticles).
     * Вынесен отдельно, чтобы не раздувать горячий цикл и не мешать его инлайнингу.
     */
    private fun processOverflowedGridCell(
        cellIndex: Int,
        gridX: Int,
        gridY: Int,
        threadId: Int,
        isOdd: Boolean,
        distributeIndices: Boolean
    ) {
        val count = gridManager.particleCounts[cellIndex]
        val maxPerCell = gridManager.maxAmountOfParticles
        val base = cellIndex * maxPerCell
        val grid = gridManager.grid

        val extraList = gridManager.overflowListOrNull(cellIndex)
            ?: throw IllegalStateException("Overflow list is null but particleCounts > maxAmountOfParticles")
        val extra = extraList.elements()
        val extraSize = extraList.size

        for (i in 0 until count) {
            val particleA = if (i < extraSize) extra[i] else grid[base + i - extraSize]
            for (j in i + 1 until count) {
                val particleB = if (j < extraSize) extra[j] else grid[base + j - extraSize]
                collisionManager.repulse(particleA, particleB, threadId)
            }
        }

        for (i in 0 until count) {
            val particleIndex = if (i < extraSize) extra[i] else grid[base + i - extraSize]
            processNeighborsCellsCollision(particleIndex, gridX, gridY, threadId)
            if (distributeIndices) {
                distributeParticleIndicesAcrossChunks(particleIndex, threadId, isOdd)
            }
        }
    }

    private fun distributeParticleIndicesAcrossChunks(
        cellIndex: Int,
        threadId: Int,
        isOdd: Boolean
    ) {
        val stacks = if (isOdd) worldCommandsManager.oddCellChunkPositionStack
        else worldCommandsManager.evenCellChunkPositionStack
        val counters = if (isOdd) worldCommandsManager.oddCellCounter
        else worldCommandsManager.evenCellCounter

        val index = counters[threadId]
        var arr = stacks[threadId]

        if (index >= arr.size) {
            arr = arr.copyOf(arr.size + (arr.size shr 1))
            stacks[threadId] = arr
        }

        arr[index] = cellIndex
        counters[threadId] = index + 1
    }

    /**
     * Полуобход соседей: три клетки сверху (x-1, x, x+1) и одна справа.
     * Так каждая пара соседних клеток обрабатывается ровно один раз.
     *
     * Верхний ряд идёт одним отрезком — индексы клеток там последовательные,
     * это дешевле трёх независимых обращений и дружелюбнее к префетчеру.
     */
    fun processNeighborsCellsCollision(cellId: Int, gridX: Int, gridY: Int, threadId: Int = 0) {
        gridManager.forEachParticleInRowSegment(gridY + 1, gridX - 1, gridX + 1) { id ->
            collisionManager.repulse(cellId, id, threadId)
        }
        gridManager.forEachParticleAt(gridX + 1, gridY) { id ->
            collisionManager.repulse(cellId, id, threadId)
        }
    }

    /**
     * Оставлено для совместимости: попарный перебор по готовому массиву индексов.
     * В горячем пути не используется — там работает processGridRangePhysics.
     */
    fun processCollisionsInTheSameCell(cells: IntArray, threadId: Int = 0) {
        processCollisionsInTheSameCell(cells, cells.size, threadId)
    }

    fun processCollisionsInTheSameCell(cells: IntArray, count: Int, threadId: Int = 0) {
        for (i in 0 until count) {
            val particleA = cells[i]
            for (j in i + 1 until count) {
                collisionManager.repulse(particleA, cells[j], threadId)
            }
        }
    }
}
