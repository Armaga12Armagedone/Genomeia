package io.github.some_example_name.old.cells

import io.github.some_example_name.old.core.CellSettings
import io.github.some_example_name.old.core.utils.whiteColors

class Bone(cellTypeId: Int): Cell(
    defaultColor = whiteColors.first(),
    cellTypeId = cellTypeId,
    textureName = "bone.png",
    defaultCellSettings = CellSettings(
        maxEnergy = 3f,
        cellStiffness = 0.04f,
        linkStiffness = 0.4f
    )
)
