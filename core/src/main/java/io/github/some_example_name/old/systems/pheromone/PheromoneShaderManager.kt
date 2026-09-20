package io.github.some_example_name.old.systems.pheromone

import com.badlogic.gdx.math.Matrix4
import java.nio.ByteBuffer

interface PheromoneShaderManager {
    fun create()

    fun renderPheromones(
        cameraProjection: Matrix4,
        currentRead: ByteBuffer
    )

    fun dispose()
}
