package io.github.some_example_name.old.cells

import io.github.some_example_name.old.core.utils.genomeEditorColor

class TouchTrigger(cellTypeId: Int): Cell(
    defaultColor = genomeEditorColor[6],
    cellTypeId = cellTypeId,
    textureName = "TouchTrigger.png",
    isNeural = true,
    isNeuronTransportable = true,
    effectOnContact = true
) {

    override fun onContact(
        cellIndex: Int,
        particleIndexCollided: Int,
        distance: Float,
        threadId: Int
    ) = with(cellEntity) {
        val cellRadius = getRadius(cellIndex)
        val collidedRadius = particleEntity.radius[particleIndexCollided]

        val overlap = cellRadius + collidedRadius - distance
        if (overlap > 0f) {
            val pressure = overlap / cellRadius
            neuronImpulseInput[cellIndex] += pressure
        }
    }

}
