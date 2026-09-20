package io.github.some_example_name.old.systems.genomics

import io.github.some_example_name.old.core.DEBUG_CHECKS
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.NeuralLinkEntity

class NeuralLinkManager(
    val cellEntity: CellEntity,
    val neuralLinkEntity: NeuralLinkEntity
) {

    fun iterate() {
        with(neuralLinkEntity) {
            aliveList.forEach { linkIndex ->
                transportNeuralSignal(
                    linkIndex
                )
            }
        }
    }

    fun transportNeuralSignal(linkIndex: Int) = with(cellEntity) {
        val linkCellA = neuralLinkEntity.links1[linkIndex]
        val linkCellB = neuralLinkEntity.links2[linkIndex]

        if (DEBUG_CHECKS) {
            if (!isAliveAndSameGen(linkCellA, neuralLinkEntity.linksGeneration1[linkIndex]) ||
                !isAliveAndSameGen(linkCellB, neuralLinkEntity.linksGeneration2[linkIndex])
            ) {
                throw IllegalStateException(
                    "живая нейронная связь $linkIndex ссылается на мёртвую клетку: " +
                        "A=$linkCellA B=$linkCellB — detachAllNeuralLinks не был вызван"
                )
            }
        }

        val isLink1NeuralDirected = neuralLinkEntity.isLink1NeuralDirected[linkIndex]
        val signalToCellIndex = if (isLink1NeuralDirected) linkCellA else linkCellB
        val signalFromCellIndex = if (isLink1NeuralDirected) linkCellB else linkCellA

        val neuronImpulseOutput = neuronImpulseOutput[signalFromCellIndex]

        if (isNeural[signalToCellIndex]) {
            if (getIsSum(signalToCellIndex)) {
                neuronImpulseInput[signalToCellIndex] += neuronImpulseOutput
            } else {
                neuronImpulseInput[signalToCellIndex] *= neuronImpulseOutput
            }
        } else {
            neuronImpulseInput[signalToCellIndex] += neuronImpulseOutput
        }
    }
}
