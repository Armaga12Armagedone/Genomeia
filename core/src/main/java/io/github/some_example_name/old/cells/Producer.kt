package io.github.some_example_name.old.cells

import io.github.some_example_name.old.commands.WorldCommandType
import io.github.some_example_name.old.core.DISimulationContainer.zygote
import io.github.some_example_name.old.core.utils.redColors
import io.github.some_example_name.old.systems.physics.CollisionManager.Companion.PARTICLE_MAX_RADIUS

class Producer(cellTypeId: Int): Cell(
    defaultColor = redColors[4],
    cellTypeId = cellTypeId,
    textureName = "Producer.png",
    isDirected = true,
    isNeural = true
) {

    override fun doOnTick(cellIndex: Int, threadId: Int) = with(cellEntity) {
        val impulse = neuronImpulseOutput[cellIndex]

        neuronImpulseOutput[cellIndex] = 0f
        if(energy[cellIndex] < substrateSettings.cellsSettings[cellType[cellIndex].toInt()].maxEnergy || impulse < 1) return

        //TODO Make the impulse increase smoothly from 0 to 1, and have the producer divide at the moment when the impulse equals 1
        val organIndex = organIndex[cellIndex]
        val time = specialEntity.getReproductionRestriction(cellIndex)

        if (time <= 0) {
            val genomeIndex = organEntity.genomeIndex[organIndex] // TODO сделать выбор sub-genome
            val genome = genomeManager.genomes[genomeIndex]
            var counter = 0
            genome.stageInstruction.forEach {
                counter += it.cellActions.size
            }
            specialEntity.setReproductionRestriction(cellIndex, counter * substrateSettings.data.producerRestoreTimeTickCoefficient.toInt())
        } else {
            specialEntity.setReproductionRestriction(cellIndex, time - 1)
        }

        if (specialEntity.getReproductionRestriction(cellIndex) != 1) return

        val color: Int = zygote.defaultColor.toIntBits()
        val radius: Float = PARTICLE_MAX_RADIUS
        val cellType: Int = zygote.cellTypeId
        val parentIndex: Int = -1
        val angleDiffCosDefault: Float = 1f
        val angleDiffSinDefault: Float = 0f
        val colorDifferentiation: Int = 7
        val visibilityRange: Float = 4.25f
        val a: Float = 1f
        val b: Float = 0f
        val c: Float = 0f
        val isSum: Boolean = true
        val activationFuncType: Int = 0
        val finalCos = angleCos[cellIndex]
        val finalSin = angleSin[cellIndex]

        val x = getX(cellIndex) + finalCos * 0.05f
        val y = getY(cellIndex) + finalSin * 0.05f

        val isMorphogenesis = false
        val pheromoneType = -1

        worldCommandsManager.worldCommandBuffer[threadId].push(
            type = WorldCommandType.ADD_CELL,
            booleans = booleanArrayOf(isSum, isMorphogenesis),
            floats = floatArrayOf(x, y, radius, finalCos, finalSin, angleDiffCosDefault, angleDiffSinDefault, visibilityRange, a, b, c),
            ints = intArrayOf(
                color,
                0,          //cellGenomeId
                cellType,
                organIndex,
                parentIndex,
                colorDifferentiation,
                activationFuncType,
                pheromoneType,
                -1, //mod data
                // Родителя нет (parentIndex = -1), поэтому и поколения нет —
                // обработчик ADD_CELL в этом случае проверку пропускает.
                -1
            )
        )

        neuronImpulseOutput[cellIndex] = 1f
        energy[cellIndex] -= energy[cellIndex]
    }
}
