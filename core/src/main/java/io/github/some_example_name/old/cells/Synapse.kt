package io.github.some_example_name.old.cells

import io.github.some_example_name.old.commands.WorldCommandType
import io.github.some_example_name.old.core.utils.skyBlueColors
import kotlin.Pair
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.random.Random

class Synapse(cellTypeId: Int): Cell(
    defaultColor = skyBlueColors.last(),
    cellTypeId = cellTypeId,
    isNeural = true,
    isNeuronTransportable = false,
    doesNeedNeuralConnections = true
) {

    // fullDepressionTicks теперь рассчитывается динамически в doOnTick


    override fun onStart(cellIndex: Int, threadId: Int, genomeIndex: Int) {
        cellEntity.setWeight(cellIndex, Random(cellIndex).nextFloat() * 0.2f)
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

        if (inputSignalCellRed == -1 || cellIndex == -1 || outputSignalCellPain == -1) {//TODO: Решить проблему с синапсом, outputSignallCellPain равен -1
//            println(inputSignalCellRed)
//            println(cellIndex)
//            println(outputSignalCellPain)
            return
        }

        if (cellList[cellType[inputSignalCellRed].toInt()] !is Neuron){
            neuronImpulseOutput[cellIndex] = 0f
            return
        }
        if (cellList[cellType[outputSignalCellPain].toInt()] !is Neuron){
            neuronImpulseOutput[cellIndex] = 0f
            return
        }

        val decayRate = getA(cellIndex)//0.025f

        // Рассчитываем fullDepressionTicks чтобы learningRate * trace < 0.001
        // trace = exp(-dt * decayRate), поэтому learningRate * exp(-fullDepressionTicks * decayRate) = 0.001
        // fullDepressionTicks = -ln(0.001 / learningRate) / decayRate
        val fullDepressionTicks = if (decayRate > 0.0001f && learningRate > 0.0001f) {
            (-ln(0.001f / learningRate) / decayRate).roundToInt().coerceAtLeast(1)
        } else {
            500 // fallback к старому значению если параметры некорректны
        }

        val (redSpikeTick, isRedJustSpiked) = spikeRed(cellIndex, neuronImpulseOutput[inputSignalCellRed], fullDepressionTicks)
        val (painSpikeTick, isPainJustSpiked) = spikePain(cellIndex, neuronImpulseOutput[outputSignalCellPain] - neuronImpulseOutput[inputSignalCellRed] * weight, fullDepressionTicks)
        if (redSpikeTick < 0f || painSpikeTick < 0f) {
            neuronImpulseOutput[cellIndex] = weight * neuronImpulseOutput[inputSignalCellRed]
            return
        }

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

    fun spikeRed(cellIndex: Int, redSignal: Float, fullDepressionTicks: Int): Pair<Int, Boolean> = with(cellEntity) {
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

    fun spikePain(cellIndex: Int, painSignal: Float, fullDepressionTicks: Int): Pair<Int, Boolean> = with(cellEntity) {
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
