package io.github.some_example_name.old.systems.genomics

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.commands.WorldCommandType
import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.core.utils.collectParticles
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.systems.physics.GridManager
import io.github.some_example_name.old.systems.physics.ParticlePhysicsSystem.Companion.PARTICLE_MAX_RADIUS
import kotlin.math.cos
import kotlin.math.sin

class DivideManager(
    val cellEntity: CellEntity,
    val particleEntity: ParticleEntity,
    val worldCommandsManager: WorldCommandsManager,
    val gridManager: GridManager,
    val cellList: List<Cell>
) {

    fun divideCell(index: Int, threadId: Int) = with(cellEntity) {
        if (!isDividedInThisStage[index] && energy[index] >= energyNecessaryToDivide[index]) {
            isDividedInThisStage[index] = true

            val action = cellActions[index]?.divide ?: return

            val parentLinkLength = action.physicalLink[cellGenomeId[index]]?.length ?: 0.025f
            val genomeAngle = action.angle ?: throw Exception("Forgot angle")
            val divideAngleCos = cos(genomeAngle)
            val divideAngleSin = sin(genomeAngle)
            //angle + genomeAngle
            val isDirected = cellList[cellType[index].toInt()].isDirected
            //TODO Для компенсации угла у клеток с parentIndex == -1 не стоит использовать angleDiff, это условие временное, что бы было корректное деление из зиготы
            val parentAngleCos = if (isDirected) { angleCos[index] * angleDiffCos[index] + angleSin[index] * angleDiffSin[index] } else angleCos[index]
            val parentAngleSin = if (isDirected) { angleSin[index] * angleDiffCos[index] - angleCos[index] * angleDiffSin[index] } else angleSin[index]
            val finalCos = parentAngleCos * divideAngleCos - parentAngleSin * divideAngleSin
            val finalSin = parentAngleSin * divideAngleCos + parentAngleCos * divideAngleSin
            var x = getX(index) + finalCos * parentLinkLength
            var y = getY(index) + finalSin * parentLinkLength

            if (x < 0) {
                x = 0.1f
            }
            if (x > gridManager.gridWidth) {
                x = gridManager.gridWidth - 0.1f
            }
            if (y < 0) {
                y = 0.1f
            }
            if (y > gridManager.gridHeight) {
                y = gridManager.gridHeight - 0.1f
            }

            val cellGenomeId: Int = action.id
            val parentOrganIndex: Int = organIndex[index]
            run {
                val color: Int = (action.color ?: Color.WHITE).toIntBits()
                val radius: Float = PARTICLE_MAX_RADIUS
                val cellType: Int = action.cellType ?: throw Exception("Forgot cellType")
                val parentIndex: Int = index
                val angleDiff: Float = action.angleDirected ?: 0f
                val angleDiffCos: Float = cos(angleDiff)
                val angleDiffSin: Float = sin(angleDiff)
                val angleCos: Float = finalCos * angleDiffCos - finalSin * angleDiffSin
                val angleSin: Float = finalSin * angleDiffCos + finalCos * angleDiffSin

                val colorDifferentiation: Int = action.colorRecognition ?: 7
                val visibilityRange: Float = action.lengthDirected ?: 4.25f
                val a: Float = action.a ?: 1f
                val b: Float = action.b ?: 0f
                val c: Float = action.c ?: 0f
                val isSum: Boolean = action.isSum ?: true
                val activationFuncType: Int = action.funActivation ?: 0

                worldCommandsManager.worldCommandBuffer[threadId].push(
                    type = WorldCommandType.ADD_CELL,
                    booleans = booleanArrayOf(isSum),
                    floats = floatArrayOf(x, y, radius, angleCos, angleSin, angleDiffCos, angleDiffSin, visibilityRange, a, b, c),
                    ints = intArrayOf(
                        color,
                        cellGenomeId,
                        cellType,
                        parentOrganIndex,
                        parentIndex,
                        colorDifferentiation,
                        activationFuncType
                    )
                )

                worldCommandsManager.worldCommandBuffer[threadId].push(
                    type = WorldCommandType.DECREMENT_DIVIDE_COUNTER,
                    ints = intArrayOf(parentOrganIndex)
                )
            }

            if (action.physicalLink.isNotEmpty()) {
                val gridX = x.toInt()
                val gridY = y.toInt()
                val closestCells = gridManager.collectParticles(gridX, gridY)
                val idToIndexAssociation = closestCells
                        .filter { particleEntity.isCell[it] }
                        .map { particleEntity.holderEntityIndex[it] }
                        .filter { organIndex[it] == organIndex[index]}
                        .associateBy { this.cellGenomeId[it] }

                action.physicalLink.forEach { (cellGenomeIdToConnectWith, linkData) ->
                    val otherCellIndex = idToIndexAssociation[cellGenomeIdToConnectWith]
                    if (linkData != null && linkData.length != null) {

                        val cellIndex: Int = -1
                        val linksLength: Float = linkData.length
                        val degreeOfShortening: Float = 1f
                        val isStickyLink: Boolean = false
                        val isNeuronLink: Boolean = linkData.isNeuronal
                        val isLink1NeuralDirected: Boolean = linkData.directedNeuronLink == action.id

                        if (otherCellIndex != null) {
                            if (linkData.isNeuronal && linkData.directedNeuronLink != action.id
                                && linkData.directedNeuronLink != cellGenomeIdToConnectWith
                            ) {
                                throw Exception("Incorrect logic in the neural-link")
                            }

                            worldCommandsManager.worldCommandBuffer[threadId].push(
                                type = WorldCommandType.ADD_LINK,
                                booleans = booleanArrayOf(
                                    isStickyLink,
                                    isNeuronLink,
                                    isLink1NeuralDirected
                                ),
                                floats = floatArrayOf(linksLength, degreeOfShortening),
                                ints = intArrayOf(cellIndex, otherCellIndex)
                            )
                        } else {
                            val cellId: Int = cellGenomeId
                            val otherCellId: Int = cellGenomeIdToConnectWith

                            worldCommandsManager.worldCommandSecondBuffer[threadId].push(
                                type = WorldCommandType.ADD_LINK_BY_ID,
                                booleans = booleanArrayOf(isNeuronLink, isLink1NeuralDirected),
                                floats = floatArrayOf(linksLength),
                                ints = intArrayOf(cellId, otherCellId, parentOrganIndex)
                            )
                        }
                    }
                }
            }

            energy[index] -= energyNecessaryToDivide[index] - 0.7f
        }
    }

}
