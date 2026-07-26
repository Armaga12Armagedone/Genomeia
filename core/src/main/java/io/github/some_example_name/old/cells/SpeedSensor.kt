package io.github.some_example_name.old.cells

import io.github.some_example_name.old.cells.base.activation
import io.github.some_example_name.old.core.utils.blueColors

class SpeedSensor(cellTypeId: Int) : Cell(
    defaultColor = blueColors[6],
    cellTypeId = cellTypeId,
    isDirected = true,
    isNeural = true,
    isNeuronTransportable = false
) {

    override fun doOnTick(cellIndex: Int, threadId: Int) = with(cellEntity) {
        val vx = getVx(cellIndex)
        val vy = getVy(cellIndex)
        val cos = angleCos[cellIndex]
        val sin = angleSin[cellIndex]

        // Проекция скорости на направление клетки (скалярное произведение)
        // > 0 — движется по направлению вектора
        // < 0 — движется против направления вектора
        var projectedSpeed = (vx * cos + vy * sin * 100).coerceIn(-1f, 1f)

        if (projectedSpeed < 0.0001f && projectedSpeed > -0.0001f) projectedSpeed = 0f

        neuronImpulseOutput[cellIndex] = activation(cellIndex, projectedSpeed)

        energy[cellIndex] -= substrateSettings.cellsSettings[cellType[cellIndex].toInt()].energyActionCost
    }
}
