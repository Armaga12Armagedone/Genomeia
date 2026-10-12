package io.github.some_example_name.render.components

import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import io.github.some_example_name.render.RenderMode
import java.nio.ByteBuffer

/**
 * Shared context passed between render components.
 * Contains all the data needed by various renderers in the pipeline.
 */
class RenderContext {
    // Shared mesh for fullscreen quad rendering
    lateinit var fullscreenMesh: Mesh

    // Camera/projection
    lateinit var cameraProjection: Matrix4

    /**
     * Клеток в кадре. Сами данные уже лежат в инстансном VBO у [CellGeometry]: их заливает
     * WorldRenderer один раз за кадр, до всех проходов.
     */
    var numInstances: Int = 0

    // Pheromone data
    var pheromoneData: ByteBuffer? = null
    var numPheromoneInstances: Int = 0

    /**
     * Коэффициенты спада концентрации феромона: радиус пятна = (a/P - 1)/K.
     * Приходят из RenderFrame — см. пояснение там, почему их здесь не хардкодят.
     */
    var pheromoneK: Float = 0f
    var pheromoneP: Float = 0f

    // Render parameters
    var blurAmount: Float = 0f
    var zoom: Float = 1f
    var vignetteEnabled: Float = 0f
    var mode: RenderMode = RenderMode.REALISTIC

    /** Короткая запись для проходов постпроцесса: все они рисуют только в этом случае. */
    val usePostProcess: Boolean get() = mode.usesPostProcess

    // Texture passing between components
    var currentTexture: Texture? = null

    // FBO references for component communication
    var sceneFbo: FrameBuffer? = null
    var blurFbo: FrameBuffer? = null
    var distortFbo: FrameBuffer? = null
}

/**
 * Interface for all rendering components in the pipeline.
 * Each component handles a specific rendering responsibility.
 */
interface RenderComponent {
    /**
     * Initialize GPU resources (shaders, FBOs, textures).
     * Called once during setup.
     */
    fun create()

    /**
     * Handle window/viewport resize.
     * Recreate any size-dependent resources like FBOs.
     */
    fun resize(width: Int, height: Int)

    /**
     * Execute the rendering pass.
     * @param context Shared render context with input data and textures
     */
    fun render(context: RenderContext)

    /**
     * Release all GPU resources.
     */
    fun dispose()
}
