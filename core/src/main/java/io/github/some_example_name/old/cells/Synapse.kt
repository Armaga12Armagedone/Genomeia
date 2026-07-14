package io.github.some_example_name.old.cells

import io.github.some_example_name.old.commands.WorldCommandType
import io.github.some_example_name.old.core.utils.skyBlueColors
import kotlin.Pair
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

class Synapse(cellTypeId: Int): Cell(
    defaultColor = skyBlueColors.last(),
    cellTypeId = cellTypeId,
    isNeural = true,
    isNeuronTransportable = false,
    doesNeedNeuralConnections = true
) {

    companion object {
        val fullDepressionTicks = 100
    }


    override fun onStart(cellIndex: Int, threadId: Int, genomeIndex: Int) {
        cellEntity.setWeight(cellIndex, Random(cellIndex).nextFloat() * cellEntity.getA(cellIndex))
    }

    //Бинарная активация
    //Пока что очень костыльная клетка, но нужна в качестве эксперимента

    override fun doOnTick(cellIndex: Int, threadId: Int) = with(cellEntity) {
        val learningRate = if (getB(cellIndex) <= 0) 0.01f else getB(cellIndex) // b
        val depression = if (getC(cellIndex) <= 0) 0.008f else getC(cellIndex) // c

        var weight = getWeight(cellIndex)

        val neuralLinks = getNeuralLinks(cellIndex)

        if (neuralLinks.size != 2) {
            neuronImpulseOutput[cellIndex] = 0f
            return
        }

        var inputSignalCellRed = -1
        var outputSignalCellPain = -1

        neuralLinks.forEach { linkIndex ->
            val linkCell1 = linkEntity.links1[linkIndex]
            val linkCell2 = linkEntity.links2[linkIndex]

            val directed = linkEntity.isLink1NeuralDirected[linkIndex]
            val signalToCellIndex = if (directed) linkCell1 else linkCell2
            val signalFromCellIndex = if (directed) linkCell2 else linkCell1

            if (cellIndex == signalToCellIndex) inputSignalCellRed = signalFromCellIndex
            if (cellIndex == signalFromCellIndex) outputSignalCellPain = signalToCellIndex
        }

        if (cellList[cellType[inputSignalCellRed].toInt()] !is Neuron){
            neuronImpulseOutput[cellIndex] = 0f
            return
        }
        if (cellList[cellType[outputSignalCellPain].toInt()] !is Neuron){
            neuronImpulseOutput[cellIndex] = 0f
            return
        }

        val (redSpikeTick, isRedJustSpiked) = spikeRed(cellIndex, neuronImpulseOutput[inputSignalCellRed])
        val (painSpikeTick, isPainJustSpiked) = spikePain(cellIndex, neuronImpulseOutput[outputSignalCellPain] - neuronImpulseOutput[inputSignalCellRed] * weight)
        if (redSpikeTick < 0f || painSpikeTick < 0f) {
            neuronImpulseOutput[cellIndex] = weight * neuronImpulseOutput[inputSignalCellRed]
            return
        }

        val decayRate = 0.05f

        if (isPainJustSpiked || isRedJustSpiked) {
            val dt = painSpikeTick - redSpikeTick
            val trace = exp(-abs(dt) * decayRate)

            if (dt >= 0) {
                weight += learningRate * trace
            } else {
                weight -= depression * trace
            }

            weight = weight.coerceIn(0f, 1f)
            setWeight(cellIndex, weight)
            setTickRed(cellIndex, -1)
            setTickPain(cellIndex, -1)
            if (weight <= 0f) {
                worldCommandsManager.worldCommandBuffer[threadId].push(
                    type = WorldCommandType.DELETE_CELL,
                    ints = intArrayOf(cellIndex, cellEntity.getGeneration(cellIndex))
                )
            }
        }

        neuronImpulseOutput[cellIndex] = weight * neuronImpulseOutput[inputSignalCellRed]
    }

    fun spikeRed(cellIndex: Int, redSignal: Float): Pair<Int, Boolean> = with(cellEntity) {
        val dx = redSignal - getDTime(cellIndex)
        setDTime(cellIndex, redSignal)

        if (dx > 0f && dx >= 0.999f) {
            setTickRed(cellIndex, simulationData.tickCounter)
            Pair(simulationData.tickCounter, true)
        } else {
            var redTick = getTickRed(cellIndex)
            if (simulationData.tickCounter - redTick > fullDepressionTicks) {
                redTick = -1
                setTickRed(cellIndex, redTick)
            }
            Pair(redTick, false)
        }
    }

    fun spikePain(cellIndex: Int, painSignal: Float): Pair<Int, Boolean> = with(cellEntity) {
        val dx = painSignal - getRemember(cellIndex)
        setRemember(cellIndex, painSignal)

        if (dx > 0f && dx >= 0.999f) {
            setTickPain(cellIndex, simulationData.tickCounter)
            simulationData.tickCounter
            Pair(simulationData.tickCounter, true)
        } else {
            var painTick = getTickPain(cellIndex)
            if (simulationData.tickCounter - painTick > fullDepressionTicks) {
                painTick = -1
                setTickPain(cellIndex, painTick)
            }
            Pair(painTick, false)
        }
    }
}
