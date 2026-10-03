package io.github.some_example_name.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import io.github.some_example_name.render.components.BlurPostProcessRenderer
import io.github.some_example_name.render.components.DistortRenderer
import io.github.some_example_name.render.components.ParticleRenderer
import io.github.some_example_name.render.components.PheromoneRenderer
import io.github.some_example_name.render.components.PostProcessRenderer
import io.github.some_example_name.render.components.RenderComponent
import io.github.some_example_name.render.components.RenderContext
import io.github.some_example_name.render.components.VignetteRenderer
import io.github.some_example_name.render.pack.CellInstanceBuffer
import io.github.some_example_name.render.pack.PheromoneInstanceBuffer

/**
 * Единственная точка входа модуля рендера: отдаёшь [RenderFrame] — получаешь картинку.
 *
 * Конвейер выполняется по порядку:
 *  1. ParticleRenderer      — клетки инстансингом в scene FBO
 *  2. PostProcessRenderer   — собель и пастельная стилизация
 *  3. DistortRenderer       — хроматическая аберрация
 *  4. BlurPostProcessRenderer — гауссов блюр
 *  5. PheromoneRenderer     — феромоны поверх
 *  6. VignetteRenderer      — виньетка, единственный проход прямо в экран
 *
 * Порядок задаётся списком [components] — чтобы вставить свой проход, достаточно
 * добавить [RenderComponent] в нужную позицию.
 *
 * ПРО ВЛАДЕНИЕ И ЖИЗНЕННЫЙ ЦИКЛ
 * -----------------------------
 * Экземпляр владеет ВСЕМИ своими GL-ресурсами: меш, три FBO, шейдеры и текстуры внутри
 * компонентов. Отсюда правило: у кого экземпляр — тот и зовёт [dispose], и только он.
 *
 * Раньше это правило было нарушено, и молча. Один ShaderManager лежал синглтоном в
 * DIGameGlobalContainer, а пользовались им три экрана сразу — меню, симуляция и редактор
 * генома. Диспозить его при закрытии одного экрана было нельзя, потому что два других
 * продолжали бы рисовать уже удалёнными шейдерами; в итоге RenderSystem.dispose() был
 * пустым TODO, и ресурсы не освобождались вообще никогда. Пока экземпляр один на игру,
 * это ничего не стоит, но именно эта конструкция и мешала завести второй.
 *
 * Требуется живой GL-контекст: [create] дёргается из конструктора, так что создавать
 * объект можно только после инициализации графики.
 */
