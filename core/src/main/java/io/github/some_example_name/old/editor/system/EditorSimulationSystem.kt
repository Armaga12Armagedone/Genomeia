package io.github.some_example_name.old.editor.system

import io.github.some_example_name.old.cells.Zygote
import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.core.utils.StageTimelineBinarySearch
import io.github.some_example_name.old.core.utils.distanceTo
import io.github.some_example_name.old.editor.entities.CellReplay
import io.github.some_example_name.old.editor.entities.EyeReplay
import io.github.some_example_name.old.editor.entities.LinkReplay
import io.github.some_example_name.old.editor.entities.NeuralReplay
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.Entity
import io.github.some_example_name.old.entities.OrganEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.systems.genomics.CellSystem
import io.github.some_example_name.old.systems.genomics.OrganManager
import io.github.some_example_name.old.systems.genomics.genome.Genome
import io.github.some_example_name.old.systems.genomics.genome.GenomeManager
import io.github.some_example_name.old.systems.physics.GridManager
import io.github.some_example_name.old.systems.physics.ParticlePhysicsSystem.Companion.PARTICLE_MAX_RADIUS
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap

class EditorSimulationSystem(
    val cellEntity: CellEntity,
    val organEntity: OrganEntity,
    val organManager: OrganManager,
    val worldCommandsManager: WorldCommandsManager,
    val genomeManager: GenomeManager,
    val cellReplay: CellReplay,
    val linkReplay: LinkReplay,
    val eyeReplay: EyeReplay,
    val neuralReplay: NeuralReplay,
    val particleEntity: ParticleEntity,
    val cellSystem: CellSystem,
    val gridManager: GridManager,
    val zygote: Zygote,
    val entityList: List<Entity>
) {

    val baseOrganIndex = 0
    var genome = genomeManager.genomes[0]
    var tickByStage = IntArray(0)
    var stageByTick = StageTimelineBinarySearch(tickByStage)
    val mapCellGenomeIdToIndex = Int2IntOpenHashMap().apply{
        defaultReturnValue(-1)
    }

    var maxCellId = 0

    fun newGenome() {
        //TODO понять на сколько это вообще нужно
        val genomeStageInstruction = genome.genomeStageInstruction
        val dividedTimes = IntArray(genomeStageInstruction.size)
        val mutatedTimes = IntArray(genomeStageInstruction.size)

        genomeStageInstruction.forEachIndexed { index, stage ->
            stage.cellActions.forEach { (_, action) ->
                if (action.divide != null) {
                    dividedTimes[index]++
                    if (action.divide!!.id > maxCellId) {
                        maxCellId = action.divide!!.id
                    }
                }
                if (action.mutate != null) mutatedTimes[index] ++
            }
        }
        genome = Genome(
            genomeStageInstruction = genomeStageInstruction,
            dividedTimes = dividedTimes,
            mutatedTimes = mutatedTimes,
            name = genome.name
        )
        val genomeForPhysics = genome.deepCopy()

        genomeForPhysics.genomeStageInstruction.forEach { stage ->
            val invertedDivide = hashMapOf<Int, Int>()
            for ((key, action) in stage.cellActions) {
                action.divide?.id?.let { divideId ->
                    invertedDivide[divideId] = key
                }
            }

            for ((key, action) in stage.cellActions) {
                action.divide?.physicalLink?.forEach { linkKey, linkData ->
                    action.divide?.id?.let { id ->
                        invertedDivide[linkKey]?.let { divideKey ->
                            val cellAction = stage.cellActions[divideKey]
                            cellAction?.divide?.physicalLink?.put(id, linkData)
                        }
                    }
                }
                action.mutate?.physicalLink?.forEach { linkKey, linkData ->
                    invertedDivide[linkKey]?.let { divideKey ->
                        val cellAction = stage.cellActions[divideKey]
                        cellAction?.divide?.physicalLink?.put(key, linkData)
                    }
                }
            }
        }

        genomeManager.genomes[0] = genomeForPhysics
    }

    fun simulate() {
        mapCellGenomeIdToIndex.clear()
        gridManager.clearAll()
        entityList.forEach { it.clear() }
        val genomeIndex = 0
//        genome = genomeManager.genomes[genomeIndex]
        newGenome()

        val organIndex = organEntity.addOrgan(
            genomeIndex = genomeIndex,
            genomeSize = genome.genomeStageInstruction.size,
            dividedTimes = genome.dividedTimes[0],
            mutatedTimes = genome.mutatedTimes[0]
        )
        cellEntity.addCell(
            x = gridManager.gridWidth * 0.5f,
            y = gridManager.gridHeight * 0.5f,
            color = zygote.defaultColor.toIntBits(),
            radius = PARTICLE_MAX_RADIUS,
            cellType = zygote.cellTypeId,
            organIndex = organIndex
        )

        val stagesAmount = genome.genomeStageInstruction.size
        var stageCounter = 0
        tickByStage = IntArray(stagesAmount + 1)
        stageCounter++

        cellReplay.replayCellsCounterInTick.clear()
        cellReplay.tickStartIndices.clear()
        linkReplay.replayCellsCounterInTick.clear()
        linkReplay.tickStartIndices.clear()
        eyeReplay.replayCellsCounterInTick.clear()
        eyeReplay.tickStartIndices.clear()
        neuralReplay.replayCellsCounterInTick.clear()
        neuralReplay.tickStartIndices.clear()

        for (tick in 0..TIME_SIMULATION) {
            updateTick()
            cellReplay.copy()
            linkReplay.copy()
            eyeReplay.copy()
            neuralReplay.copy()

            if (organEntity.alreadyGrownUp[baseOrganIndex]) {
                tickByStage[stageCounter] = tick
                break
            }

            if (organEntity.justChangedStage[baseOrganIndex]) {
                tickByStage[stageCounter] = tick
                stageCounter++
            }
            if (tick == TIME_SIMULATION) throw Exception("Too long simulation!")
        }

        stageByTick = StageTimelineBinarySearch(tickByStage)
        cellEntity.aliveList.forEach { cellIndex ->
            mapCellGenomeIdToIndex.put(cellEntity.cellGenomeId[cellIndex], cellIndex)
        }
    }

    private fun updateTick() = with(cellEntity) {
        cellEntity.aliveList.forEach { cellIndex ->
            energy[cellIndex] += 1.5f
            if (energy[cellIndex] > maxEnergy[cellIndex]) {
                energy[cellIndex] = maxEnergy[cellIndex]
            }
            cellSystem.genomicTransformations(cellIndex)
        }

        worldCommandsManager.executingCommandsFromTheWorld()
        organManager.performOrgansNextStage()
    }

    fun getClickedCellIndex(
        clickX: Float,
        clickY: Float,
        currentTick: Int,
        nextStageTick: Int
    ): Pair<Int, Boolean>? {

        val x = clickX.toInt()
        val y = clickY.toInt()

        val fx = clickX - x
        val fy = clickY - y

        val dx = if (fx < 0.5f) intArrayOf(-1, 0) else intArrayOf(0, 1)
        val dy = if (fy < 0.5f) intArrayOf(-1, 0) else intArrayOf(0, 1)

        var bestIndex: Int? = null
        var bestDist = Float.MAX_VALUE
        var isPhantom = false

        for (i in dx) for (j in dy) {
            for (p in gridManager.getParticles(x + i, y + j)) {

                val currentCellIndex = cellReplay.getCellIndex(currentTick, p)
                val index = currentCellIndex ?: cellReplay.getCellIndex(nextStageTick, p) ?: continue

                val px = particleEntity.x[index]
                val py = particleEntity.y[index]
                val r = particleEntity.radius[index]

                val d = distanceTo(clickX, clickY, px, py)

                if (d <= r && d < bestDist) {
                    bestDist = d
                    bestIndex = p
                    isPhantom = currentCellIndex == null
                }
            }
        }

        return bestIndex?.let { it to isPhantom }
    }

    fun getAllCloseNeighboursEditor(
        grabbedX: Float,
        grabbedY: Float,
        grabbedRadius: Float,
        grabbedCellIndex: Int? = null,
        currentTick: Int,
        nextStageTick: Int,
        isSort: Boolean = false
    ): List<Int> {

        val gx = grabbedX.toInt()
        val gy = grabbedY.toInt()

        val result = mutableListOf<Int>()

        for (i in -1..1) for (j in -1..1) {
            for (p in gridManager.getParticles(gx + i, gy + j)) {

                if (p == grabbedCellIndex) continue

                val currentCellIndex = cellReplay.getCellIndex(currentTick, p)
                val index = currentCellIndex ?: cellReplay.getCellIndex(nextStageTick, p) ?: continue

                val dx = particleEntity.x[index] - grabbedX
                val dy = particleEntity.y[index] - grabbedY
                val rr = particleEntity.radius[index] + grabbedRadius

                if (dx * dx + dy * dy <= rr * rr) {
                    result.add(p)
                }
            }
        }

        return if (isSort) result.filterNot { it == grabbedCellIndex } else result
    }

    companion object {
        const val TIME_SIMULATION = 1_000
    }
}
