package io.github.some_example_name.old.systems.render

import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.cells.base.formulaType
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.SpecialEntity
import io.github.some_example_name.old.systems.simulation.SimulationData
import kotlin.math.round
import java.util.concurrent.atomic.AtomicInteger

class RenderBufferManager(
    val simulationData: SimulationData,
    val cellEntity: CellEntity,
    val particleEntity: ParticleEntity,
    val linkEntity: LinkEntity,
    val cellList: List<Cell>,
    val specialEntity: SpecialEntity,
    initialCellCapacity: Int = 50_000,
    initialLinkCapacity: Int = 50_000
) {

    // Двойные буферы
    private val cellBuffers = arrayOf(
        RenderCellBufferData(initialCellCapacity),
        RenderCellBufferData(initialCellCapacity)
    )
    private val linkBuffers = arrayOf(
        RenderLinkBufferData(initialLinkCapacity),
        RenderLinkBufferData(initialLinkCapacity)
    )
    private val specificBuffer0 = RenderSpecificBufferData()
    private val specificBuffer1 = RenderSpecificBufferData()

    private val cellFrontIndex = AtomicInteger(0)
    private val linkFrontIndex = AtomicInteger(0)
    private val specificFrontIndex = AtomicInteger(0)

    fun getCurrentCellBuffer(): RenderCellBufferData = cellBuffers[cellFrontIndex.get()]
    fun getCurrentLinkBuffer(): RenderLinkBufferData = linkBuffers[linkFrontIndex.get()]
    fun getCurrentSpecificBufferData(): RenderSpecificBufferData =
        if (specificFrontIndex.get() == 0) specificBuffer0 else specificBuffer1

    fun updateBuffer() {
        // ==================== CELL ====================
        with(particleEntity) {
            val needed = aliveList.size
            val backIndex = 1 - cellFrontIndex.get()
            val back = cellBuffers[backIndex]

            back.ensureCapacity(needed)

            for (bufIndex in 0..<aliveList.size) {
                val i = aliveList.getInt(bufIndex)
                back.x[bufIndex] = x[i]
                back.y[bufIndex] = y[i]
                back.color[bufIndex] = color[i]

                if (isCell[i]) {
                    val cellIndex = holderEntityIndex[i]

                    val cosByte = ((cellEntity.angleCos[cellIndex] * 0.5f + 0.5f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val sinByte = ((cellEntity.angleSin[cellIndex] * 0.5f + 0.5f) * 255f + 0.5f).toInt().coerceIn(0, 255)

                    val bRadius = (((radius[i] - 0.1f) / 0.4f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val bEnergy = 0//((cellEntity.energy[cellIndex] / 10f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val bCell = cellEntity.cellType[cellIndex].toInt().coerceIn(0, 255)

                    back.packed1[bufIndex] = cosByte or (sinByte shl 8) or (bRadius shl 24)
                    back.packed2[bufIndex] = bEnergy or (bCell shl 8)

                    if (!usePostProcess) {
                        val length = when (cellEntity.cellType[cellIndex].toInt()) {
                            14 -> specialEntity.getVisibilityRange(cellIndex)
                            3 -> 1f
                            9 -> 1f
                            18 -> 1f
                            else -> 0f
                        }
                        with(cellEntity) {
                            val cos = angleCos[cellIndex]
                            val sin = angleSin[cellIndex]
                            back.directedAngleCos[bufIndex] = cos * length
                            back.directedAngleSin[bufIndex] = sin * length
                        }
                    }
                } else {
                    val bRadius = (((radius[i] - 0.1f) / 0.4f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val bCell = (cellList.size + 1).coerceIn(0, 255)

                    back.packed1[bufIndex] = bRadius shl 24
                    back.packed2[bufIndex] = bCell shl 8

                    if (!usePostProcess) {
                        back.directedAngleCos[bufIndex] = 0f
                        back.directedAngleSin[bufIndex] = 0f
                    }
                }
            }
            back.renderCellBufferSize = aliveList.size
        }
        cellFrontIndex.set(1 - cellFrontIndex.get())   // swap

        // ==================== LINK ====================
        if (!usePostProcess) {
            val needed = linkEntity.aliveList.size
            val backIndex = 1 - linkFrontIndex.get()
            val back = linkBuffers[backIndex]

            back.ensureCapacity(needed)

            with(linkEntity) {
                for (bufIndex in 0..<aliveList.size) {
                    val i = aliveList.getInt(bufIndex)
                    val particleAIndex = cellEntity.getParticleIndex(links1[i])
                    val particleBIndex = cellEntity.getParticleIndex(links2[i])

                    back.cellA[bufIndex] = particleEntity.positionInAlive[particleAIndex]
                    back.cellB[bufIndex] = particleEntity.positionInAlive[particleBIndex]

                    back.isNeuralDirected[bufIndex] = if (isNeuronLink[i]) {
                        if (isLink1NeuralDirected[i]) 1 else 0
                    } else {
                        if (isStickyLink[i]) 3 else -1
                    }
                }
                back.renderLinkAmount = aliveList.size
            }
            linkFrontIndex.set(backIndex)
        }

        // ==================== SPECIFIC ====================
        val specificBackIndex = 1 - specificFrontIndex.get()
        val specificBack = if (specificBackIndex == 0) specificBuffer0 else specificBuffer1

        with(specificBack) {
            ups = simulationData.ups
            updateTime = round(1e5f / simulationData.ups) / 100f
            cellsAmount = cellEntity.lastId - cellEntity.deadStack.size + 1
            particleAmount = particleEntity.lastId - particleEntity.deadStack.size + 1
            linksAmount = linkEntity.lastId - linkEntity.deadStack.size + 1

            val cellIndex = simulationData.selectedCellIndex
            if (cellIndex != -1) {
                selectedCellIndex = cellEntity.cellGenomeId[cellIndex]//cellIndex
                neuronImpulseInput = cellEntity.neuronImpulseInput[cellIndex]
                neuronImpulseOutput = cellEntity.neuronImpulseOutput[cellIndex]
                isCellSelected = true
                grabbedCellX = cellEntity.getX(cellIndex)
                grabbedCellY = cellEntity.getY(cellIndex)
                val cellType = cellEntity.cellType[cellIndex].toInt()
                cellName = cellList[cellType].name +
                    if (cellEntity.isNeural[cellIndex])
                        " ${formulaType[cellEntity.getActivationFuncType(cellIndex)]} " +
                            "${cellEntity.getA(cellIndex)} ${cellEntity.getB(cellIndex)} ${cellEntity.getC(cellIndex)}"
                    else ""
            } else {
                neuronImpulseInput = null
                neuronImpulseOutput = null
                isCellSelected = false
                grabbedCellX = null
                grabbedCellY = null
                cellName = null
            }
        }
        specificFrontIndex.set(specificBackIndex)
    }
}

class RenderCellBufferData(initialCapacity: Int) {
    var capacity = initialCapacity
    var renderCellBufferSize = 0

    var x = FloatArray(capacity)
    var y = FloatArray(capacity)
    var color = IntArray(capacity)
    var packed1 = IntArray(capacity)
    var packed2 = IntArray(capacity)
    var directedAngleCos = FloatArray(capacity)
    var directedAngleSin = FloatArray(capacity)

    fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > capacity) {
            val newCapacity = if (capacity == 0) minCapacity else (capacity * 2).coerceAtLeast(minCapacity)
            capacity = newCapacity

            x = x.copyOf(newCapacity)
            y = y.copyOf(newCapacity)
            color = color.copyOf(newCapacity)
            packed1 = packed1.copyOf(newCapacity)
            packed2 = packed2.copyOf(newCapacity)
            directedAngleCos = directedAngleCos.copyOf(newCapacity)
            directedAngleSin = directedAngleSin.copyOf(newCapacity)
        }
    }
}

class RenderLinkBufferData(initialCapacity: Int) {
    var capacity = initialCapacity
    var renderLinkAmount = 0

    var cellA = IntArray(capacity)
    var cellB = IntArray(capacity)
    var isNeuralDirected = ByteArray(capacity)

    fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > capacity) {
            val newCapacity = if (capacity == 0) minCapacity else (capacity * 2).coerceAtLeast(minCapacity)
            capacity = newCapacity

            cellA = cellA.copyOf(newCapacity)
            cellB = cellB.copyOf(newCapacity)
            isNeuralDirected = isNeuralDirected.copyOf(newCapacity)
        }
    }
}

data class RenderSpecificBufferData(
    var ups: Int = 0,
    var updateTime: Float = 0f,
    var cellsAmount: Int = 0,
    var particleAmount: Int = 0,
    var linksAmount: Int = 0,
    var neuronImpulseInput: Float? = null,
    var neuronImpulseOutput: Float? = null,
    var isCellSelected: Boolean = false,
    var grabbedCellX: Float? = null,
    var grabbedCellY: Float? = null,
    var cellName: String? = null,
    var selectedCellIndex: Int = -1
)