class WorldRenderer(
    /**
     * Слои TextureArray по порядку: тип клетки — это индекс в этом списке.
     *
     * Публичный, потому что порядок нужен снимку сцены (RenderSceneDump): без него дамп
     * открылся бы с чужими текстурами. Снаружи список только читают.
     */
    val texturePaths: List<String>
) {

    // Общие ресурсы
    private lateinit var mesh: Mesh
    private lateinit var fbo: FrameBuffer
    private lateinit var blurFbo: FrameBuffer
    private lateinit var distortFbo: FrameBuffer

    /** Переиспользуется между кадрами: на кадр не должно быть ни одной аллокации. */
    private val renderContext = RenderContext()

    private val components = mutableListOf<RenderComponent>()

    var currentWidth = Gdx.graphics.width.coerceAtLeast(1)
        private set
    var currentHeight = Gdx.graphics.height.coerceAtLeast(1)
        private set

    init {
        create()
    }

    /**
     * Пересоздать FBO, если окно изменилось мимо [resize].
     *
     * Нужно потому, что экземпляр переживает смену экранов: пока играли в симуляцию и
     * ресайзили окно, редактор генома об этом не знал.
     */
    fun checkResize() {
        val width = Gdx.graphics.width.coerceAtLeast(1)
        val height = Gdx.graphics.height.coerceAtLeast(1)
        if (currentWidth != width || currentHeight != height) {
            resize(width, height)
        }
    }

    private fun create() {
        createFullscreenMesh()
        createFBOs()

        // Порядок в списке = порядок проходов.
        components.add(ParticleRenderer(texturePaths))
        components.add(PostProcessRenderer())
        components.add(DistortRenderer())
        components.add(BlurPostProcessRenderer())
        components.add(PheromoneRenderer())
        components.add(VignetteRenderer())

        components.forEach { it.create() }
    }

    private fun createFullscreenMesh() {
        val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val attributes = VertexAttributes(
            VertexAttribute(
                VertexAttributes.Usage.Position,
                2,
                ShaderProgram.POSITION_ATTRIBUTE
            )
        )
        mesh = Mesh(false, 4, 0, attributes).apply { setVertices(vertices) }
    }

    private fun createFBOs() {
        val width = Gdx.graphics.width.coerceAtLeast(1)
        val height = Gdx.graphics.height.coerceAtLeast(1)

        fbo = FrameBuffer(Pixmap.Format.RGBA8888, width, height, true)
        println("✅ FBO создан: ${width}×${height} (для пост-процессинга)")

        blurFbo = FrameBuffer(Pixmap.Format.RGBA8888, width, height, false)
        blurFbo.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        fbo.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)

        distortFbo = FrameBuffer(Pixmap.Format.RGBA8888, width, height, false)
        distortFbo.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)

        currentWidth = width
        currentHeight = height
    }

    fun resize(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)

        if (::fbo.isInitialized) fbo.dispose()
        fbo = FrameBuffer(Pixmap.Format.RGBA8888, safeW, safeH, true)

        if (::blurFbo.isInitialized) blurFbo.dispose()
        blurFbo = FrameBuffer(Pixmap.Format.RGBA8888, safeW, safeH, false)

        if (::distortFbo.isInitialized) distortFbo.dispose()
        distortFbo = FrameBuffer(Pixmap.Format.RGBA8888, safeW, safeH, false)

        // Linear filtering for smooth sampling
        fbo.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        blurFbo.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        distortFbo.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)

        currentWidth = safeW
        currentHeight = safeH

        println("✅ FBOs resized → scene: ${safeW}×${safeH}, blur: ${safeW}×${safeH}")

        components.forEach { it.resize(safeW, safeH) }
    }

    fun render(frame: RenderFrame) {
        val cells = frame.cells

        renderContext.apply {
            fullscreenMesh = mesh
            cameraProjection = frame.cameraProjection

            particleData = cells
            numInstances = cells?.let { it.remaining() / CellInstanceBuffer.STRUCT_SIZE } ?: 0
            isNewFrame = frame.uploadCells

            pheromoneData = frame.pheromones
            numPheromoneInstances =
                frame.pheromones?.let { it.remaining() / PheromoneInstanceBuffer.STRUCT_SIZE } ?: 0
            pheromoneK = frame.pheromoneK
            pheromoneP = frame.pheromoneP

            blurAmount = frame.blurAmount
            zoom = frame.zoom
            vignetteEnabled = frame.vignetteEnabled
            usePostProcess = frame.usePostProcess

            sceneFbo = fbo
            this.blurFbo = this@WorldRenderer.blurFbo
            this.distortFbo = this@WorldRenderer.distortFbo
            currentTexture = null
        }

        components.forEach { component ->
            component.render(renderContext)
        }

        Gdx.gl.glUseProgram(0)
    }

    /**
     * Квадратное превью кадра мира размером [size]×[size], строки сверху вниз.
     *
     * Звать сразу после [render] в том же кадре, до интерфейса: в бэкбуфере сейчас ровно
     * то, что нарисовали проходы конвейера, без кнопок и текста поверх. Берётся центральный
     * квадрат кадра, и он уменьшается усреднением по блокам, а не выборкой через пиксель:
     * при уменьшении в 8-10 раз выборка дала бы мерцающую «лесенку» на границах клеток.
     *
     * Pixmap принадлежит вызывающему.
     */
    fun capturePreview(size: Int): Pixmap {
        val width = Gdx.graphics.backBufferWidth
        val height = Gdx.graphics.backBufferHeight
        val side = minOf(width, height)
        val frame = Pixmap.createFromFrameBuffer((width - side) / 2, (height - side) / 2, side, side)
        try {
            return downscaleFlipped(frame, side, size)
        } finally {
            frame.dispose()
        }
    }

    private fun downscaleFlipped(frame: Pixmap, side: Int, size: Int): Pixmap {
        val preview = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val src = frame.pixels
        val dst = preview.pixels

        for (outY in 0 until size) {
            val y0 = outY * side / size
            val y1 = maxOf(y0 + 1, (outY + 1) * side / size)
            // Кадровый буфер читается снизу вверх, превью пишется сверху вниз.
            val dstRow = (size - 1 - outY) * size

            for (outX in 0 until size) {
                val x0 = outX * side / size
                val x1 = maxOf(x0 + 1, (outX + 1) * side / size)

                var r = 0
                var g = 0
                var b = 0
                for (y in y0 until y1) {
                    var offset = (y * side + x0) * 4
                    for (x in x0 until x1) {
                        r += src.get(offset).toInt() and 0xFF
                        g += src.get(offset + 1).toInt() and 0xFF
                        b += src.get(offset + 2).toInt() and 0xFF
                        offset += 4
                    }
                }
                val count = (y1 - y0) * (x1 - x0)
                val out = (dstRow + outX) * 4
                dst.put(out, (r / count).toByte())
                dst.put(out + 1, (g / count).toByte())
                dst.put(out + 2, (b / count).toByte())
                // Альфа в бэкбуфере после смешивания проходов не обязана быть единицей,
                // а прозрачное превью в PNG выглядело бы дырой.
                dst.put(out + 3, 0xFF.toByte())
            }
        }
        return preview
    }

    fun dispose() {
        components.forEach { it.dispose() }
        components.clear()

        if (::mesh.isInitialized) mesh.dispose()

        if (::fbo.isInitialized) fbo.dispose()
        if (::blurFbo.isInitialized) blurFbo.dispose()
        if (::distortFbo.isInitialized) distortFbo.dispose()
    }

    // === Управление конвейером ===

    /** Добавить проход в конец конвейера. */
    fun addComponent(component: RenderComponent) {
        component.create()
        components.add(component)
    }

    /** Вставить проход на позицию [index] (0 — первым). */
    fun insertComponent(index: Int, component: RenderComponent) {
        component.create()
        components.add(index, component)
    }

    fun removeComponent(component: RenderComponent) {
        if (components.remove(component)) {
            component.dispose()
        }
    }

    fun getComponentCount(): Int = components.size
}
