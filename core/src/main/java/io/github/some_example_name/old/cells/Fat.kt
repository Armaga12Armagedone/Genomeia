package io.github.some_example_name.old.cells

import io.github.some_example_name.old.core.CellSettings
import io.github.some_example_name.old.core.utils.yellowColors


class Fat(cellTypeId: Int): Cell(
    defaultColor = yellowColors.first(),
    cellTypeId = cellTypeId,
    textureName = "fat.png",
    isNeural = true,
    isNeuronTransportable = false,
    isDirected = true,
    defaultCellSettings = CellSettings(
        maxEnergy = 10f,
        cellStiffness = 0.01f,
        linkStiffness = 0.0125f
    )
) {
    override fun doOnTick(cellIndex: Int, threadId: Int) = with (cellEntity) {
        neuronImpulseOutput[cellIndex] = energy[cellIndex] / maxEnergy[cellIndex]
    }
}
