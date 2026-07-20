package io.github.some_example_name.old.systems.render.components

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL31
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.BufferUtils
import io.github.some_example_name.old.systems.pheromone.PheromonesManager.Companion.K
import io.github.some_example_name.old.systems.pheromone.PheromonesManager.Companion.P
import io.github.some_example_name.old.systems.render.RenderSystem.Companion.INITIAL_PHEROMONE_CAPACITY
import io.github.some_example_name.old.systems.render.RenderSystem.Companion.PHEROMONE_STRUCT_SIZE

/**
 * Renders pheromones using instanced drawing with SSBO data.
 * Renders after post-processing but before vignette.
 */
class PheromoneRenderer : RenderComponent {

    private lateinit var shader: ShaderProgram
    private lateinit var mesh: Mesh

    private val ssbo = IntArray(1)
    private var ssboCapacity = 0

    override fun create() {
        val vert = Gdx.files.internal("shaders/pheromone/pheromone_pc.vert").readString()
        val frag = Gdx.files.internal("shaders/pheromone/pheromone.frag").readString()
        shader = ShaderProgram(vert, frag)
        if (!shader.isCompiled) throw RuntimeException("Pheromone shader failed: ${shader.log}")

        val attributes = VertexAttributes(
            VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position")
        )

        val vertices = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )

        mesh = Mesh(false, 4, 0, attributes).apply { setVertices(vertices) }

        val buf = BufferUtils.newIntBuffer(1)
        Gdx.gl31.glGenBuffers(1, buf)
        ssbo[0] = buf.get(0)

        resizeSSBO(INITIAL_PHEROMONE_CAPACITY * PHEROMONE_STRUCT_SIZE)
    }

    private fun resizeSSBO(newSize: Int) {
        if (newSize <= ssboCapacity) return
        var cap = ssboCapacity
        if (cap == 0) cap = INITIAL_PHEROMONE_CAPACITY * PHEROMONE_STRUCT_SIZE
        while (cap < newSize) cap = (cap * 1.5).toInt()

        Gdx.gl31.glBindBuffer(GL31.GL_SHADER_STORAGE_BUFFER, ssbo[0])
        Gdx.gl31.glBufferData(GL31.GL_SHADER_STORAGE_BUFFER, cap, null, GL20.GL_DYNAMIC_DRAW)
        Gdx.gl31.glBindBufferBase(GL31.GL_SHADER_STORAGE_BUFFER, 1, ssbo[0])
        ssboCapacity = cap
    }

    override fun resize(width: Int, height: Int) {
        // No size-dependent resources
    }

    override fun render(context: RenderContext) {
        if (!context.usePostProcess) return

        val pheromoneData = context.pheromoneData ?: return
        val numInstances = context.numPheromoneInstances
        if (numInstances == 0) return

        val blurFbo = context.blurFbo ?: return

        resizeSSBO(pheromoneData.remaining())

        Gdx.gl31.glBindBuffer(GL31.GL_SHADER_STORAGE_BUFFER, ssbo[0])
        Gdx.gl31.glBufferSubData(GL31.GL_SHADER_STORAGE_BUFFER, 0, pheromoneData.remaining(), pheromoneData)
        Gdx.gl31.glBindBuffer(GL31.GL_SHADER_STORAGE_BUFFER, 0)

        // Render pheromones into blurFbo (vignette will read from it)
        blurFbo.begin()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)

        shader.bind()
        shader.setUniformMatrix("u_projTrans", context.cameraProjection)
        shader.setUniformf("u_K", K)
        shader.setUniformf("u_P", P)

        Gdx.gl31.glBindBufferBase(GL31.GL_SHADER_STORAGE_BUFFER, 1, ssbo[0])

        mesh.bind(shader)
        Gdx.gl31.glDrawArraysInstanced(GL20.GL_TRIANGLE_STRIP, 0, 4, numInstances)
        mesh.unbind(shader)

        Gdx.gl.glDisable(GL20.GL_BLEND)

        blurFbo.end()

        // Update texture for next component (VignetteRenderer)
        context.currentTexture = blurFbo.colorBufferTexture
    }

    override fun dispose() {
        if (::shader.isInitialized) shader.dispose()
        if (::mesh.isInitialized) mesh.dispose()

        if (ssbo[0] != 0) {
            val buf = BufferUtils.newIntBuffer(1).apply {
                put(ssbo[0])
                flip()
            }
            Gdx.gl31.glDeleteBuffers(1, buf)
            ssbo[0] = 0
        }

        ssboCapacity = 0
    }
}