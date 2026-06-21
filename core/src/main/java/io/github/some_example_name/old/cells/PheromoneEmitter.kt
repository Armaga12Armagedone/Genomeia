package io.github.some_example_name.old.cells

import io.github.some_example_name.old.commands.WorldCommandType
import io.github.some_example_name.old.core.CellSettings
import io.github.some_example_name.old.core.utils.blueColors

class PheromoneEmitter(cellTypeId: Int) : Cell(
    defaultColor = blueColors[3],
    cellTypeId = cellTypeId,
    textureName = "PheromoneEmitter.png",
    isNeural = true
) {

    override fun doOnTick(cellIndex: Int, threadId: Int) = with(cellEntity) {
        val lastImpulse = specialEntity.getPheromoneEmitterLastImpulse(cellIndex)
        val currentImpulse = neuronImpulseOutput[cellIndex]
        specialEntity.setPheromoneEmitterLastImpulse(cellIndex, currentImpulse)

        if (lastImpulse <= 0 && currentImpulse > 0) {
            worldCommandsManager.worldCommandBuffer[threadId].push(
                type = WorldCommandType.ADD_PHEROMONE,
                ints = intArrayOf(getParticleIndex(cellIndex), cellEntity.pheromoneType[cellIndex]),
                floats = floatArrayOf(getX(cellIndex), getY(cellIndex))
            )
        }

        if (currentImpulse > 0) {
            setIsPheromoneEmitter(cellIndex, true)
        } else {
            setIsPheromoneEmitter(cellIndex, false)
        }
    }
}
