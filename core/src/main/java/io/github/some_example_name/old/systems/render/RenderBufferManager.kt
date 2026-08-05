package io.github.some_example_name.old.systems.render

import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.cells.Eye
import io.github.some_example_name.old.cells.base.formulaType
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.NeuralLinkEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.PheromoneEntity
import io.github.some_example_name.old.entities.SpecialEntity
import io.github.some_example_name.old.systems.simulation.SimulationData
import kotlin.math.round
import java.util.concurrent.atomic.AtomicInteger

class RenderBufferManager(
    val simulationData: SimulationData,
    val cellEntity: CellEntity,
    val particleEntity: ParticleEntity,
    val pheromoneEntity: PheromoneEntity,
    val linkEntity: LinkEntity,
    val neuralLinkEntity: NeuralLinkEntity,
    val cellList: List<Cell>,
    val specialEntity: SpecialEntity,
    initialCellCapacity: Int = 50_000,
    initialLinkCapacity: Int = 50_000,
    initialPheromoneCapacity: Int = 1_000
) {

    // Двойные буферы
    private val cellBuffers = arrayOf(
        RenderCellBufferData(initialCellCapacity),
        RenderCellBufferData(initialCellCapacity)
    )
    private val pheromoneBuffers = arrayOf(
        PheromoneBufferData(initialPheromoneCapacity),
        PheromoneBufferData(initialPheromoneCapacity)
    )
    private val linkBuffers = arrayOf(
        RenderLinkBufferData(initialLinkCapacity),
        RenderLinkBufferData(initialLinkCapacity)
    )
    private val specificBuffer0 = RenderSpecificBufferData()
    private val specificBuffer1 = RenderSpecificBufferData()

    /**
     * ОДИН индекс на буферы клеток и связей — они обязаны переключаться вместе.
     *
     * Буфер связей хранит не индексы клеток, а positionInAlive, то есть позиции внутри
     * БУФЕРА ЧАСТИЦ. Эти позиции осмысленны только для того буфера частиц, который собран
     * в том же вызове updateBuffer.
     *
     * Раньше индексы были отдельные и переключались в разных местах: клетки сразу после
     * своей сборки, связи — в конце, уже после сборки феромонов. В это окно поток рендера
     * успевал взять НОВЫЙ буфер клеток и СТАРЫЙ буфер связей. Порядок aliveList меняется
     * каждый тик (удаление делает swap-with-last), поэтому старые позиции указывали на
     * произвольные другие частицы — на экране это связи между чужими организмами, а при
     * уменьшении числа клеток позиция уезжала за границу и линия уходила в ноль.
     *
     * Феромоны и specific самодостаточны: они не ссылаются на чужие буферы, поэтому у них
     * свои индексы и своё время переключения.
     */
    private val frameFrontIndex = AtomicInteger(0)
    private val pheromoneFrontIndex = AtomicInteger(0)
    private val specificFrontIndex = AtomicInteger(0)

    /**
     * Индекс согласованной пары буферов «клетки + связи».
     *
     * Поток рендера обязан прочитать его ОДИН раз за кадр и дальше брать оба буфера по нему.
     * Если читать через два отдельных геттера, симуляция успеет переключиться между ними —
     * и получится ровно тот же рассинхрон, от которого мы здесь избавляемся.
     */
    fun frontFrameIndex(): Int = frameFrontIndex.get()

    fun cellBuffer(frameIndex: Int): RenderCellBufferData = cellBuffers[frameIndex]
    fun linkBuffer(frameIndex: Int): RenderLinkBufferData = linkBuffers[frameIndex]

    fun getCurrentPheromoneBuffer(): PheromoneBufferData = pheromoneBuffers[pheromoneFrontIndex.get()]
    fun getCurrentSpecificBufferData(): RenderSpecificBufferData =
        if (specificFrontIndex.get() == 0) specificBuffer0 else specificBuffer1

    fun updateBuffer(performanceInfo: String = "") {
        // Общий back-индекс на клетки и связи: оба буфера собираются в него и публикуются
        // одним переключением в конце, чтобы рендер не увидел их вразнобой.
        val frameBackIndex = 1 - frameFrontIndex.get()

        // ==================== CELL ====================
        with(particleEntity) {
            val needed = aliveList.size
            val back = cellBuffers[frameBackIndex]

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

                    val bRadius = ((((radius[i] * cellEntity.degreeOfShortening[cellIndex]) - 0.05f) / 0.7f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val bEnergy = ((cellEntity.energy[cellIndex] / 10f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val bCell = cellEntity.cellType[cellIndex].toInt().coerceIn(0, 255)

                    back.packed1[bufIndex] = cosByte or (sinByte shl 8) or (bRadius shl 24)
                    back.packed2[bufIndex] = bEnergy or (bCell shl 8)

                    if (!doesUsePostProcess) {
                        val cell = cellList[cellEntity.cellType[cellIndex].toInt()]
                        val length = when {
                            cell is Eye -> specialEntity.getVisibilityRange(cellIndex)
                            cell.isDirected -> 1f
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
                    val bRadius = (((radius[i] - 0.05f) / 0.7f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val bCell = (cellList.size + 1).coerceIn(0, 255)

                    back.packed1[bufIndex] = bRadius shl 24
                    back.packed2[bufIndex] = bCell shl 8

                    if (!doesUsePostProcess) {
                        back.directedAngleCos[bufIndex] = 0f
                        back.directedAngleSin[bufIndex] = 0f
                    }
                }
            }
            back.renderCellBufferSize = aliveList.size
        }
        // Переключения тут больше нет: клетки публикуются вместе со связями, в самом конце.

        // ==================== PHEROMONE ===============
        val backPheromoneIndex = 1 - pheromoneFrontIndex.get()
        val back = pheromoneBuffers[backPheromoneIndex]
        with(pheromoneEntity) {
            val needed = aliveList.size

            back.ensureCapacity(needed)

            for (bufIndex in 0..<aliveList.size) {
                val i = aliveList.getInt(bufIndex)
                back.x[bufIndex] = x[i]
                back.y[bufIndex] = y[i]
                back.a[bufIndex] = time[i]
                back.color[bufIndex] = color[i]
                back.radiusSquared[bufIndex] = radiusSquared[i]
            }

            back.pheromoneBufferSize = aliveList.size
        }
        pheromoneFrontIndex.set(backPheromoneIndex)

        // ==================== LINK ====================
        val linkBack = linkBuffers[frameBackIndex]
        if (!doesUsePostProcess) {
            val needed = linkEntity.aliveList.size + neuralLinkEntity.aliveList.size
            val back = linkBack

            back.ensureCapacity(needed)

            var writeIndex = 0

            // длинные нейролинки
            with(neuralLinkEntity) {
                for (bufIndex in aliveList.indices) {
                    val i = aliveList.getInt(bufIndex)
                    val linkCellA = links1[i]
                    val linkCellB = links2[i]
                    val linkCellAIsDead = !cellEntity.isAlive[linkCellA] || cellEntity.getGeneration(linkCellA) != linksGeneration1[i]
                    val linkCellBIsDead = !cellEntity.isAlive[linkCellB] || cellEntity.getGeneration(linkCellB) != linksGeneration2[i]
                    if (linkCellAIsDead || linkCellBIsDead) continue

                    val particleAIndex = cellEntity.getParticleIndex(linkCellA)
                    val particleBIndex = cellEntity.getParticleIndex(linkCellB)

                    // Позиция -1 означает, что частицы нет в aliveList, то есть её в буфере
                    // клеток тоже не будет. Рисовать по такому индексу нельзя: линия уйдёт
                    // в нулевую координату. Проверка защитная — при живых клетках такого
                    // быть не должно.
                    val positionA = particleEntity.positionInAlive[particleAIndex]
                    val positionB = particleEntity.positionInAlive[particleBIndex]
                    if (positionA == -1 || positionB == -1) continue

                    back.cellA[writeIndex] = positionA
                    back.cellB[writeIndex] = positionB

                    // длинные нейролинки всегда neural-directed
                    back.isNeuralDirected[writeIndex] = if (isLink1NeuralDirected[i]) 1 else 0

                    writeIndex++
                }
            }

            // обычные линки
            with(linkEntity) {
                for (bufIndex in aliveList.indices) {
                    val i = aliveList.getInt(bufIndex)
                    val linkCellA = links1[i]
                    val linkCellB = links2[i]
                    val linkCellAIsDead = !cellEntity.isAlive[linkCellA] || cellEntity.getGeneration(linkCellA) != linksGeneration1[i]
                    val linkCellBIsDead = !cellEntity.isAlive[linkCellB] || cellEntity.getGeneration(linkCellB) != linksGeneration2[i]
                    if (linkCellAIsDead || linkCellBIsDead) continue

                    val particleAIndex = cellEntity.getParticleIndex(linkCellA)
                    val particleBIndex = cellEntity.getParticleIndex(linkCellB)

                    // Позиция -1 означает, что частицы нет в aliveList, то есть её в буфере
                    // клеток тоже не будет. Рисовать по такому индексу нельзя: линия уйдёт
                    // в нулевую координату. Проверка защитная — при живых клетках такого
                    // быть не должно.
                    val positionA = particleEntity.positionInAlive[particleAIndex]
                    val positionB = particleEntity.positionInAlive[particleBIndex]
                    if (positionA == -1 || positionB == -1) continue

                    back.cellA[writeIndex] = positionA
                    back.cellB[writeIndex] = positionB

                    back.isNeuralDirected[writeIndex] = -1

                    writeIndex++
                }
            }

            back.renderLinkAmount = writeIndex
        } else {
            // Связи не рисуются, но буфер публикуется вместе с клетками — обнуляем, чтобы
            // в нём не осталось позиций от позапрошлого кадра.
            linkBack.renderLinkAmount = 0
        }

        // Единственная публикация пары «клетки + связи».
        frameFrontIndex.set(frameBackIndex)

        // ==================== SPECIFIC ====================
        val specificBackIndex = 1 - specificFrontIndex.get()
        val specificBack = if (specificBackIndex == 0) specificBuffer0 else specificBuffer1

        with(specificBack) {
            ups = simulationData.ups
            updateTime = round(1e5f / simulationData.ups) / 100f
            cellsAmount = cellEntity.lastId - cellEntity.deadStack.size + 1
            particleAmount = particleEntity.lastId - particleEntity.deadStack.size + 1
            linksAmount = linkEntity.lastId - linkEntity.deadStack.size + 1

            detailedPerformance = performanceInfo

            val cellIndex = simulationData.selectedCellIndex
            if (cellIndex != -1 && cellEntity.isAlive[cellIndex]) {
                selectedCellIndex = cellEntity.cellGenomeId[cellIndex]
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

class PheromoneBufferData(initialCapacity: Int) {
    var capacity = initialCapacity
    var pheromoneBufferSize = 0

    var x = FloatArray(initialCapacity)
    var y = FloatArray(initialCapacity)
    var a = FloatArray(initialCapacity)
    var color = IntArray(initialCapacity)
    var radiusSquared = FloatArray(initialCapacity)

    fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > capacity) {
            val newCapacity = if (capacity == 0) minCapacity else (capacity * 2).coerceAtLeast(minCapacity)
            capacity = newCapacity

            x = x.copyOf(newCapacity)
            y = y.copyOf(newCapacity)
            a = a.copyOf(newCapacity)
            color = color.copyOf(newCapacity)
            radiusSquared = radiusSquared.copyOf(newCapacity)
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
    var selectedCellIndex: Int = -1,
    var detailedPerformance: String = ""          // ← добавлено
)
