package io.github.some_example_name.old.systems.render.components

import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
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

    // Particle data
    lateinit var particleData: ByteBuffer
    var numInstances: Int = 0
    var isNewFrame: Boolean = false

    // Render parameters
    var blurAmount: Float = 0f
    var zoom: Float = 1f
    var vignetteEnabled: Float = 0f
    var usePostProcess: Boolean = true

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
