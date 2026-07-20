package io.github.some_example_name.old.systems.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import io.github.some_example_name.old.core.DIContext
import io.github.some_example_name.old.core.utils.drawTriangleMiddle
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.PheromoneEntity
import io.github.some_example_name.old.systems.pheromone.PheromoneShaderManager
import io.github.some_example_name.old.ui.screens.isRenderUi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class RenderSystem(
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val particleEntity: ParticleEntity,
    val shaderManager: ShaderManager,
//    val pheromoneShaderManager: PheromoneShaderManager,
    val renderBufferManager: RenderBufferManager,
    val diContext: DIContext,
    val pheromoneEntity: PheromoneEntity
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
//        pheromoneShaderManager.create()
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

    private var bufferCell = allocateBuffer(INITIAL_PARTICLE_CAPACITY)
    private var bufferPheromone = allocateBuffer(INITIAL_PHEROMONE_CAPACITY)

    fun resize(width: Int, height: Int) {
        shaderManager.resize(width, height)
    }

    fun render() {
        val cellBuf = renderBufferManager.getCurrentCellBuffer()
        val pheromoneBuf = renderBufferManager.getCurrentPheromoneBuffer()
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

        ensureCellBufferCapacityForWrite(particleEntity.aliveList.size)
        drawCellShader(cellBuf)

        ensurePheromoneBufferCapacityForWrite(pheromoneEntity.aliveList.size)
        if (usePostProcess) {
            drawPheromoneShader(pheromoneBuf)
        }
        if (!usePostProcess) {
            drawDebug(cellBuf, linkBuf, pheromoneBuf)
        }

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

    private fun ensureCellBufferCapacityForWrite(neededParticles: Int) {
        val currentCapacity = bufferCell.capacity() / PARTICLE_STRUCT_SIZE
        if (neededParticles <= currentCapacity) return

        var newCapacity = currentCapacity.toDouble()
        do { newCapacity *= 1.5 } while (newCapacity < neededParticles)

        val finalCapacity = newCapacity.toInt().coerceAtLeast(neededParticles)
        bufferCell = allocateBuffer(finalCapacity)
    }


    private fun ensurePheromoneBufferCapacityForWrite(neededPheromones: Int) {
        val currentCapacity = bufferPheromone.capacity() / PHEROMONE_STRUCT_SIZE
        if (neededPheromones <= currentCapacity) return

        var newCapacity = currentCapacity.toDouble()
        do { newCapacity *= 1.5 } while (newCapacity < neededPheromones)

        val finalCapacity = newCapacity.toInt().coerceAtLeast(neededPheromones)
        bufferPheromone = allocateBuffer(finalCapacity)
    }

    private fun drawPheromoneShader(pheromoneBuffer: PheromoneBufferData) {
        (bufferPheromone as java.nio.Buffer).clear()
        with(pheromoneBuffer) {
            for (i in 0..<pheromoneBufferSize) {
                bufferPheromone.putFloat(x[i])
                bufferPheromone.putFloat(y[i])
                bufferPheromone.putFloat(a[i])
                bufferPheromone.putInt(color[i])
            }
        }
        (bufferPheromone as java.nio.Buffer).flip()
//        pheromoneShaderManager.renderPheromones(camera.combined, bufferPheromone)
    }

    private fun drawCellShader(cellBuf: RenderCellBufferData) {
        (bufferCell as java.nio.Buffer).clear()
        with(cellBuf) {
            for (i in 0..<renderCellBufferSize) {
                bufferCell.putFloat(x[i])
                bufferCell.putFloat(y[i])
                bufferCell.putInt(color[i])
                bufferCell.putInt(packed1[i])
                bufferCell.putInt(packed2[i])
                bufferCell.putInt(0)
            }
        }
        (bufferCell as java.nio.Buffer).flip()

        val worldX = camera.position.x
        val worldY = camera.position.y
        shaderManager.render(
            currentRead = bufferCell,
            cameraProjection = camera.combined,
            isNewFrame = true,
            isClear = false,
            worldX = worldX,
            worldY = worldY,
            blurAmount = blurLevel,
            zoom = camera.zoom,
            vignetteEnabled = 1f
        )
    }

    fun drawDebug(cellBuf: RenderCellBufferData, linkBuf: RenderLinkBufferData, pheromoneBuffer: PheromoneBufferData) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        Gdx.gl.glLineWidth(2f)
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

//      Дебаг отрисовка феромона
        with(pheromoneBuffer) {
            for (i in 0..<pheromoneBufferSize) {
                shapeRenderer.circle(x[i], y[i], sqrt(radiusSquared[i]), 64)
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
                        0.55f,
                        64
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
        //TODO
    }

    companion object {
        const val INITIAL_PARTICLE_CAPACITY = 30_000
        const val INITIAL_PHEROMONE_CAPACITY = 1_000
        const val PARTICLE_STRUCT_SIZE = 24
        const val PHEROMONE_STRUCT_SIZE = 16
    }
}
