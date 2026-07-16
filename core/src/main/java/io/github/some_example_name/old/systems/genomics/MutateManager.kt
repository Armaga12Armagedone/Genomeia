package io.github.some_example_name.old.systems.genomics

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Disposable
import io.github.some_example_name.old.cells.Eye
import io.github.some_example_name.old.cells.Muscle
import io.github.some_example_name.old.cells.PheromoneEmitter
import io.github.some_example_name.old.cells.Producer
import io.github.some_example_name.old.cells.Tail
import io.github.some_example_name.old.cells.Zygote
import io.github.some_example_name.old.commands.WorldCommandType
import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.core.DISimulationContainer.cellsSettings
import io.github.some_example_name.old.core.utils.collectParticles
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.OrganEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.SpecialEntity
import io.github.some_example_name.old.systems.physics.GridManager
import kotlin.math.cos
import kotlin.math.sin

class MutateManager(
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val particleEntity: ParticleEntity,
    val worldCommandsManager: WorldCommandsManager,
    val gridManager: GridManager,
    val specialEntity: SpecialEntity,
    val organEntity: OrganEntity,
    val isEditor: Boolean
): Disposable {

    fun mutateCell(index: Int, threadId: Int) = with(cellEntity) {
        //TODO очень легко запутсаться и потерять какие-то значения при мутации, нужно либо перепроверить, либо менять все параметры разом или сделать общий метод с addCell
        if (!isMutateInThisStage[index] && energy[index] >= energyNecessaryToMutate[index]) {
            isMutateInThisStage[index] = true

            val action = cellActions[index]?.mutate ?: return

            worldCommandsManager.worldCommandBuffer[threadId].push(
                type = WorldCommandType.DECREMENT_MUTATION_COUNTER,
                ints = intArrayOf(organIndex[index])
            )

            var isFromMuscleToAnother = false

            val lastCellType = cellType[index].toInt()
            val lastCell = cellList[lastCellType]
            val newCell = action.cellType?.let { cellList[it] } ?: lastCell

            action.cellType?.let {
                isFromMuscleToAnother = lastCell is Muscle && newCell !is Muscle

                if (lastCell.isNeural && !newCell.isNeural) {
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        type = WorldCommandType.DELETE_NEURAL,
                        ints = intArrayOf(index, getNeuralGeneration(index))
                    )
                }
                if (!lastCell.isNeural && newCell.isNeural) {
                    val cellType: Int = it
                    val a: Float = action.a ?: 1f
                    val b: Float = action.b ?: 0f
                    val c: Float = action.c ?: 0f
                    val isSum: Boolean = action.isSum ?: true
                    val activationFuncType: Int = action.funActivation ?: 0
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        type = WorldCommandType.ADD_NEURAL,
                        ints = intArrayOf(index, cellType, activationFuncType),
                        floats = floatArrayOf(a, b, c),
                        booleans = booleanArrayOf(isSum)
                    )
                }
                if (lastCell.doesNeedNeuralConnections && !newCell.doesNeedNeuralConnections) {
                    neuralConnections.remove(index)
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        type = WorldCommandType.DELETE_NEURAL_CONNECTIONS,
                        ints = intArrayOf(index)
                    )
                }
                if (!lastCell.doesNeedNeuralConnections && newCell.doesNeedNeuralConnections) {
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        type = WorldCommandType.ADD_NEURAL_CONNECTIONS_EMPTY_LIST,
                        ints = intArrayOf(index)
                    )
                    command[index] = 0
                }
                if (lastCell is Eye && newCell !is Eye) {
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        type = WorldCommandType.DELETE_EYE,
                        ints = intArrayOf(index, specialEntity.getEyeGeneration(index))
                    )
                }
                if (lastCell !is Eye && newCell is Eye) {
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        type = WorldCommandType.ADD_EYE,
                        ints = intArrayOf(index, action.colorRecognition ?: 7),
                        floats = floatArrayOf(action.lengthDirected ?: 4.25f)
                    )
                }
                if (lastCell is Tail && newCell !is Tail) {
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        type = WorldCommandType.DELETE_TAIL,
                        ints = intArrayOf(index, specialEntity.getTailGeneration(index))
                    )
                }
                if (lastCell !is Tail && newCell is Tail) {
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        type = WorldCommandType.ADD_TAIL,
                        ints = intArrayOf(index)
                    )
                }
                if (lastCell is Producer && newCell !is Producer) {
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        type = WorldCommandType.DELETE_PRODUCER,
                        ints = intArrayOf(index, specialEntity.getProducerGeneration(index))
                    )
                }
                if (lastCell !is Producer && newCell is Producer) {
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        type = WorldCommandType.ADD_PRODUCER,
                        ints = intArrayOf(index)
                    )
                }
                if (lastCell is PheromoneEmitter && newCell !is PheromoneEmitter) {
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        type = WorldCommandType.DELETE_PHEROMONE_EMITTER,
                        ints = intArrayOf(index, specialEntity.getPheromoneEmitterGeneration(index))
                    )
                }
                if (lastCell !is PheromoneEmitter && newCell is PheromoneEmitter) {
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        type = WorldCommandType.ADD_PHEROMONE_EMITTER,
                        ints = intArrayOf(index)
                    )
                }
                cellType[index] = it.toByte()
                maxEnergy[index] = cellsSettings[it].maxEnergy
                setDragCoefficient(index, substrateSettings.data.viscosityOfTheEnvironment)
                setEffectOnContact(index, newCell.effectOnContact)
                setIsCollidable(index, newCell.isCollidable)
                setCellStiffness(index, cellsSettings[it].cellStiffness)
                isNeural[index] = newCell.isNeural

                if (newCell is Zygote && !isEditor) {
                    cellGenomeId[index] = 0
                }

                val genomeIndex = organEntity.genomeIndex[organIndex[index]]
                worldCommandsManager.worldCommandBuffer[threadId].push(
                    type = WorldCommandType.MUTATE_ON_START,
                    ints = intArrayOf(index, threadId, genomeIndex)
                )
            }

            if (isFromMuscleToAnother) {
                degreeOfShortening[index] = 1f
            }

            action.color?.let { setColor(index, it.toIntBits()) }

            action.radius?.let { setRadius(index, it) }

            if (lastCell.isNeural && newCell.isNeural) {
                action.funActivation?.let { setActivationFuncType(index, it.toByte()) }
                action.a?.let { setA(index, it) }
                action.b?.let { setB(index, it) }
                action.c?.let { setC(index, it) }
                action.isSum?.let { setIsSum(index, it) }
                setIsNeuronTransportable(index, newCell.isNeuronTransportable)
            }

            action.angleDirected?.let {
                val cosA = angleCos[index]
                val sinA = angleSin[index]
                val cosB = angleDirectedCos[index]
                val sinB = angleDirectedSin[index]

                angleCos[index] = cosA * cosB + sinA * sinB
                angleSin[index] = sinA * cosB - cosA * sinB

                angleDirectedCos[index] = cos(it)
                angleDirectedSin[index] = sin(it)

                val cosA2 = angleCos[index]
                val sinA2 = angleSin[index]
                val cosB2 = angleDirectedCos[index]
                val sinB2 = angleDirectedSin[index]

                angleCos[index] = cosA2 * cosB2 - sinA2 * sinB2
                angleSin[index] = sinA2 * cosB2 + cosA2 * sinB2
            }

            if (lastCell is Eye && newCell is Eye) {
                action.colorRecognition?.let { specialEntity.setColorDifferentiation(index, it.toByte()) }
                action.lengthDirected?.let { specialEntity.setVisibilityRange(index, it) }
            }

            action.pheromoneType?.let {
                pheromoneType[index] = it
            }

            if (action.physicalLink.isNotEmpty()) {
                val gridX = getX(index).toInt()
                val gridY = getY(index).toInt()
                val closestCells = gridManager.collectParticles(gridX, gridY)
                val idToIndexAssociation = closestCells
                    .filter { particleEntity.isCell[it] }
                    .map { particleEntity.holderEntityIndex[it] }
                    .filter { organIndex[it] == organIndex[index] && it != index }
                    .associateBy { cellGenomeId[it] }

                action.physicalLink.forEach { (cellGenomeIdToConnectWith, linkData) ->
                    val linkedCellIndex = idToIndexAssociation[cellGenomeIdToConnectWith]
                    if (linkedCellIndex != null) {
                        val linkIndex = linkEntity.linkIndexMap.get(index, linkedCellIndex)
                        if (linkData != null) {
                            if (linkIndex == -1) {
                                if (linkData.isNeuronal && linkData.directedNeuronLink != cellGenomeId[index]
                                    && linkData.directedNeuronLink != cellGenomeIdToConnectWith
                                ) {
                                    throw Exception("Incorrect logic in the neural-link")
                                }

                                val cellIndex: Int = index
                                val otherCellIndex: Int = linkedCellIndex
                                val linksLength: Float = linkData.length ?: -1f
                                val degreeOfShortening: Float = 1f
                                val isStickyLink: Boolean = false
                                val isNeuronLink: Boolean = linkData.isNeuronal
                                val isLink1NeuralDirected: Boolean = linkData.directedNeuronLink == cellGenomeId[index]
                                val linkColor = (linkData.color ?: if (linkData.isNeuronal) Color.CYAN else Color.RED).toIntBits()

                                if (otherCellIndex == -1) throw Exception("otherCellIndex == -1 in Mutate")
                                worldCommandsManager.worldCommandBuffer[threadId].push(
                                    type = WorldCommandType.ADD_LINK,
                                    booleans = booleanArrayOf(
                                        isStickyLink,
                                        isNeuronLink,
                                        isLink1NeuralDirected
                                    ),
                                    floats = floatArrayOf(linksLength, degreeOfShortening),
                                    ints = intArrayOf(cellIndex, otherCellIndex, linkColor)
                                )
                            } else {
                                with(linkEntity) {
                                    color[linkIndex] = (linkData.color ?: if (linkData.isNeuronal) Color.CYAN else Color.RED).toIntBits()

                                    if (!linkData.isNeuronal) {
                                        val cellA = links1[linkIndex]
                                        val cellB = links2[linkIndex]
                                        val cellIndex = if (isLink1NeuralDirected[linkIndex]) cellA else cellB
                                        if (isNeural[cellIndex]) {
                                            neuronImpulseInput[cellIndex] = 0f
                                            neuronImpulseOutput[cellIndex] = 0f
                                        }

                                        //Удаление нейролинков из списока для клеток, которые в них нуждаются
                                        if (cellList[cellType[cellA].toInt()].doesNeedNeuralConnections)
                                            removeNeuralConnection(cellA, linkIndex)
                                        if (cellList[cellType[cellB].toInt()].doesNeedNeuralConnections)
                                            removeNeuralConnection(cellB, linkIndex)

                                    } else {
                                        val cellA = links1[linkIndex]
                                        val cellB = links2[linkIndex]
                                        val cellLink1Id = cellGenomeId[cellA]
                                        val cellLink2Id = cellGenomeId[cellB]

                                        isLink1NeuralDirected[linkIndex] = linkData.directedNeuronLink == cellLink1Id

                                        //Добавление нейролинка в список для клеток, которые в них нуждаются
                                        if (cellList[cellType[cellA].toInt()].doesNeedNeuralConnections)
                                            addNeuralConnection(cellA, linkIndex)
                                        if (cellList[cellType[cellB].toInt()].doesNeedNeuralConnections)
                                            addNeuralConnection(cellB, linkIndex)

                                        if (linkData.directedNeuronLink != cellLink1Id && linkData.directedNeuronLink != cellLink2Id) {
                                            throw Exception("Incorrect logic in the neural-link ${linkData.directedNeuronLink} ${cellGenomeId[index]} $cellGenomeIdToConnectWith")
                                        }
                                    }

                                    isNeuronLink[linkIndex] = linkData.isNeuronal
                                }
                            }
                        } else {
                            if (linkIndex != -1) {
                                linkEntity.reinitParentLink(linkIndex)
                                worldCommandsManager.worldCommandBuffer[threadId].push(
                                    type = WorldCommandType.DELETE_LINK,
                                    ints = intArrayOf(linkIndex, linkEntity.getGeneration(linkIndex))
                                )
                            }
                        }
                    }
                }
            }

            energy[index] -= energyNecessaryToMutate[index] - 0.7f
        }
    }

    override fun dispose() {

    }

}
