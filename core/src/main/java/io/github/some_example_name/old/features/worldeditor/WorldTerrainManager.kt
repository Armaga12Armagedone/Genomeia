package io.github.some_example_name.old.features.worldeditor

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.SubstancesEntity
import io.github.some_example_name.old.features.settings.GlobalSettings
import kotlin.Float
import kotlin.random.Random

class WorldTerrainManager(
    val particleEntity: ParticleEntity,
    val substancesEntity: SubstancesEntity
) {
    var map: Array<BooleanArray>? = null
    val gridWith: Float = 0f
    val gridHeight: Float = 0f

    fun getColor(random: Random): Int {
        val r = random.nextFloat() * 0.25f + 0.1f   // 0.1 - 0.35
        val g = random.nextFloat() * 0.5f + 0.5f    // 0.5 - 1.0
        val b = random.nextFloat() * 0.2f + 0.05f   // 0.05 - 0.25

        return Color(r, g, b, 1f).toIntBits()
    }

    fun initWorld(
        gridWith: Int = 128,
        gridHeight: Int = 128
    ) {
        val random = Random(3)
        map?.let {
            for ((y, element) in it.withIndex()) {
                for ((x, element1) in element.withIndex()) {

                    val scaleX = x.toFloat() / 1.5f
                    val scaleY = y.toFloat() / 1.5f
                    if (x == 0 || x == it.size - 1 || y == 0 || y == element.size - 1) continue
                    if (element1) {
                        if (scaleX < gridWith && scaleY < gridHeight) {
                            particleEntity.addParticle(
                                x = scaleX,
                                y = scaleY,
                                radius = 0.5f,
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
        for (i in 1..<(gridWith / 2 * 3)) {
            particleEntity.addParticle(
                x = i.toFloat() / 1.5f,
                y = 0.5f,
                radius = 0.5f,
                color = getColor(random),
                isCell = false,
                isSub = false,
                holderEntityIndex = -1,
                dragCoefficient = 1.0f
            )
        }
        for (i in 1..<(gridHeight / 2 * 3)) {
            particleEntity.addParticle(
                x = 0.5f,
                y = i.toFloat() / 1.5f,
                radius = 0.5f,
                color = getColor(random),
                isCell = false,
                isSub = false,
                holderEntityIndex = -1,
                dragCoefficient = 1.0f
            )
        }

        for (i in 1..<(gridHeight / 2 * 3)) {
            particleEntity.addParticle(
                x = gridWith - 0.5f,
                y = i.toFloat() / 1.5f,
                radius = 0.5f,
                color = getColor(random),
                isCell = false,
                isSub = false,
                holderEntityIndex = -1,
                dragCoefficient = 1.0f
            )
        }
        for (i in 1..<(gridWith / 2 * 3)) {
            particleEntity.addParticle(
                x = i.toFloat() / 1.5f,
                y = gridHeight - 0.5f,
                radius = 0.5f,
                color = getColor(random),
                isCell = false,
                isSub = false,
                holderEntityIndex = -1,
                dragCoefficient = 1.0f
            )
        }
    }
}
