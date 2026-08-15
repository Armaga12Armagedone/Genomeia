package io.github.some_example_name.old.commands

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Disposable
import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.cells.SpecialModData
import io.github.some_example_name.old.cells.Zygote
import io.github.some_example_name.old.core.DIContext
import io.github.some_example_name.old.core.SELF_REPRODUCTION_ENABLED
import io.github.some_example_name.old.core.SubstrateSettings
import io.github.some_example_name.old.core.WorldResizable
import io.github.some_example_name.old.core.utils.OrderedIntPairMap
import io.github.some_example_name.old.core.utils.collectParticles
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.NeuralLinkEntity
import io.github.some_example_name.old.entities.OrganEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.PheromoneEntity
import io.github.some_example_name.old.entities.SpecialEntity
import io.github.some_example_name.old.systems.simulation.SimulationData
import io.github.some_example_name.old.entities.SubstancesEntity
import io.github.some_example_name.old.systems.genomics.OrganManager
import io.github.some_example_name.old.systems.genomics.genome.GenomeManager
import io.github.some_example_name.old.systems.physics.GridManager
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import kotlin.collections.map
import kotlin.math.sqrt

class WorldCommandsManager(
    val gridManager: GridManager,
    val organManager: OrganManager,
    val organEntity: OrganEntity,
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val neuralLinkEntity: NeuralLinkEntity,
    val specialEntity: SpecialEntity,
    val particleEntity: ParticleEntity,
    val pheromoneEntity: PheromoneEntity,
    val substrateSettings: SubstrateSettings,
    val genomeManager: GenomeManager,
    val simulationData: SimulationData,
    val cellList: List<Cell>,
    val substancesEntity: SubstancesEntity,
    val userCommandManager: UserCommandManager? = null,
    val diContext: DIContext,
    val isEditor: Boolean
): WorldResizable, Disposable {

    val mapCellGenomeIdToIndex = Int2IntOpenHashMap().apply{
        defaultReturnValue(-1)
    }

    var worldCommandSpecialModDataBuffer = Array(diContext.threadCount) { mutableListOf<SpecialModData>() }
    var worldCommandBuffer = Array(diContext.threadCount) { WorldCommandBuffer() }
    var worldCommandSecondBuffer = Array(diContext.threadCount) { WorldCommandBuffer(100) }
    val worldCommandLastBuffer = WorldCommandBuffer(100)

    private var lastAddedCellIndexBuffer = IntArray(diContext.threadCount) { -1 }
    private val organIndexCellIdMapIndex = OrderedIntPairMap()



    fun executingCommandsFromTheWorld() {
        worldCommandBuffer.forEachIndexed { threadId, worldCommandBuffer ->
            worldCommandBuffer.consume { type, ints, floats, booleans ->
                when (type) {
                    WorldCommandType.DIVIDE_ALIVE_CELL_ACTION_COUNTER -> {
                        organEntity.divideCounterThisStage[ints[0]]++
                    }
                    WorldCommandType.MUTATE_ALIVE_CELL_ACTION_COUNTER -> {
                        organEntity.mutateCounterThisStage[ints[0]]++
                    }
                    WorldCommandType.ADD_PARTICLE -> {
//                        particleEntity.addParticle(
//                            x = floats[0],
//                            y = floats[1],
//                            radius = floats[2],
//                            color = ints[0],
//                            isCell = false,
//                            holderEntityIndex = -1
//                        )
                    }
                    WorldCommandType.ADD_LINK -> {
                        // ints[0] == -1 означает "клетка, созданная предыдущей командой
                        // этого же буфера". Но ADD_CELL мог отказаться её создавать
                        // (морфогенез, если место уже занято), и тогда привязываться не
                        // к чему: раньше здесь подхватывался ОСТАТОК от прошлого деления,
                        // возможно вообще из другого тика, и связь уходила к посторонней
                        // клетке. См. сброс lastAddedCellIndexBuffer в ADD_CELL.
                        val cellIndex = if (ints[0] == -1) {
                            lastAddedCellIndexBuffer[threadId]
                        } else ints[0]

                        if (cellIndex == -1) return@consume

                        val linkIndex = linkEntity.addLink(
                            cellIndex = cellIndex,
                            otherCellIndex = ints[1],
                            linksLength = floats[0],
                            cellGeneration = ints[2],
                            otherCellGeneration = ints[3],
                        )
                        // -1 значит, что связь не создалась: либо у одной из клеток кончились
                        // слоты (CellEntity.MAX_LINKS_PER_CELL), либо одна из них умерла
                        // (или её индекс переиспользовала другая клетка) раньше, чем команда
                        // дошла до применения.
                        if (linkIndex != -1) {
                        }
                    }

                    WorldCommandType.ADD_NEURAL_LINK -> {
                        // Та же ловушка, что и в ADD_LINK: клетки могло не быть создано.
                        val cellIndex = if (ints[0] == -1) {
                            lastAddedCellIndexBuffer[threadId]
                        } else ints[0]

                        if (cellIndex == -1) return@consume

                        neuralLinkEntity.addNeuralLink(
                            cellIndex = cellIndex,
                            otherCellIndex = ints[1],
                            isLink1NeuralDirected = booleans[0],
                            color = ints[2],
                            cellGeneration = ints[3],
                            otherCellGeneration = ints[4]
                        )
                    }
                    WorldCommandType.DELETE_NEURAL_LINK -> {
                        neuralLinkEntity.deleteNeuralLink(linkIndex = ints[0], linkGeneration = ints[1])
                    }
                    WorldCommandType.DELETE_LINK -> {
                        val linkIndex = ints[0]
                        linkEntity.deleteLink(linkIndex, linkGeneration = ints[1])
                    }
                    WorldCommandType.ADD_CELL -> {
                        val isMorphogenesis = booleans[1]
                        val x = floats[0]
                        val y = floats[1]

                        val radius = floats[2]

                        var isDivide = true
                        var closestCells: IntArray? = null

                        // Родитель мог умереть, пока команда ждала применения.
                        //
                        // ADD_CELL формируется в параллельной фазе, а применяется позже и по
                        // буферам подряд: DELETE_CELL из буфера 1 выполнится раньше, чем
                        // ADD_CELL из буфера 3. Делить уже нечего — контекст деления исчез.
                        //
                        // Если всё-таки создать клетку, она возьмёт свой якорь вместо
                        // родительского (организм) и окажется новой системой
                        // отсчёта посреди чужого организма. Все её связи с телом отвергнет
                        // барьер в LinkEntity.addLink, и клетка молча повиснет отдельно.
                        //
                        // Одного isAlive мало: индексы клеток переиспользуются через deadStack,
                        // поэтому умерший и заново занятый слот снова "жив", но это уже другая
                        // клетка. Тогда потомок унаследовал бы ЧУЖУЮ карту тела и чужой якорь —
                        // и присоединился бы не к тому организму, причём совершенно молча.
                        // Отличает их поколение, снятое в момент постановки команды.
                        //
                        // Проверка стоит до морфогенеза: она заодно экономит collectParticles.
                        val parentCellIndex = ints[4]
                        val parentGeneration = ints[9]
                        if (parentCellIndex != -1 &&
                            !cellEntity.isAliveAndSameGen(parentCellIndex, parentGeneration)
                        ) {
                            isDivide = false
                        }

                        if (isDivide && isMorphogenesis) {
                            closestCells = gridManager.collectParticles(
                                gridX = x.toInt(),
                                gridY = y.toInt(),
                                radius = 1
                            )
                            //TODO сделать без алокаций
                            closestCells
                                .filter { particleEntity.isCell[it] }
                                .forEach {
                                    val dx = particleEntity.x[it] - x
                                    val dy = particleEntity.y[it] - y
                                    val squareDist = dx * dx + dy * dy
                                    if (squareDist < 0.36f) {
                                        isDivide = false
                                        return@forEach
                                    }
                                }
                        }
                        if (isDivide) {
                            val cellType = ints[2]
                            val newCell = cellList[cellType]

                            val cellGenomeId = if (newCell is Zygote && !isEditor) {
                                0
                            } else ints[1]
                            val parentOrganIndex = ints[3]
                            val parentIndex = ints[4]
                            val organIndex = if (newCell is Zygote) {
                                if (isEditor) 0 else -1
                            } else parentOrganIndex

                            val specialModDataIndex = ints[8]

                            val cellIndex = cellEntity.addCell(
                                x = x,
                                y = y,
                                color = ints[0],
                                radius = radius,
                                cellGenomeId = cellGenomeId,
                                cellType = cellType,
                                organIndex = organIndex,
                                parentIndex = parentIndex,
                                angleCos = floats[3],
                                angleSin = floats[4],
                                angleDiffCos = floats[5],
                                angleDiffSin = floats[6],
                                colorDifferentiation = ints[5],
                                visibilityRange = floats[7],
                                a = floats[8],
                                b = floats[9],
                                c = floats[10],
                                isSum = booleans[0],
                                activationFuncType = ints[6].toByte(),
                                pheromoneType = ints[7],
                                specialModData = if (specialModDataIndex == -1) null else worldCommandSpecialModDataBuffer[threadId][specialModDataIndex]
                            )
                            if (isEditor) {
                                mapCellGenomeIdToIndex.put(cellGenomeId, cellIndex)
                            }

                            //TODO сделать без алокаций
                            closestCells?.filter { particleEntity.isCell[it] }
                                ?.map { particleEntity.holderEntityIndex[it] }
                                // Связываться можно только со своим организмом.
                                //
                                // Это не косметика: слот связи назначается по СТАТИЧЕСКИМ
                                // organIndex, а он осмыслен
                                // сравнимы лишь внутри одного организма — у разных начала
                                // отсчёта не связаны. Связь между организмами дала бы
                                // произвольное статическое расстояние между её клетками,
                                // а с ним вернулась бы гонка на vx/vy в фазе связей.
                                ?.filter { cellEntity.organIndex[it] == cellEntity.organIndex[cellIndex] }
                                ?.forEach {
                                        val dx = cellEntity.getX(it) - x
                                        val dy = cellEntity.getY(it) - y
                                        val rSum = cellEntity.getRadius(it) + radius
                                        val squareDist = dx * dx + dy * dy
                                        if (rSum * rSum > squareDist) {
                                            val linkIndex = linkEntity.addLink(
                                                cellIndex = cellIndex,
                                                otherCellIndex = it,
                                                linksLength = sqrt(squareDist),
                                            )
                                            if (linkIndex != -1) {
                                            }
                                        }

                                }

                            val genomeIndex = organEntity.genomeIndex[parentOrganIndex]
                            newCell.onStart(cellIndex, -1, genomeIndex)

                            if (parentIndex != -1 && cellEntity.parentIndex[parentIndex] == -1) {
                                cellEntity.parentIndex[parentIndex] = cellIndex
                            }

                            lastAddedCellIndexBuffer[threadId] = cellIndex
                            organIndexCellIdMapIndex.put(parentOrganIndex, cellGenomeId, cellIndex)
                        } else {
                            // Клетка не создана — значит и «последней созданной» для
                            // следующих команд этого буфера нет.
                            //
                            // Без этого сброса ADD_LINK и ADD_NEURAL_LINK, идущие следом
                            // и ссылающиеся на неё через -1, подхватывали ОСТАТОК от
                            // прошлого деления: индекс живой, но совершенно посторонней
                            // клетки, возможно из другого тика и другого места мира.
                            // Связь при этом создавалась настоящая, считалась физикой и
                            // писала в vx/vy чужих частиц.
                            //
                            // Ловилось это проверкой статических координат: посторонняя
                            // клетка часто оказывалась из того же организма и физически
                            // рядом (то есть проходила проверку расстояния в addLink),
                            // но по карте тела стояла в другом месте.
                            lastAddedCellIndexBuffer[threadId] = -1
                        }
                    }
                    WorldCommandType.DECREMENT_DIVIDE_COUNTER -> {
                        organEntity.dividedTimes[ints[0]]--
                    }
                    WorldCommandType.DECREMENT_MUTATION_COUNTER -> {
                        organEntity.mutatedTimes[ints[0]]--
                    }
                    WorldCommandType.DELETE_CELL -> {
                        val cellIndex = ints[0]
                        val cellGeneration = ints[1]
                        if (cellEntity.getParticleIndex(cellIndex) == userCommandManager?.grabbedParticleIndex) {
                            userCommandManager.grabbedParticleIndex = -1
                            userCommandManager.isDragging = false
                        }

                        if (cellEntity.isAlive[cellIndex] && cellEntity.getGeneration(cellIndex) == cellGeneration) {
                            val x = cellEntity.getX(cellIndex)
                            val y = cellEntity.getY(cellIndex)
                            val newSubIndex = substancesEntity.addSubstance(
                                x = cellEntity.getX(cellIndex),
                                y = cellEntity.getY(cellIndex),
                                color = Color.RED.toIntBits(),
                                radius = (0.03f * cellEntity.energy[cellIndex]).coerceIn(0.1f, 0.5f),
                                subType = 0,
                            )
                            pheromoneEntity.addPheromone(x, y, emitterIndex = substancesEntity.particleIndex[newSubIndex], type = 0)
                            pheromoneEntity.addPheromone(x, y, emitterIndex = -1, type = 18, time = 0.3f)
                            organManager.cellDeleted(cellIndex)
                            // Строго до deleteCell: detachAllLinks читает список соседей
                            // умирающей клетки, а deleteCell его затирает. Здесь же
                            // выжившие соседи получают isOnEdge и сброс parentIndex —
                            // раньше это делал processLink, увидев мёртвую клетку.
                            linkEntity.detachAllLinks(cellIndex)
                            neuralLinkEntity.detachAllNeuralLinks(cellIndex)
                            cellEntity.deleteCell(cellIndex)
                            cellList[cellEntity.cellType[cellIndex].toInt()].onDie(cellIndex)
                        }
                    }
                    WorldCommandType.DELETE_NEURAL -> {
                        cellEntity.deleteNeural(cellIndex = ints[0], neuralGeneration = ints[1])
                    }
                    WorldCommandType.ADD_NEURAL -> {
                        cellEntity.addNeural(
                            index = ints[0],
                            cellType = ints[1],
                            a = floats[0],
                            b = floats[1],
                            c = floats[2],
                            isSum = booleans[0],
                            activationFuncType = ints[2].toByte()
                        )
                    }
                    WorldCommandType.DELETE_EYE -> {
                        specialEntity.deleteEye(cellIndex = ints[0], eyeGeneration = ints[1])
                    }
                    WorldCommandType.ADD_EYE -> {
                        specialEntity.addEye(
                            index = ints[0],
                            colorDifferentiation = ints[1],
                            visibilityRange = floats[0]
                        )
                    }
                    WorldCommandType.DELETE_TAIL -> {
                        specialEntity.deleteTail(cellIndex = ints[0], tailGeneration = ints[1])
                    }
                    WorldCommandType.ADD_TAIL -> {
                        specialEntity.addTail(index = ints[0])
                    }
                    WorldCommandType.DELETE_PRODUCER -> {
                        specialEntity.deleteProducer(cellIndex = ints[0], producerGeneration = ints[1])
                    }
                    WorldCommandType.ADD_PRODUCER -> {
                        specialEntity.addProducer(index = ints[0])
                    }
                    WorldCommandType.ADD_ORGAN -> {
                        worldCommandLastBuffer.push(
                            type = WorldCommandType.ADD_ORGAN,
                            booleans = booleans,
                            floats = floats,
                            ints = ints
                        )
                    }
                    WorldCommandType.DELETE_ORGAN -> {
                        val organIndex = ints[0]
                        val organGeneration = ints[1]
                        //TODO DELETE_ORGAN
                        //TODO подумать все ли нормально будет с organIndexCellIdMapIndex
                    }
                    WorldCommandType.ADD_SUBSTANCE -> {
                        val newSubIndex = substancesEntity.addSubstance(
                            x = floats[0],
                            y = floats[1],
                            color = ints[0],
                            radius = floats[2],
                            subType = ints[1].toByte(),
                        )
                        pheromoneEntity.addPheromone(x = floats[0], y = floats[1], emitterIndex = substancesEntity.particleIndex[newSubIndex], type = 0)
                    }
                    WorldCommandType.DELETE_SUBSTANCE -> {
                        substancesEntity.deleteSubstance(subIndex = ints[0], subGeneration = ints[1])
                    }
                    WorldCommandType.ADD_PHEROMONE -> {
                        pheromoneEntity.addPheromone(x = floats[0], y = floats[1], emitterIndex = ints[0], type = ints[1])
                    }
                    WorldCommandType.DELETE_PHEROMONE -> {
                        pheromoneEntity.deletePheromone(pheromoneIndex = ints[0], pheromoneGeneration = ints[1])
                    }
                    WorldCommandType.DELETE_PHEROMONE_EMITTER -> {
                        specialEntity.deletePheromoneEmitter(cellIndex = ints[0], pheromoneEmitterGeneration = ints[1])
                    }
                    WorldCommandType.ADD_PHEROMONE_EMITTER -> {
                        specialEntity.addPheromoneEmitter(index = ints[0])
                    }
                    WorldCommandType.MUTATE_ON_START -> {
                        val index = ints[0]
                        val threadId = ints[1]
                        val genomeIndex = ints[2]
                        val newCell = cellList[cellEntity.cellType[index].toInt()]
                        newCell.onStart(index, threadId, genomeIndex)
                    }
                    else -> {}
                }
            }
        }

        worldCommandSecondBuffer.forEachIndexed { _, worldCommandBuffer ->
            worldCommandBuffer.consume { type, ints, floats, booleans ->
                when (type) {
                    WorldCommandType.ADD_LINK_BY_ID -> {
                        val cellId = ints[0]
                        val otherCellId = ints[1]
                        val organIndex = ints[2]
                        val linksLength = floats[0]

                        val cellIndex = organIndexCellIdMapIndex.get(organIndex, cellId)
                        val otherCellIndex = organIndexCellIdMapIndex.get(organIndex, otherCellId)

                        if (cellIndex != -1 && otherCellIndex != -1 &&
                            !cellEntity.areCellsLinked(cellIndex, otherCellIndex)
                        ) {
                            val linkIndex = linkEntity.addLink(
                                cellIndex = cellIndex,
                                otherCellIndex = otherCellIndex,
                                linksLength = linksLength,
                            )
                            if (linkIndex != -1) {
                            }
                        }

                    }

                    WorldCommandType.ADD_NEURAL_LINK_BY_ID -> {
                        val cellId = ints[0]
                        val otherCellId = ints[1]
                        val organIndex = ints[2]
                        val isLink1NeuralDirected = booleans[0]

                        val cellIndex = organIndexCellIdMapIndex.get(organIndex, cellId)
                        val otherCellIndex = organIndexCellIdMapIndex.get(organIndex, otherCellId)

                        if (cellIndex != -1 && otherCellIndex != -1 && neuralLinkEntity.linkIndexMap.get(cellIndex, otherCellIndex) == -1) {
                            neuralLinkEntity.addNeuralLink(
                                cellIndex = cellIndex,
                                otherCellIndex = otherCellIndex,
                                isLink1NeuralDirected = isLink1NeuralDirected,
                                color = ints[3]
                            )
                        }
                    }

                    else -> {}
                }
            }
        }

        organIndexCellIdMapIndex.clear()
    }

    fun executingLastCommandsFromTheWorld() {
        worldCommandLastBuffer.consume { type, ints, floats, booleans ->
            when (type) {
                WorldCommandType.ADD_ORGAN -> {
                    if (!isEditor) {
                        // Сюда попадают только самозародившиеся организмы: ручной спавн
                        // заводит организм напрямую в UserCommandManager, до первой клетки.
                        //
                        // Аренами этот путь пока не поддержан, и это осознанно: зигота
                        // создаётся раньше своего организма, поэтому физически не может
                        // оказаться в его арене (см. SELF_REPRODUCTION_ENABLED). Падаем
                        // явно, а не заводим организм, у которого часть тела вне арены —
                        // такое не упало бы вовсе, а просто выключило бы обход по диапазону.
                        if (!SELF_REPRODUCTION_ENABLED) {
                            throw IllegalStateException(
                                "ADD_ORGAN при выключенном SELF_REPRODUCTION_ENABLED: " +
                                    "кто-то создаёт организм в обход UserCommandManager"
                            )
                        }

                        val organStartCellOrganIndex = ints[0]
                        val newOrganIndex = organEntity.addOrgan(
                            genomeIndex = ints[1],
                            genomeSize = ints[2],
                            dividedTimes = ints[3],
                            mutatedTimes = ints[4]
                        )
                        cellEntity.organIndex[organStartCellOrganIndex] = newOrganIndex
//                        organEntity.allocateArenas(organIndex = newOrganIndex)
                    }
                }

                else -> {}
            }
        }
    }

    override fun resize() {
        worldCommandBuffer = Array(diContext.threadCount) { WorldCommandBuffer() }
        worldCommandSecondBuffer = Array(diContext.threadCount) { WorldCommandBuffer(100) }
        lastAddedCellIndexBuffer = IntArray(diContext.threadCount) { -1 }
    }

    override fun dispose() {
    }
}
