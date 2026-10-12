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
import io.github.some_example_name.render.components.CellGeometry
import io.github.some_example_name.render.components.DistortRenderer
import io.github.some_example_name.render.components.NostalgicRenderer
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
 *  1. ParticleRenderer      — клетки инстансингом в scene FBO (в отладке — прямо в экран)
 *  2. NostalgicRenderer     — клетки ностальгического режима, сам выводит кадр на экран
 *  3. PostProcessRenderer   — собель и пастельная стилизация
 *  4. DistortRenderer       — хроматическая аберрация
 *  5. BlurPostProcessRenderer — гауссов блюр
 *  6. PheromoneRenderer     — феромоны поверх
 *  7. VignetteRenderer      — виньетка, единственный проход прямо в экран
 *
 * Какие из них работают, решает [RenderFrame.mode]: проход, которому в этом режиме делать
 * нечего, сразу возвращается. В реалистичном режиме идут все, кроме второго; в отладочном
 * — только первый; в ностальгическом — только второй.
 *
 * Порядок задаётся списком [components] — чтобы вставить свой проход, достаточно
 * добавить [RenderComponent] в нужную позицию.
 *
 * Клетки заливаются на GPU один раз за кадр, до всех проходов, в общую [CellGeometry] —
 * её рисуют и ParticleRenderer, и NostalgicRenderer.
 *
 * ПРО ВЛАДЕНИЕ И ЖИЗНЕННЫЙ ЦИКЛ
 * -----------------------------
 * Экземпляр владеет ВСЕМИ своими GL-ресурсами: меш, геометрия клеток, три FBO, шейдеры
 * и текстуры внутри компонентов. Отсюда правило: у кого экземпляр — тот и зовёт
 * [dispose], и только он.
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
    private val cellGeometry = CellGeometry()
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
        cellGeometry.create()

        // Порядок в списке = порядок проходов.
        components.add(ParticleRenderer(texturePaths, cellGeometry))
        components.add(NostalgicRenderer(cellGeometry))
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

        fbo = createSceneFbo(width, height)
        println("✅ FBO создан: ${width}×${height} (для пост-процессинга)")

        blurFbo = FrameBuffer(Pixmap.Format.RGBA8888, width, height, false)
        blurFbo.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        fbo.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)

        distortFbo = FrameBuffer(Pixmap.Format.RGBA8888, width, height, false)
        distortFbo.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)

        currentWidth = width
        currentHeight = height
    }

    /**
     * Scene FBO — с глубиной И stencil.
     *
     * Stencil нужен ностальгическому режиму: по нему считается контур (NostalgicRenderer).
     * Остальные режимы его не трогают — тест stencil у них выключен. Глубина с ним
     * становится пакованной D24S8 вместо отдельного D16, то есть заодно точнее; лишняя
     * память — два байта на пиксель.
     */
    private fun createSceneFbo(width: Int, height: Int): FrameBuffer =
        FrameBuffer(Pixmap.Format.RGBA8888, width, height, true, true)

    fun resize(width: Int, height: Int) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)

        if (::fbo.isInitialized) fbo.dispose()
        fbo = createSceneFbo(safeW, safeH)

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

        // Один раз за кадр и до всех проходов: сколько бы их ни рисовало клетки, байты
        // едут на GPU единожды.
        if (frame.uploadCells && cells != null) {
            cellGeometry.upload(cells)
        }

        renderContext.apply {
            fullscreenMesh = mesh
            cameraProjection = frame.cameraProjection

            numInstances = cells?.let { it.remaining() / CellInstanceBuffer.STRUCT_SIZE } ?: 0

            pheromoneData = frame.pheromones
            numPheromoneInstances =
                frame.pheromones?.let { it.remaining() / PheromoneInstanceBuffer.STRUCT_SIZE } ?: 0
            pheromoneK = frame.pheromoneK
            pheromoneP = frame.pheromoneP

            blurAmount = frame.blurAmount
            zoom = frame.zoom
            vignetteEnabled = frame.vignetteEnabled
            mode = frame.mode

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

    fun dispose() {
        components.forEach { it.dispose() }
        components.clear()

        cellGeometry.dispose()
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
