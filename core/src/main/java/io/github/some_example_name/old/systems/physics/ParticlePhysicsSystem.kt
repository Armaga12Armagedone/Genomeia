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

    fun processGridChunkPhysics(start: Int, end: Int, threadId: Int, isOdd: Boolean) {
        for (i in start until end) {
            val x = i % gridManager.gridWidth
            val y = i / gridManager.gridWidth

            if (gridManager.particleCounts[i] > 0) {
                val particles = gridManager.getParticlesIndex(i)
                processCollisionsInTheSameCell(particles, threadId)
                for (particleIndex in particles) {
                    processNeighborsCellsCollision(particleIndex, x, y, threadId)
                    distributeParticleIndicesAcrossChunks(particleIndex, threadId, isOdd)
                }
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

    fun processNeighborsCellsCollision(cellId: Int, gridX: Int, gridY: Int, threadId: Int = 0) {
        gridManager.getParticles(gridX - 1, gridY + 1).also { ids ->
            for (id in ids) collisionManager.repulse(cellId, id, threadId)
        }
        gridManager.getParticles(gridX, gridY + 1).also { ids ->
            for (id in ids) collisionManager.repulse(cellId, id, threadId)
        }
        gridManager.getParticles(gridX + 1, gridY + 1).also { ids ->
            for (id in ids) collisionManager.repulse(cellId, id, threadId)
        }

        gridManager.getParticles(gridX + 1, gridY).also { ids ->
            for (id in ids) collisionManager.repulse(cellId, id, threadId)
        }
    }

    fun processCollisionsInTheSameCell(cells: IntArray, threadId: Int = 0) {
        for (i in cells.indices) {
            for (j in i + 1 until cells.size) {
                collisionManager.repulse(cells[i], cells[j], threadId)
            }
        }
    }
}
