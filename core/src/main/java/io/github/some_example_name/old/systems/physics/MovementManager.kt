package io.github.some_example_name.old.systems.physics

import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.core.DIGameGlobalContainer.simMaxSpeed
import io.github.some_example_name.old.core.DISimulationContainer.HALF_CHUNK_HEIGHT
import io.github.some_example_name.old.core.SubstrateSettings
import io.github.some_example_name.old.core.utils.invSqrt
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.SubstancesEntity
import io.github.some_example_name.old.systems.pheromone.PheromonesManager
import io.github.some_example_name.old.systems.simulation.SimulationData

class MovementManager(
    val entity: ParticleEntity,
    val gridManager: GridManager,
    val substrateSettings: SubstrateSettings,
    val worldCommandsManager: WorldCommandsManager,
    val simulationData: SimulationData,
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val cellList: List<Cell>,
    val substancesEntity: SubstancesEntity,
    val pheromonesManager: PheromonesManager
) {

    fun moveParticle(particleIndex: Int, threadId: Int = 0) = with(entity) {
        val oldX = x[particleIndex].toInt()
        val oldY = y[particleIndex].toInt()
        val gridCellIndex = gridId[particleIndex]
        vy[particleIndex] -= 0

        processCellFrictionOld(particleIndex)

        seedBump(particleIndex)

        x[particleIndex] += vx[particleIndex] * 7.5f
        y[particleIndex] += vy[particleIndex] * 7.5f

        processWorldBorders(particleIndex)
        val x = x[particleIndex]
        val y = y[particleIndex]

        val newX = x.toInt()
        val newY = y.toInt()
        if (newX != oldX || newY != oldY) {
            if (isPheromoneEmitter[particleIndex]) {
                pheromonesManager.newGridCell(x, y, particleIndex, threadId)
            }
            gridManager.removeParticle(gridCellIndex, particleIndex)
            gridId[particleIndex] = gridManager.addParticle(newX, newY, particleIndex)
        }
    }

    private fun seedBump(particleIndex: Int) = with(entity) {
        val vxv = vx[particleIndex]
        val vyv = vy[particleIndex]

        val speed2 = vxv * vxv + vyv * vyv
        if (speed2 > simMaxSpeed) {
            val invLen = HALF_CHUNK_HEIGHT * invSqrt(speed2)
            vx[particleIndex] *= invLen
            vy[particleIndex] *= invLen
        }
    }

    private fun processWorldBorders(cellId: Int) = with(entity) {
        if (x[cellId] < radius[cellId]) {
            x[cellId] = radius[cellId]
            vx[cellId] *= -0.8f
        } else if (x[cellId] > gridManager.gridWidth - radius[cellId]) {
            x[cellId] = gridManager.gridWidth - radius[cellId]
            vx[cellId] *= -0.8f
        }

        if (y[cellId] < radius[cellId]) {
            y[cellId] = radius[cellId]
            vy[cellId] *= -0.8f
        } else if (y[cellId] > gridManager.gridHeight - radius[cellId]) {
            y[cellId] = gridManager.gridHeight - radius[cellId]
            vy[cellId] *= -0.8f
        }
    }

    private fun processCellFrictionOld(particleIndex: Int) = with(entity) {
        if (isCell[particleIndex]) {
            val cellIndex = holderEntityIndex[particleIndex]
            val linkAmount = cellEntity.linkAmount[cellIndex]
            val x = linkAmount * 0.25f
            val x2 = x * x
            val x4 = x2 * x2

            val friction = (1f / (x4 + 1f)) * dragCoefficient[particleIndex]
            vx[particleIndex] *= 1f - friction
            vy[particleIndex] *= 1f - friction
        } else {
            vx[particleIndex] *= 1f - dragCoefficient[particleIndex]
            vy[particleIndex] *= 1f - dragCoefficient[particleIndex]
        }
    }
}
