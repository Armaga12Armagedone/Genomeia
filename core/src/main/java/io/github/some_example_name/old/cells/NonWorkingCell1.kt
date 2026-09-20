package io.github.some_example_name.old.cells

import io.github.some_example_name.old.cells.base.activation
import io.github.some_example_name.old.core.utils.skyBlueColors

class NonWorkingCell1(cellTypeId: Int): Cell(
    defaultColor = skyBlueColors.last(),
    cellTypeId = cellTypeId,
    isNeural = true,
    isNeuronTransportable = false,
//    specialData = ControllerData::class
) {

    override fun doOnTick(cellIndex: Int, threadId: Int) = with(cellEntity) {
//        val specialData = specialEntity.getSpecialData(cellIndex) as ControllerData
//        val keyIsTouched = simulationData.controllerKeyTouched[specialData.attachedKey] ?: return
//        if (keyIsTouched) {
//            neuronImpulseOutput[cellIndex] = activation(cellIndex, 1f)
//        } else {
//            neuronImpulseOutput[cellIndex] = 0f
//        }
//        energy[cellIndex] -= substrateSettings.cellsSettings[cellType[cellIndex].toInt()].energyActionCost
    }
}

@JvmInline
value class ControllerData(
    val attachedKey: Char
): SpecialModData
