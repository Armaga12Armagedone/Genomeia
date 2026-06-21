package io.github.some_example_name.old.cells

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.core.utils.whiteColors

class Chameleon(cellTypeId: Int) : Cell(
    defaultColor = whiteColors[0],
    cellTypeId = cellTypeId,
    textureName = "Chameleon.png",
    isNeural = true
) {

    override fun doOnTick(cellIndex: Int, threadId: Int) {
        with(cellEntity) {
            val impulse = neuronImpulseOutput[cellIndex]
            val r = (impulse.toInt() % 256) / 255f
            val g = ((impulse.toInt() / 256) % 256) / 255f
            val b = ((impulse.toInt() / 65536) % 256) / 255f

            setColor(cellIndex, Color(r, g, b, 1f).toIntBits())

            energy[cellIndex] -= substrateSettings.cellsSettings[cellType[cellIndex].toInt()].energyActionCost
        }
    }
}
