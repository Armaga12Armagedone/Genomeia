package io.github.some_example_name.old.systems.render.components

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.GL31
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.BufferUtils
import io.github.some_example_name.old.systems.render.RenderSystem.Companion.INITIAL_PARTICLE_CAPACITY
import io.github.some_example_name.old.systems.render.RenderSystem.Companion.PARTICLE_STRUCT_SIZE

/**
 * Renders particles using instanced drawing with SSBO data.
 * Manages texture array and SSBO buffers for particle data.
 */
class ParticleRenderer(private val texturePaths: List<String>) : RenderComponent {

    private val ssbos = IntArray(1)
    private var currentReadIndex = 0
    private val ssboCapacities = IntArray(1)

    private lateinit var shader: ShaderProgram
    private var textureArray: Int = 0
    private var numLayers: Int = 0

    override fun create() {
        createShader()
        createTextureArray()
        createSSBO()
    }

    private fun createShader() {
        val vertexShader = Gdx.files.internal("shaders/debug/circle_pc.vert").readString()
        val fragmentShader = Gdx.files.internal("shaders/debug/circle.frag").readString()
        shader = ShaderProgram(vertexShader, fragmentShader)
        if (!shader.isCompiled) {
            throw RuntimeException("Shader compilation failed: ${shader.log}")
        }
    }

    private fun createTextureArray() {
        numLayers = texturePaths.size
        if (numLayers == 0) throw IllegalStateException("Нет текстур для TextureArray!")

        val pixmaps = texturePaths.map { path ->
            val file = Gdx.files.internal(path)
            if (!file.exists()) throw IllegalArgumentException("Текстура не найдена: $path")
            Pixmap(file)
        }

        val width = pixmaps[0].width
        val height = pixmaps[0].height

        for (p in pixmaps) {
            if (p.width != width || p.height != height) {
                throw IllegalStateException(
                    "Все текстуры в TextureArray должны быть одного размера! (${width}×${height})"
                )
            }
        }

        val buffer = BufferUtils.newIntBuffer(1)
        Gdx.gl31.glGenTextures(1, buffer)
        textureArray = buffer.get(0)

        Gdx.gl31.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, textureArray)

        Gdx.gl31.glTexImage3D(
            GL30.GL_TEXTURE_2D_ARRAY, 0, GL30.GL_RGBA8,
            width, height, numLayers, 0,
            GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, null
        )

        for ((layer, pixmap) in pixmaps.withIndex()) {
            Gdx.gl31.glTexSubImage3D(
                GL30.GL_TEXTURE_2D_ARRAY, 0,
                0, 0, layer,
                pixmap.width, pixmap.height, 1,
                GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE,
                pixmap.getPixels()
            )
            pixmap.dispose()
        }

        Gdx.gl.glGenerateMipmap(GL30.GL_TEXTURE_2D_ARRAY)

        Gdx.gl31.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR_MIPMAP_LINEAR)
        Gdx.gl31.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR)
        Gdx.gl31.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_WRAP_S, GL30.GL_REPEAT)
        Gdx.gl31.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_WRAP_T, GL30.GL_REPEAT)

        Gdx.gl31.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0)

        println("✅ TextureArray создан: $numLayers слоёв, ${width}×${height} px")
    }

    private fun createSSBO() {
        val ssboBuffer = BufferUtils.newIntBuffer(1)
        Gdx.gl31.glGenBuffers(1, ssboBuffer)
        ssbos[0] = ssboBuffer.get(0)

        ssboCapacities[0] = INITIAL_PARTICLE_CAPACITY * PARTICLE_STRUCT_SIZE
        Gdx.gl31.glBindBuffer(GL31.GL_SHADER_STORAGE_BUFFER, ssbos[0])
        Gdx.gl31.glBufferData(
            GL31.GL_SHADER_STORAGE_BUFFER,
            INITIAL_PARTICLE_CAPACITY * PARTICLE_STRUCT_SIZE,
            null,
            GL20.GL_DYNAMIC_DRAW
        )
        Gdx.gl31.glBindBufferBase(GL31.GL_SHADER_STORAGE_BUFFER, 0, ssbos[0])
        Gdx.gl31.glBindBuffer(GL31.GL_SHADER_STORAGE_BUFFER, 0)
    }

    private fun resizeSSBO(dataSize: Int, targetIndex: Int, ssboId: Int) {
        if (dataSize > ssboCapacities[targetIndex]) {
            var newCapacity = ssboCapacities[targetIndex].toDouble()
            do {
                newCapacity *= 1.5
            } while (newCapacity < dataSize)

            val finalCapacity = newCapacity.toInt().coerceAtLeast(dataSize)

            Gdx.gl31.glBindBuffer(GL31.GL_SHADER_STORAGE_BUFFER, ssboId)
            Gdx.gl31.glBufferData(GL31.GL_SHADER_STORAGE_BUFFER, finalCapacity, null, GL20.GL_DYNAMIC_DRAW)
            Gdx.gl31.glBindBuffer(GL31.GL_SHADER_STORAGE_BUFFER, 0)

            ssboCapacities[targetIndex] = finalCapacity
        }
    }

    override fun resize(width: Int, height: Int) {
        // ParticleRenderer doesn't have size-dependent resources
    }

    override fun render(context: RenderContext) {
        val dataSize = context.particleData.remaining()

        if (context.isNewFrame && dataSize > 0) {
            val writeIndex = 0
            resizeSSBO(dataSize, writeIndex, ssbos[writeIndex])

            Gdx.gl31.glBindBuffer(GL31.GL_SHADER_STORAGE_BUFFER, ssbos[writeIndex])
            Gdx.gl31.glBufferSubData(GL31.GL_SHADER_STORAGE_BUFFER, 0, dataSize, context.particleData)
            Gdx.gl31.glBindBuffer(GL31.GL_SHADER_STORAGE_BUFFER, 0)

            currentReadIndex = writeIndex
        }

        if (context.usePostProcess) {
            context.sceneFbo?.begin()
        }

        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        shader.bind()
        shader.setUniformMatrix("u_projTrans", context.cameraProjection)
        shader.setUniformf("u_textureScale", 1.0f)
        shader.setUniformf("u_colorScale", if (context.usePostProcess) 0.0f else 1.0f)
        shader.setUniformi("u_textureArray", 0)

        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        Gdx.gl31.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, textureArray)

        context.fullscreenMesh.bind(shader)
        Gdx.gl31.glDrawArraysInstanced(GL20.GL_TRIANGLE_STRIP, 0, 4, context.numInstances)
        context.fullscreenMesh.unbind(shader)

        if (context.usePostProcess) {
            context.sceneFbo?.end()
            context.currentTexture = context.sceneFbo?.colorBufferTexture
        }
    }

    override fun dispose() {
        shader.dispose()

        if (textureArray != 0) {
            val deleteBuf = BufferUtils.newIntBuffer(1).apply {
                put(textureArray)
                flip()
            }
            Gdx.gl31.glDeleteTextures(1, deleteBuf)
            textureArray = 0
        }

        val deleteBuffer = BufferUtils.newIntBuffer(2).apply {
            put(ssbos[0])
            put(0) // Padding for array size
            flip()
        }
        Gdx.gl31.glDeleteBuffers(1, deleteBuffer)
    }
}
