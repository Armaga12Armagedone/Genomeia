package io.github.some_example_name.android

import android.opengl.GLES32
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Matrix4
import io.github.some_example_name.old.systems.render.RenderSystem.Companion.INITIAL_PARTICLE_CAPACITY
import io.github.some_example_name.old.systems.render.RenderSystem.Companion.PARTICLE_STRUCT_SIZE
import io.github.some_example_name.old.systems.render.ShaderManager
import io.github.some_example_name.old.systems.render.texturePaths
import io.github.some_example_name.old.systems.render.usePostProcess
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ShaderManagerAndroidApi : ShaderManager {

    // Particle VBO (вместо SSBO)
    private val particleVbo = IntArray(1)
    private var particleVboCapacity = INITIAL_PARTICLE_CAPACITY * PARTICLE_STRUCT_SIZE

    // Шейдеры
    private var particleProgram = 0
    private var sobelProgram = 0
    private var distortProgram = 0
    private var blurProgram = 0

    // Uniform locations
    private var particleProjLoc = 0
    private var particleTextureScaleLoc = 0
    private var particleColorScaleLoc = 0
    private var particleTextureArrayLoc = 0

    private var sobelTextureLoc = 0
    private var sobelResolutionLoc = 0
    private var sobelZoomLoc = 0
    private var vignetteEnabledLoc = 0

    private var distortTextureLoc = 0
    private var distortResolutionLoc = 0

    private var blurTextureLoc = 0
    private var blurAmountLoc = 0
    private var blurResolutionLoc = 0

    // Quad VAO + VBO (один на всё)
    private var quadVao = 0
    private var quadVbo = 0

    // Texture Array
    private var textureArray = 0
    private var numLayers = 0

    // FBOs
    private var sceneFbo = 0
    private var sceneColorTex = 0
    private var sceneDepthRbo = 0
    private var sceneFboWidth = 0
    private var sceneFboHeight = 0

    private var blurFbo = 0
    private var blurColorTex = 0
    private var blurFboWidth = 0
    private var blurFboHeight = 0

    private var distortFbo = 0
    private var distortColorTex = 0
    private var distortFboWidth = 0
    private var distortFboHeight = 0

    private val invProjMatrix = Matrix4()

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES32.glCreateShader(type)
        GLES32.glShaderSource(shader, source)
        GLES32.glCompileShader(shader)

        val status = IntArray(1)
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES32.glGetShaderInfoLog(shader)
            GLES32.glDeleteShader(shader)
            throw RuntimeException("Shader compile error: $log")
        }
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = compileShader(GLES32.GL_VERTEX_SHADER, vertexSource)
        val fs = compileShader(GLES32.GL_FRAGMENT_SHADER, fragmentSource)

        val program = GLES32.glCreateProgram()
        GLES32.glAttachShader(program, vs)
        GLES32.glAttachShader(program, fs)
        GLES32.glLinkProgram(program)

        val status = IntArray(1)
        GLES32.glGetProgramiv(program, GLES32.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES32.glGetProgramInfoLog(program)
            GLES32.glDeleteProgram(program)
            throw RuntimeException("Program link error: $log")
        }

        GLES32.glDeleteShader(vs)
        GLES32.glDeleteShader(fs)
        return program
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
                throw IllegalStateException("Все текстуры в TextureArray должны быть одного размера! (${width}×${height})")
            }
        }

        val buffer = IntArray(1)
        GLES32.glGenTextures(1, buffer, 0)
        textureArray = buffer[0]

        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D_ARRAY, textureArray)

        GLES32.glTexImage3D(
            GLES32.GL_TEXTURE_2D_ARRAY, 0, GLES32.GL_RGBA8,
            width, height, numLayers, 0,
            GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, null
        )

        for ((layer, pixmap) in pixmaps.withIndex()) {
            GLES32.glTexSubImage3D(
                GLES32.GL_TEXTURE_2D_ARRAY, 0,
                0, 0, layer,
                pixmap.width, pixmap.height, 1,
                GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE,
                pixmap.pixels
            )
            pixmap.dispose()
        }

        GLES32.glGenerateMipmap(GLES32.GL_TEXTURE_2D_ARRAY)

        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D_ARRAY, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR_MIPMAP_LINEAR)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D_ARRAY, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D_ARRAY, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_REPEAT)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D_ARRAY, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_REPEAT)

        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D_ARRAY, 0)

        println("✅ TextureArray создан (Android GLES): $numLayers слоёв, ${width}×${height} px")
    }

    private fun createParticleVbo() {
        GLES32.glGenBuffers(1, particleVbo, 0)
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, particleVbo[0])
        GLES32.glBufferData(
            GLES32.GL_ARRAY_BUFFER,
            INITIAL_PARTICLE_CAPACITY * PARTICLE_STRUCT_SIZE,
            null,
            GLES32.GL_DYNAMIC_DRAW
        )
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)
        println("✅ Particle VBO создан (Android GLES)")
    }

    private fun resizeParticleVbo(dataSize: Int) {
        if (dataSize > particleVboCapacity) {
            var newCapacity = particleVboCapacity.toDouble()
            do {
                newCapacity *= 1.5
            } while (newCapacity < dataSize)

            val finalCapacity = newCapacity.toInt().coerceAtLeast(dataSize)

            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, particleVbo[0])
            GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, finalCapacity, null, GLES32.GL_DYNAMIC_DRAW)
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)

            particleVboCapacity = finalCapacity
        }
    }

    private fun createQuadMesh() {
        val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

        val buf = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(vertices).position(0)

        val vao = IntArray(1)
        GLES32.glGenVertexArrays(1, vao, 0)
        quadVao = vao[0]

        GLES32.glBindVertexArray(quadVao)

        // Quad vertices (location 0)
        val vbo = IntArray(1)
        GLES32.glGenBuffers(1, vbo, 0)
        quadVbo = vbo[0]

        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, quadVbo)
        GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, vertices.size * 4, buf, GLES32.GL_STATIC_DRAW)

        GLES32.glEnableVertexAttribArray(0)
        GLES32.glVertexAttribPointer(0, 2, GLES32.GL_FLOAT, false, 0, 0)

        // === INSTANCE ATTRIBUTES (particle data) ===
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, particleVbo[0])
        val stride = PARTICLE_STRUCT_SIZE

        // location 1: vec2 pos          offset 0
        GLES32.glEnableVertexAttribArray(1)
        GLES32.glVertexAttribPointer(1, 2, GLES32.GL_FLOAT, false, stride, 0)
        GLES32.glVertexAttribDivisor(1, 1)

        // location 2: uint color        offset 8
        GLES32.glEnableVertexAttribArray(2)
        GLES32.glVertexAttribIPointer(2, 1, GLES32.GL_UNSIGNED_INT, stride, 8)
        GLES32.glVertexAttribDivisor(2, 1)

        // location 3: uint packed1      offset 12
        GLES32.glEnableVertexAttribArray(3)
        GLES32.glVertexAttribIPointer(3, 1, GLES32.GL_UNSIGNED_INT, stride, 12)
        GLES32.glVertexAttribDivisor(3, 1)

        // location 4: uint packed2      offset 16
        GLES32.glEnableVertexAttribArray(4)
        GLES32.glVertexAttribIPointer(4, 1, GLES32.GL_UNSIGNED_INT, stride, 16)
        GLES32.glVertexAttribDivisor(4, 1)

        GLES32.glBindVertexArray(0)
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)

        println("✅ Quad + Instance attributes VAO создан")
    }

    // === FBO функции (createSceneFbo, createBlurFbo, createDistortFbo, deleteFbos) ===
    // Оставьте их **точно такими же**, как у вас были — они не менялись.

    private fun createSceneFbo(width: Int, height: Int) {
        // Color texture
        val tex = IntArray(1)
        GLES32.glGenTextures(1, tex, 0)
        sceneColorTex = tex[0]

        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, sceneColorTex)
        GLES32.glTexImage2D(
            GLES32.GL_TEXTURE_2D, 0, GLES32.GL_RGBA8,
            width, height, 0,
            GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, null
        )
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, 0)

        // Depth renderbuffer
        val rbo = IntArray(1)
        GLES32.glGenRenderbuffers(1, rbo, 0)
        sceneDepthRbo = rbo[0]

        GLES32.glBindRenderbuffer(GLES32.GL_RENDERBUFFER, sceneDepthRbo)
        GLES32.glRenderbufferStorage(GLES32.GL_RENDERBUFFER, GLES32.GL_DEPTH_COMPONENT24, width, height)
        GLES32.glBindRenderbuffer(GLES32.GL_RENDERBUFFER, 0)

        // FBO
        val f = IntArray(1)
        GLES32.glGenFramebuffers(1, f, 0)
        sceneFbo = f[0]

        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, sceneFbo)
        GLES32.glFramebufferTexture2D(
            GLES32.GL_FRAMEBUFFER, GLES32.GL_COLOR_ATTACHMENT0,
            GLES32.GL_TEXTURE_2D, sceneColorTex, 0
        )
        GLES32.glFramebufferRenderbuffer(
            GLES32.GL_FRAMEBUFFER, GLES32.GL_DEPTH_ATTACHMENT,
            GLES32.GL_RENDERBUFFER, sceneDepthRbo
        )

        val status = GLES32.glCheckFramebufferStatus(GLES32.GL_FRAMEBUFFER)
        if (status != GLES32.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Scene FBO incomplete: $status")
        }
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
    }

    private fun createBlurFbo(width: Int, height: Int) {
        val tex = IntArray(1)
        GLES32.glGenTextures(1, tex, 0)
        blurColorTex = tex[0]

        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, blurColorTex)
        GLES32.glTexImage2D(
            GLES32.GL_TEXTURE_2D, 0, GLES32.GL_RGBA8,
            width, height, 0,
            GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, null
        )
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, 0)

        val f = IntArray(1)
        GLES32.glGenFramebuffers(1, f, 0)
        blurFbo = f[0]

        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, blurFbo)
        GLES32.glFramebufferTexture2D(
            GLES32.GL_FRAMEBUFFER, GLES32.GL_COLOR_ATTACHMENT0,
            GLES32.GL_TEXTURE_2D, blurColorTex, 0
        )

        val status = GLES32.glCheckFramebufferStatus(GLES32.GL_FRAMEBUFFER)
        if (status != GLES32.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Blur FBO incomplete: $status")
        }
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
    }

    private fun createDistortFbo(width: Int, height: Int) {
        val tex = IntArray(1)
        GLES32.glGenTextures(1, tex, 0)
        distortColorTex = tex[0]

        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, distortColorTex)
        GLES32.glTexImage2D(
            GLES32.GL_TEXTURE_2D, 0, GLES32.GL_RGBA8,
            width, height, 0,
            GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, null
        )
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, 0)

        val f = IntArray(1)
        GLES32.glGenFramebuffers(1, f, 0)
        distortFbo = f[0]

        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, distortFbo)
        GLES32.glFramebufferTexture2D(
            GLES32.GL_FRAMEBUFFER, GLES32.GL_COLOR_ATTACHMENT0,
            GLES32.GL_TEXTURE_2D, distortColorTex, 0
        )

        val status = GLES32.glCheckFramebufferStatus(GLES32.GL_FRAMEBUFFER)
        if (status != GLES32.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Distort FBO incomplete: $status")
        }
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
    }

    private fun deleteFbos() {
        if (sceneFbo != 0) {
            GLES32.glDeleteFramebuffers(1, intArrayOf(sceneFbo), 0)
            GLES32.glDeleteTextures(1, intArrayOf(sceneColorTex), 0)
            GLES32.glDeleteRenderbuffers(1, intArrayOf(sceneDepthRbo), 0)
            sceneFbo = 0
            sceneColorTex = 0
            sceneDepthRbo = 0
        }
        if (blurFbo != 0) {
            GLES32.glDeleteFramebuffers(1, intArrayOf(blurFbo), 0)
            GLES32.glDeleteTextures(1, intArrayOf(blurColorTex), 0)
            blurFbo = 0
            blurColorTex = 0
        }
        if (distortFbo != 0) {
            GLES32.glDeleteFramebuffers(1, intArrayOf(distortFbo), 0)
            GLES32.glDeleteTextures(1, intArrayOf(distortColorTex), 0)
            distortFbo = 0
            distortColorTex = 0
        }
    }

    override fun create() {
        // Шейдеры (точно как в LibGDX)
        val particleVert = Gdx.files.internal("shaders/debug/circle_android.vert").readString()
        val particleFrag = Gdx.files.internal("shaders/debug/circle.frag").readString()
        particleProgram = createProgram(particleVert, particleFrag)

        val sobelVert = Gdx.files.internal("shaders/post_process/post_process.vert").readString()
        val sobelFrag = Gdx.files.internal("shaders/post_process/post_process.frag").readString()
        sobelProgram = createProgram(sobelVert, sobelFrag)

        val distortVert = Gdx.files.internal("shaders/blur/blur.vert").readString()
        val distortFrag = Gdx.files.internal("shaders/blur/ca_distort.frag").readString()
        distortProgram = createProgram(distortVert, distortFrag)

        val blurVert = Gdx.files.internal("shaders/blur/blur.vert").readString()
        val blurFrag = Gdx.files.internal("shaders/blur/gaussian_blur.frag").readString()
        blurProgram = createProgram(blurVert, blurFrag)

        // Uniform locations (без изменений)
        particleProjLoc = GLES32.glGetUniformLocation(particleProgram, "u_projTrans")
        particleTextureScaleLoc = GLES32.glGetUniformLocation(particleProgram, "u_textureScale")
        particleColorScaleLoc = GLES32.glGetUniformLocation(particleProgram, "u_colorScale")
        particleTextureArrayLoc = GLES32.glGetUniformLocation(particleProgram, "u_textureArray")

        sobelTextureLoc = GLES32.glGetUniformLocation(sobelProgram, "u_texture")
        sobelResolutionLoc = GLES32.glGetUniformLocation(sobelProgram, "u_resolution")
        sobelZoomLoc = GLES32.glGetUniformLocation(sobelProgram, "u_zoom")
        vignetteEnabledLoc = GLES32.glGetUniformLocation(sobelProgram, "u_vignetteEnabled")

        distortTextureLoc = GLES32.glGetUniformLocation(distortProgram, "u_texture")
        distortResolutionLoc = GLES32.glGetUniformLocation(distortProgram, "u_resolution")

        blurTextureLoc = GLES32.glGetUniformLocation(blurProgram, "u_texture")
        blurAmountLoc = GLES32.glGetUniformLocation(blurProgram, "u_blurAmount")
        blurResolutionLoc = GLES32.glGetUniformLocation(blurProgram, "u_resolution")

        createParticleVbo()      // ← новый
        createQuadMesh()         // ← теперь создаёт instance attributes
        createTextureArray()

        val w = Gdx.graphics.width.coerceAtLeast(1)
        val h = Gdx.graphics.height.coerceAtLeast(1)

        createSceneFbo(w, h)
        sceneFboWidth = w
        sceneFboHeight = h

        createBlurFbo(w, h)
        blurFboWidth = w
        blurFboHeight = h

        createDistortFbo(w, h)
        distortFboWidth = w
        distortFboHeight = h

        GLES32.glViewport(0, 0, w, h)

        println("✅ ShaderManagerAndroidApi создан (instanced attributes, 100% совместим с Android)")
    }

    override fun resize(width: Int, height: Int) {
        // ... ваш оригинальный resize (FBO) без изменений ...
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)

        deleteFbos()

        createSceneFbo(safeW, safeH)
        sceneFboWidth = safeW
        sceneFboHeight = safeH

        createBlurFbo(safeW, safeH)
        blurFboWidth = safeW
        blurFboHeight = safeH

        createDistortFbo(safeW, safeH)
        distortFboWidth = safeW
        distortFboHeight = safeH

        GLES32.glViewport(0, 0, safeW, safeH)

        println("✅ FBOs resized (Android GLES)")
    }

    override fun render(
        currentRead: ByteBuffer,
        cameraProjection: Matrix4,
        isNewFrame: Boolean,
        isClear: Boolean,
        worldX: Float,
        worldY: Float,
        blurAmount: Float,
        zoom: Float,
        vignetteEnabled: Float
    ) {
        val dataSize = currentRead.remaining()
        val numInstances = dataSize / PARTICLE_STRUCT_SIZE

        if (isNewFrame && dataSize > 0) {
            resizeParticleVbo(dataSize)

            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, particleVbo[0])
            GLES32.glBufferSubData(GLES32.GL_ARRAY_BUFFER, 0, dataSize, currentRead)
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)
        }

        // ====================== РЕНДЕР ЧАСТИЦ ======================
        val targetFbo = if (usePostProcess) sceneFbo else 0
        val targetW = if (usePostProcess) sceneFboWidth else Gdx.graphics.width
        val targetH = if (usePostProcess) sceneFboHeight else Gdx.graphics.height

        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, targetFbo)
        GLES32.glViewport(0, 0, targetW, targetH)          // ← КРИТИЧНО!

        GLES32.glDisable(GLES32.GL_BLEND)
        GLES32.glEnable(GLES32.GL_DEPTH_TEST)
        GLES32.glDepthFunc(GLES32.GL_LESS)
        GLES32.glDepthMask(true)
        GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT or GLES32.GL_DEPTH_BUFFER_BIT)

        GLES32.glUseProgram(particleProgram)

        GLES32.glUniformMatrix4fv(particleProjLoc, 1, false, cameraProjection.`val`, 0)
        GLES32.glUniform1f(particleTextureScaleLoc, 1.0f)
        GLES32.glUniform1f(particleColorScaleLoc, if (usePostProcess) 0f else 0.5f)
        GLES32.glUniform1i(particleTextureArrayLoc, 0)

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D_ARRAY, textureArray)

        GLES32.glBindVertexArray(quadVao)
        GLES32.glDrawArraysInstanced(GLES32.GL_TRIANGLE_STRIP, 0, 4, numInstances)
        GLES32.glBindVertexArray(0)

        GLES32.glUseProgram(0)

        if (!usePostProcess) return

        // ====================== POST-PROCESS ======================
        // 1. Sobel → blurFbo
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, blurFbo)
        GLES32.glViewport(0, 0, blurFboWidth, blurFboHeight)   // ← важно!

        GLES32.glDisable(GLES32.GL_DEPTH_TEST)
        GLES32.glDisable(GLES32.GL_BLEND)

        GLES32.glUseProgram(sobelProgram)
        GLES32.glUniform1i(sobelTextureLoc, 0)
        GLES32.glUniform2f(sobelResolutionLoc, sceneFboWidth.toFloat(), sceneFboHeight.toFloat())
        val zoomX10 = zoom * 10f
        val sobel = zoomX10.coerceIn(0.16f, 0.24f)
        GLES32.glUniform1f(sobelZoomLoc, sobel)
        GLES32.glUniform1f(vignetteEnabledLoc, vignetteEnabled)

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, sceneColorTex)

        GLES32.glBindVertexArray(quadVao)
        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)
        GLES32.glBindVertexArray(0)

        // 2. Distort (CA) → distortFbo
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, distortFbo)
        GLES32.glViewport(0, 0, distortFboWidth, distortFboHeight)

        GLES32.glUseProgram(distortProgram)
        GLES32.glUniform1i(distortTextureLoc, 0)
        GLES32.glUniform2f(distortResolutionLoc, blurFboWidth.toFloat(), blurFboHeight.toFloat())

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, blurColorTex)

        GLES32.glBindVertexArray(quadVao)
        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)
        GLES32.glBindVertexArray(0)

        // 3. Gaussian Blur → экран
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
        GLES32.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)  // ← обратно на экран!

        GLES32.glUseProgram(blurProgram)
        GLES32.glUniform1i(blurTextureLoc, 0)
        GLES32.glUniform1f(blurAmountLoc, (blurAmount + 0.04f) * 0.5f)
        GLES32.glUniform2f(blurResolutionLoc, blurFboWidth.toFloat(), blurFboHeight.toFloat())

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, distortColorTex)

        GLES32.glBindVertexArray(quadVao)
        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)
        GLES32.glBindVertexArray(0)

        GLES32.glUseProgram(0)
    }

    override fun dispose() {
        GLES32.glDeleteProgram(particleProgram)
        GLES32.glDeleteProgram(sobelProgram)
        GLES32.glDeleteProgram(distortProgram)
        GLES32.glDeleteProgram(blurProgram)

        GLES32.glDeleteVertexArrays(1, intArrayOf(quadVao), 0)
        GLES32.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        GLES32.glDeleteBuffers(1, particleVbo, 0)

        if (textureArray != 0) {
            GLES32.glDeleteTextures(1, intArrayOf(textureArray), 0)
        }

        deleteFbos()

        println("✅ ShaderManagerAndroidApi disposed")
    }
}
