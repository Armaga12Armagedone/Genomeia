package io.github.some_example_name.old.cells

import io.github.some_example_name.old.core.CellSettings
import io.github.some_example_name.old.core.utils.pinkColors

class Zygote(cellTypeId: Int) : Cell(
    key = "zygote",
    defaultColor = pinkColors[0],
    cellTypeId = cellTypeId,
    textureName = "zygote.png",
    isDirected = true,
    defaultCellSettings = CellSettings(
        maxEnergy = 10f,
        cellStiffness = 0.02f,
        linkStiffness = 0.0125f,
        energyActionCost = 0f
    )
) {

    // Организм зиготе заводят до неё самой — OrganismSpawner (игрок, продюсер, деление).

    override fun doOnTick(cellIndex: Int, threadId: Int) = with (cellEntity) {
        if (energy[cellIndex] < substrateSettings.cellsSettings[cellType[cellIndex].toInt()].maxEnergy) {
            energy[cellIndex] += substrateSettings.data.amountOfSolarEnergy
        }
    }
}
