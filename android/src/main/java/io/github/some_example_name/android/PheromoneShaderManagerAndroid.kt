package io.github.some_example_name.android

import android.opengl.GLES20
import android.opengl.GLES32
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Matrix4
import io.github.some_example_name.old.systems.pheromone.PheromoneShaderManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import io.github.some_example_name.old.systems.pheromone.PheromonesManager.Companion.K
import io.github.some_example_name.old.systems.pheromone.PheromonesManager.Companion.P
import io.github.some_example_name.old.systems.render.RenderSystem.Companion.INITIAL_PHEROMONE_CAPACITY

class PheromoneShaderManagerAndroid: PheromoneShaderManager {

    private var program = 0
    private var vao = 0
    private var quadVbo = 0
    private var instanceVbo = 0

    private var uProjTransLoc = -1
    private var uKLoc = -1
    private var uPLoc = -1

    private var instanceCapacity = 0

    // Размер одной структуры в байтах: vec2 (8) + float (4) + uint (4) = 16
    private val STRUCT_SIZE = 16

    private val quadVertices = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f,  1f,
        1f,  1f
    )

    override fun create() {
        dispose()

        val vertSource = Gdx.files.internal("shaders/pheromone/pheromone_android.vert").readString()
        val fragSource = Gdx.files.internal("shaders/pheromone/pheromone.frag").readString()
        program = createProgram(vertSource, fragSource)

        uProjTransLoc = GLES32.glGetUniformLocation(program, "u_projTrans")
        uKLoc = GLES32.glGetUniformLocation(program, "u_K")
        uPLoc = GLES32.glGetUniformLocation(program, "u_P")

        setupVAO()
    }

    private fun setupVAO() {
        val tmp = IntArray(1)

        // VAO
        GLES32.glGenVertexArrays(1, tmp, 0)
        vao = tmp[0]
        GLES32.glBindVertexArray(vao)

        // Quad VBO (статический, 4 вершины)
        GLES32.glGenBuffers(1, tmp, 0)
        quadVbo = tmp[0]
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, quadVbo)

        val fb = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        fb.put(quadVertices).position(0)

        GLES32.glBufferData(
            GLES32.GL_ARRAY_BUFFER,
            quadVertices.size * 4,
            fb,
            GLES32.GL_STATIC_DRAW
        )

        GLES32.glVertexAttribPointer(0, 2, GLES32.GL_FLOAT, false, 0, 0)
        GLES32.glEnableVertexAttribArray(0)

        // Instance VBO (динамический)
        GLES32.glGenBuffers(1, tmp, 0)
        instanceVbo = tmp[0]
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, instanceVbo)

        // a_pos (location 1) — vec2
        GLES32.glVertexAttribPointer(1, 2, GLES32.GL_FLOAT, false, STRUCT_SIZE, 0)
        GLES32.glEnableVertexAttribArray(1)
        GLES32.glVertexAttribDivisor(1, 1)

        // a_A (location 2) — float
        GLES32.glVertexAttribPointer(2, 1, GLES32.GL_FLOAT, false, STRUCT_SIZE, 8)
        GLES32.glEnableVertexAttribArray(2)
        GLES32.glVertexAttribDivisor(2, 1)

        // a_color (location 3) — uint
        GLES32.glVertexAttribIPointer(3, 1, GLES32.GL_UNSIGNED_INT, STRUCT_SIZE, 12)
        GLES32.glEnableVertexAttribArray(3)
        GLES32.glVertexAttribDivisor(3, 1)

        GLES32.glBindVertexArray(0)
    }

    override fun renderPheromones(cameraProjection: Matrix4, currentRead: ByteBuffer) {
        val dataSize = currentRead.remaining()
        val numInstances = dataSize / STRUCT_SIZE
        if (numInstances == 0) return

        resizeInstanceBuffer(dataSize)

        GLES32.glBindVertexArray(vao)

        // Загружаем данные инстансов
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, instanceVbo)
        currentRead.mark()
        GLES32.glBufferSubData(GLES32.GL_ARRAY_BUFFER, 0, dataSize, currentRead)
        currentRead.reset()

        GLES32.glUseProgram(program)

        GLES32.glUniformMatrix4fv(uProjTransLoc, 1, false, cameraProjection.`val`, 0)
        GLES32.glUniform1f(uKLoc, K)
        GLES32.glUniform1f(uPLoc, P)

        GLES32.glEnable(GLES20.GL_BLEND)
        GLES32.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES32.glDrawArraysInstanced(GLES32.GL_TRIANGLE_STRIP, 0, 4, numInstances)

        GLES32.glDisable(GLES20.GL_BLEND)
        GLES32.glBindVertexArray(0)
    }

    private fun resizeInstanceBuffer(newSize: Int) {
        if (newSize <= instanceCapacity) return

        var cap = if (instanceCapacity == 0) {
            INITIAL_PHEROMONE_CAPACITY * STRUCT_SIZE
        } else {
            instanceCapacity
        }
        while (cap < newSize) cap = (cap * 1.5).toInt()

        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, instanceVbo)
        GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, cap, null, GLES32.GL_DYNAMIC_DRAW)
        instanceCapacity = cap
    }

    private fun createProgram(vertSource: String, fragSource: String): Int {
        val vertexShader = compileShader(GLES32.GL_VERTEX_SHADER, vertSource)
        val fragmentShader = compileShader(GLES32.GL_FRAGMENT_SHADER, fragSource)

        val prog = GLES32.glCreateProgram()
        GLES32.glAttachShader(prog, vertexShader)
        GLES32.glAttachShader(prog, fragmentShader)
        GLES32.glLinkProgram(prog)

        val status = IntArray(1)
        GLES32.glGetProgramiv(prog, GLES32.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            throw RuntimeException("Program link error: ${GLES32.glGetProgramInfoLog(prog)}")
        }

        GLES32.glDeleteShader(vertexShader)
        GLES32.glDeleteShader(fragmentShader)
        return prog
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES32.glCreateShader(type)
        GLES32.glShaderSource(shader, source)
        GLES32.glCompileShader(shader)

        val status = IntArray(1)
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            throw RuntimeException("Shader compile error: ${GLES32.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    override fun dispose() {
        if (program != 0) {
            GLES32.glDeleteProgram(program)
            program = 0
        }
        if (vao != 0) {
            GLES32.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            vao = 0
        }
        if (quadVbo != 0) {
            GLES32.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
            quadVbo = 0
        }
        if (instanceVbo != 0) {
            GLES32.glDeleteBuffers(1, intArrayOf(instanceVbo), 0)
            instanceVbo = 0
        }

        uProjTransLoc = -1
        uKLoc = -1
        uPLoc = -1
        instanceCapacity = 0
    }

    companion object {
        // Удобно, если хочешь задать шейдеры прямо в коде (или загружать из assets)
        fun loadShaderFromString(source: String): String = source
        // Пример вызова:
        // val vert = assets.open("shaders/pheromone/pheromone_pc.vert").bufferedReader().use { it.readText() }
    }
}
