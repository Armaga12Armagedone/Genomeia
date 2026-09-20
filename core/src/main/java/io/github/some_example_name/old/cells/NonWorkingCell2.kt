package io.github.some_example_name.old.cells

import io.github.some_example_name.old.core.utils.yellowColors

class NonWorkingCell2(cellTypeId: Int) : Cell(
    defaultColor = yellowColors[3],
    cellTypeId = cellTypeId,
    isDirected = true,
    isNeural = true,
    specialData = VascularData::class
) {

//    override fun doOnTick(cellIndex: Int, threadId: Int) = with(cellEntity) {
//        if (energy[cellIndex] <= 0) return
//        val specialData = specialEntity.getSpecialData(cellIndex) as VascularData
//        var speed = specialData.speed
//        var impulse = neuronImpulseOutput[cellIndex]
//        if (impulse < 0f) impulse = 0f
//        if (impulse > 1f) impulse = 1f
//        if (speed < impulse) speed += 0.012f else if (speed > impulse) speed -= 0.012f
//
//        if (speed <= 0.013f) return
//
//        val angleRad = angle[cellIndex] + PI.toFloat()
//        val cosA = cos(angleRad)
//        val sinA = sin(angleRad)
//
//        val directionX = cosA
//        val directionY = sinA
//        if (directionX.isNaN() || directionY.isNaN()) throw Exception("TODO потом убрать // remove later")
//
//        // Energy transfer rate dependent on the speed and dot product between its direction and the link's direction
//        // TODO: Add a checkbox for restricting the natural energy flow
//        for (i in 0..<linksAmount[cellIndex]) {
//            val linkId = links[cellIndex * MAX_LINK_AMOUNT + i]
//            val c1 = links1[linkId]
//            val c2 = links2[linkId]
//            val dx = x[c1] - x[c2]
//            val dy = y[c1] - y[c2]
//            if (dx == 0f && dy == 0f) continue
//            val length = sqrt(dx * dx + dy * dy)
//            var energyTransferRate = 0.1f * speed * (dx * directionX + dy * directionY) / length
//            if (c2 == cellIndex) {
//                energyTransferRate *= -1f
//            }
//            if (energy[c1] <= 0.01f && energyTransferRate < 0 || energy[c2] <= 0.01f && energyTransferRate > 0 ||
//                energy[c1] >= cellsSettings[cellType[c1] + 1].maxEnergy && energyTransferRate > 0 ||
//                energy[c2] >= cellsSettings[cellType[c2] + 1].maxEnergy && energyTransferRate < 0
//            ) continue
//            energy[c1] += energyTransferRate
//            energy[c2] -= energyTransferRate
//        }
//
//        energy[cellIndex] -= speed * 0.001f
//    }


}

@JvmInline
value class VascularData(
    val speed: Float
) : SpecialModData
