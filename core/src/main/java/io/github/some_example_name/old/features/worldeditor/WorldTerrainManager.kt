package io.github.some_example_name.old.features.worldeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils.ceil
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.SubstancesEntity
import kotlin.Float
import kotlin.random.Random

class WorldTerrainManager(
    val particleEntity: ParticleEntity,
    val substancesEntity: SubstancesEntity
) {
    var map: Array<BooleanArray>? = null
    val gridWidth: Float = 0f
    val gridHeight: Float = 0f

    fun getColor(random: Random): Int {
        val r = random.nextFloat() * 0.25f + 0.1f   // 0.1 - 0.35
        val g = random.nextFloat() * 0.5f + 0.5f    // 0.5 - 1.0
        val b = random.nextFloat() * 0.2f + 0.05f   // 0.05 - 0.25

        return Color(r, g, b, 1f).toIntBits()
    }

    fun initWorld(
        gridWidth: Int = 128,
        gridHeight: Int = 128,
    ) {
        val random = Random(3)
        map?.let {
            for ((y, element) in it.withIndex()) {
                for ((x, element1) in element.withIndex()) {

                    val scaleX = x.toFloat()
                    val scaleY = y.toFloat()
                    if (x == 0 || x == it.size - 1 || y == 0 || y == element.size - 1) continue
                    if (element1) {
                        if (scaleX < gridWidth && scaleY < gridHeight) {
                            particleEntity.addParticle(
                                x = scaleX,
                                y = scaleY,
                                radius = 1f,
                                color = getColor(random),
                                isCell = false,
                                isSub = false,
                                holderEntityIndex = -1,
                                dragCoefficient = 0.4f
                            )
                        }

                    } else {
//                        if (random.nextInt(60) == 1) {
//                            substancesEntity.addSubstance(
//                                x = scaleX,
//                                y = scaleY,
//                                color = Color.RED.toIntBits(),
//                                radius = 0.25f,
//                                subType = 0.toByte(),
//                            )
//                        }
                    }
                }
            }
        }
        // Walls
        for (i in 1..<gridWidth) {
            particleEntity.addParticle(
                x = i.toFloat(),
                y = 1f,
                radius = 1f,
                color = getColor(random),
                isCell = false,
                isSub = false,
                holderEntityIndex = -1,
                dragCoefficient = 1.0f
            )
        }
        for (i in 1..<gridHeight) {
            particleEntity.addParticle(
                x = 1f,
                y = i.toFloat(),
                radius = 1f,
                color = getColor(random),
                isCell = false,
                isSub = false,
                holderEntityIndex = -1,
                dragCoefficient = 1.0f
            )
        }

        for (i in 1..<gridHeight) {
            particleEntity.addParticle(
                x = gridWidth - 1f,
                y = i.toFloat(),
                radius = 1f,
                color = getColor(random),
                isCell = false,
                isSub = false,
                holderEntityIndex = -1,
                dragCoefficient = 1.0f
            )
        }
        for (i in 1..<gridWidth) {
            particleEntity.addParticle(
                x = i.toFloat(),
                y = gridHeight - 1f,
                radius = 1f,
                color = getColor(random),
                isCell = false,
                isSub = false,
                holderEntityIndex = -1,
                dragCoefficient = 1.0f
            )
        }
    }
}
