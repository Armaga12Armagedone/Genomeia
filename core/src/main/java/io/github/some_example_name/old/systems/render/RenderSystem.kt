package io.github.some_example_name.old.systems.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import io.github.some_example_name.old.core.DIContext
import io.github.some_example_name.old.core.utils.drawTriangleMiddle
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.ui.screens.isRenderUi
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RenderSystem(
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val particleEntity: ParticleEntity,
    val shaderManager: ShaderManager,
    val renderBufferManager: RenderBufferManager,
    val diContext: DIContext
) {

    private lateinit var fontMatrix: Matrix4
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var camera: OrthographicCamera

    private var zoom = 0f
    private var cameraX = 0f
    private var cameraY = 0f
    private var blurLevel = 0f

    fun create(
        fontMatrix: Matrix4,
        spriteBatch: SpriteBatch,
        font: BitmapFont,
        shapeRenderer: ShapeRenderer,
        camera: OrthographicCamera
    ) {
        shaderManager.create()
        this.fontMatrix = fontMatrix
        this.spriteBatch = spriteBatch
        this.font = font
        this.shapeRenderer = shapeRenderer
        this.camera = camera
    }

    fun moveCamera(dx: Float, dy: Float) {
        camera.position.x += dx
        camera.position.y += dy
        camera.update()
    }

    private var buffer = allocateBuffer(INITIAL_PARTICLE_CAPACITY)

    fun resize(width: Int, height: Int) {
        shaderManager.resize(width, height)
    }

    fun render() {
        val cellBuf = renderBufferManager.getCurrentCellBuffer()
        val linkBuf = renderBufferManager.getCurrentLinkBuffer()
        val spec = renderBufferManager.getCurrentSpecificBufferData()
        if (zoom != camera.zoom || cameraX != camera.position.x || cameraY != camera.position.y) {
            if (!spec.isCellSelected) {
                blurLevel = 4.0f
                cameraX = camera.position.x
                cameraY = camera.position.y
                zoom = camera.zoom
            }
        }
        ensureCapacityForWrite(particleEntity.aliveList.size)
        drawShader(cellBuf, linkBuf)

        moveCameraAndDrawSelected(spec)

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)


        if (isRenderUi) {
            drawTextSimInfo(spec)
        }

        if (blurLevel > 0) {
            blurLevel -= 0.09f
        }
    }

    private fun allocateBuffer(numParticles: Int): ByteBuffer {
        return ByteBuffer
            .allocateDirect(numParticles * PARTICLE_STRUCT_SIZE)
            .order(ByteOrder.nativeOrder())
    }

    private fun ensureCapacityForWrite(neededParticles: Int) {
        val currentCapacity = buffer.capacity() / PARTICLE_STRUCT_SIZE
        if (neededParticles <= currentCapacity) return

        var newCapacity = currentCapacity.toDouble()
        do { newCapacity *= 1.5 } while (newCapacity < neededParticles)

        val finalCapacity = newCapacity.toInt().coerceAtLeast(neededParticles)
        buffer = allocateBuffer(finalCapacity)
    }

    private fun drawShader(cellBuf: RenderCellBufferData, linkBuf: RenderLinkBufferData) {
        buffer.clear()
        with(cellBuf) {
            for (i in 0..<renderCellBufferSize) {
                buffer.putFloat(x[i])
                buffer.putFloat(y[i])
                buffer.putInt(color[i])
                buffer.putInt(packed1[i])
                buffer.putInt(packed2[i])
                buffer.putInt(0)
            }
        }
        buffer.flip()

        val worldX = camera.position.x
        val worldY = camera.position.y
        shaderManager.render(
            currentRead = buffer,
            cameraProjection = camera.combined,
            isNewFrame = true,
            isClear = false,
            worldX = worldX,
            worldY = worldY,
            blurAmount = blurLevel,
            zoom = camera.zoom
        )

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
//
        Gdx.gl.glLineWidth(2f)
        shapeRenderer.color = Color.WHITE
        if (isRenderUi) {
            shapeRenderer.rect(
                0f,
                0f,
                diContext.gridWidth.toFloat(),
                diContext.gridHeight.toFloat()
            )
        }

        if (!usePostProcess) {
            with(cellBuf) {
                shapeRenderer.color = Color.WHITE
                for (i in 0..<renderCellBufferSize) {
                    if (directedAngleCos[i] != 0f || directedAngleSin[i] != 0f) {
                        shapeRenderer.line(
                            x[i],
                            y[i],
                            x[i] + directedAngleCos[i],
                            y[i] + directedAngleSin[i]
                        )
                    }
                }
            }

            shapeRenderer.color = Color.GREEN

            with(linkBuf) {
                for (linkId in 0..<renderLinkAmount) {

                    val cellAIndex = cellA[linkId]
                    val cellBIndex = cellB[linkId]
                    shapeRenderer.color = when (isNeuralDirected[linkId].toInt()) {
                        0, 1 -> Color.CYAN
                        -1 -> Color.GREEN
                        3 -> Color.PURPLE
                        else -> Color.RED
                    }

                    if ((isNeuralDirected[linkId].toInt() == 0)) {
                        shapeRenderer.drawTriangleMiddle(
                            cellBuf.x[cellAIndex],
                            cellBuf.y[cellAIndex],
                            cellBuf.x[cellBIndex],
                            cellBuf.y[cellBIndex],
                            arrowSize = 0.1f
                        )
                    } else if ((isNeuralDirected[linkId].toInt() == 1)) {
                        shapeRenderer.drawTriangleMiddle(
                            cellBuf.x[cellBIndex],
                            cellBuf.y[cellBIndex],
                            cellBuf.x[cellAIndex],
                            cellBuf.y[cellAIndex],
                            arrowSize = 0.1f
                        )
                    }

                    shapeRenderer.line(
                        cellBuf.x[cellAIndex],
                        cellBuf.y[cellAIndex],
                        cellBuf.x[cellBIndex],
                        cellBuf.y[cellBIndex],
                    )
                }
            }
        }
        shapeRenderer.end()
    }

    private fun moveCameraAndDrawSelected(spec: RenderSpecificBufferData) = with(renderBufferManager) {
        if (spec.isCellSelected) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

            shapeRenderer.color = Color.GOLD
            Gdx.gl.glLineWidth(5f)

            with(renderBufferManager) {
                if (spec.isCellSelected) {
                    shapeRenderer.circle(
                        spec.grabbedCellX ?: 0f,
                        spec.grabbedCellY ?: 0f,
                        0.55f
                    )
                }
            }

            shapeRenderer.end()

            val targetX = spec.grabbedCellX ?: return
            val targetY = spec.grabbedCellY ?: return

            val lerpSpeed = 1f
            val delta = Gdx.graphics.deltaTime

            camera.position.x += (targetX - camera.position.x) * lerpSpeed * delta
            camera.position.y += (targetY - camera.position.y) * lerpSpeed * delta

            camera.update()
        }
    }

    private fun drawTextSimInfo(spec: RenderSpecificBufferData) = with(renderBufferManager) {
        spriteBatch.begin()
        font.draw(
            spriteBatch,
            """
                    FPS: ${Gdx.graphics.framesPerSecond}
                    UPS: ${spec.ups}
                    Update Time: ${spec.updateTime} ms
                    Cells: ${spec.cellsAmount}
                    Particles: ${spec.particleAmount}
                    Links ${spec.linksAmount}
                    NeuronImpulseInput ${spec.neuronImpulseInput}
                    NeuronImpulseOutput ${spec.neuronImpulseOutput}
                    Cell type ${spec.cellName}
                    Selected cell index ${spec.selectedCellIndex}
                """.trimIndent(),
            30f,
            200f
        )
        font.data.setScale(1f)
        spriteBatch.end()
    }

    fun dispose() {

    }

    companion object {
        const val INITIAL_PARTICLE_CAPACITY = 30_000
        const val BYTE_SIZE = 4
        const val PARTICLE_PROPERTIES_AMOUNT = 5
        const val PARTICLE_STRUCT_SIZE = 24
    }
}
