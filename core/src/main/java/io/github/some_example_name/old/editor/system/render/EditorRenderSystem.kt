package io.github.some_example_name.old.editor.system.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.currentTick
import io.github.some_example_name.old.editor.entities.CellReplay
import io.github.some_example_name.old.editor.system.simulation.EditorSimulationSystem
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.systems.render.RenderSystem
import io.github.some_example_name.old.systems.render.ShaderManager
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

//TODO рендер слишком большой
class EditorRenderSystem(
    val shaderManager: ShaderManager,
    val cellReplay: CellReplay,
    val particleEntity: ParticleEntity,
    val editorSimulationSystem: EditorSimulationSystem,
    val drawingHelperElements: DrawingHelperElements
) {

    private lateinit var camera: OrthographicCamera

    fun create(
        shapeRenderer: ShapeRenderer,
        camera: OrthographicCamera
    ) {
        shaderManager.create()
        this.camera = camera
        drawingHelperElements.create(shapeRenderer, camera)
    }

    private var buffer = allocateBuffer(RenderSystem.Companion.INITIAL_PARTICLE_CAPACITY)
    var isUpdateBuffer = true

    private fun allocateBuffer(numParticles: Int): ByteBuffer {
        return ByteBuffer
            .allocateDirect(numParticles * RenderSystem.Companion.PARTICLE_STRUCT_SIZE)
            .order(ByteOrder.nativeOrder())
    }

    private fun ensureCapacityForWrite(neededParticles: Int) {
        val currentCapacity = buffer.capacity() / RenderSystem.Companion.PARTICLE_STRUCT_SIZE
        if (neededParticles + 10 <= currentCapacity) return

        var newCapacity = currentCapacity.toDouble()
        do { newCapacity *= 1.5 } while (newCapacity < neededParticles)

        val finalCapacity = newCapacity.toInt().coerceAtLeast(neededParticles)
        buffer = allocateBuffer(finalCapacity)
    }

    fun resize(width: Int, height: Int) {
        shaderManager.resize(width, height)
    }

    fun putBuffer(
        cos: Float,
        sin: Float,
        x: Float,
        y: Float,
        color: Int,
        radius: Float,
        cellType: Byte
    ) {
        val cosByte = ((cos * 0.5f + 0.5f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val sinByte = ((sin * 0.5f + 0.5f) * 255f + 0.5f).toInt().coerceIn(0, 255)

        val bRadius = (((radius - 0.05f) / 0.7f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val bEnergy = 0
        val bCell = cellType.toInt().coerceIn(0, 255)

        val packed1 = cosByte or (sinByte shl 8) or (bRadius shl 24)
        val packed2 = bEnergy or (bCell shl 8)


        buffer.putFloat(x)
        buffer.putFloat(y)
        buffer.putInt(color)
        buffer.putInt(packed1)
        buffer.putInt(packed2)
        // Pad to 32 bytes = 2× RGBA32UI texels (GLES 3.0 data-texture path)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
    }


    fun render(touchedCellX: Float, touchedCellY: Float) {
        if (isUpdateBuffer) {
            (buffer as Buffer).clear()
            cellReplay.forEachInTick(currentTick) { cellType, index, _, angleCos, angleSin, color ->
                putBuffer(
                    cos = angleCos,
                    sin = angleSin,
                    x = particleEntity.x[index],
                    y = particleEntity.y[index],
                    color = color,
                    radius = particleEntity.radius[index],
                    cellType = cellType
                )
            }

            val stage = currentTick
            val stageInstructions = editorSimulationSystem.genomeStageInstruction
            if (stage < stageInstructions.size) {
                val genomeStage = stageInstructions[stage]

                genomeStage.cellActions.forEach { cellActionId, action ->
                    val divide = action.divide
                    if (divide != null) {
                        val index = editorSimulationSystem.mapCellGenomeIdToIndex[divide.id]

                        val angle = divide.angle ?: 0f

                        putBuffer(
                            cos = cos(angle),
                            sin = sin(angle),
                            x = particleEntity.x[index],
                            y = particleEntity.y[index],
                            color = (divide.color ?: Color.WHITE).toIntBits(),
                            radius = particleEntity.radius[index],
                            cellType = 20
                        )
                    }
                }
            }
            (buffer as Buffer).flip()
        }

        shaderManager.render(
            currentRead = buffer,
            cameraProjection = camera.combined,
            isNewFrame = true,
            isClear = false,
            worldX = camera.position.x,
            worldY = camera.position.y,
            blurAmount = -0.04f,
            zoom = camera.zoom,
            vignetteEnabled = 0f
        )

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)

        drawingHelperElements.render(touchedCellX, touchedCellY)
    }

}
