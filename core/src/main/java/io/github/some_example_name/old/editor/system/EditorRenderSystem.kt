package io.github.some_example_name.old.editor.system

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import io.github.some_example_name.old.core.DIGenomeEditorContainer.gridHeight
import io.github.some_example_name.old.core.DIGenomeEditorContainer.gridWidth
import io.github.some_example_name.old.core.utils.drawArrowWithRotationAngle
import io.github.some_example_name.old.core.utils.drawTriangleMiddle
import io.github.some_example_name.old.editor.entities.CellReplay
import io.github.some_example_name.old.editor.entities.LinkReplay
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.systems.render.RenderSystem.Companion.INITIAL_PARTICLE_CAPACITY
import io.github.some_example_name.old.systems.render.RenderSystem.Companion.PARTICLE_STRUCT_SIZE
import io.github.some_example_name.old.systems.render.ShaderManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class EditorRenderSystem(
    val shaderManager: ShaderManager,
    val cellReplay: CellReplay,
    val linkReplay: LinkReplay,
    val editorLogicSystem: EditorLogicSystem,
    val cellEntity: CellEntity,
    val particleEntity: ParticleEntity,
    val editorSimulationSystem: EditorSimulationSystem,
    val linkEntity: LinkEntity,
    val symmetryManager: SymmetryManager
) {

    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var camera: OrthographicCamera
    var showPhysicalLink = true

    fun create(
        shapeRenderer: ShapeRenderer,
        camera: OrthographicCamera
    ) {
        shaderManager.create()
        this.shapeRenderer = shapeRenderer
        this.camera = camera
    }

    private var buffer = allocateBuffer(INITIAL_PARTICLE_CAPACITY)
    var isUpdateBuffer = true

    private fun allocateBuffer(numParticles: Int): ByteBuffer {
        return ByteBuffer
            .allocateDirect(numParticles * PARTICLE_STRUCT_SIZE)
            .order(ByteOrder.nativeOrder())
    }

    private fun ensureCapacityForWrite(neededParticles: Int) {
        val currentCapacity = buffer.capacity() / PARTICLE_STRUCT_SIZE
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

        val bRadius = (((radius - 0.1f) / 0.4f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val bEnergy = 0
        val bCell = cellType.toInt().coerceIn(0, 255)

        val packed1 = cosByte or (sinByte shl 8) or (bRadius shl 24)
        val packed2 = bEnergy or (bCell shl 8)


        buffer.putFloat(x)
        buffer.putFloat(y)
        buffer.putInt(color)
        buffer.putInt(packed1)
        buffer.putInt(packed2)
        buffer.putInt(0)
    }

    fun render() {
        if (isUpdateBuffer) {
            (buffer as java.nio.Buffer).clear()
            cellReplay.forEachInTick(editorLogicSystem.currentTick) { cellType, index, _, angleCos, angleSin, color ->
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

            val stage = editorLogicSystem.currentStage
            val stageInstructions = editorSimulationSystem.genome.genomeStageInstruction
            if (stage < stageInstructions.size) {
                val genomeStage = editorSimulationSystem.genome.genomeStageInstruction[stage]

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
            (buffer as java.nio.Buffer).flip()
        }

        val worldX = camera.position.x
        val worldY = camera.position.y
        shaderManager.render(
            currentRead = buffer,
            cameraProjection = camera.combined,
            isNewFrame = true,
            isClear = false,
            worldX = worldX,
            worldY = worldY,
            blurAmount = -0.04f,
            zoom = camera.zoom,
            vignetteEnabled = 0f
        )

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)

        shapeRenderer.color = Color.WHITE
        shapeRenderer.projectionMatrix = camera.combined

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.rect(
            0f,
            0f,
            gridWidth.toFloat(),
            gridHeight.toFloat()
        )

        symmetryManager.drawSymmetry(shapeRenderer)

        Gdx.gl.glLineWidth(2f)

        val nextStageTick = editorSimulationSystem.tickByStage[(editorLogicSystem.currentStage + 1).coerceIn(0, editorLogicSystem.lastStage)]

        if (editorLogicSystem.grabbedCellIndex != -1) {
            editorSimulationSystem.getAllCloseNeighboursEditor(
                particleEntity.x[editorLogicSystem.grabbedCellIndex],
                particleEntity.y[editorLogicSystem.grabbedCellIndex],
                grabbedRadius = particleEntity.radius[editorLogicSystem.grabbedCellIndex],
                editorLogicSystem.grabbedCellIndex,
                currentTick = editorLogicSystem.currentTick,
                nextStageTick = nextStageTick
            ).forEach {
                shapeRenderer.color = Color.RED
                shapeRenderer.line(
                    particleEntity.x[editorLogicSystem.grabbedCellIndex],
                    particleEntity.y[editorLogicSystem.grabbedCellIndex],
                    particleEntity.x[it],
                    particleEntity.y[it]
                )
            }
        }

        linkReplay.forEachInTick(nextStageTick) { isNeural, isLink1NeuralDirected, i ->
            val cellA = linkEntity.links1[i]
            val cellB = linkEntity.links2[i]

            var isDrawLinkByDistance = true
            if (editorLogicSystem.grabbedCellIndex != -1) {
                if (editorLogicSystem.grabbedCellIndex == cellA || editorLogicSystem.grabbedCellIndex == cellB) {
                    val dx = particleEntity.x[cellB] - particleEntity.x[cellA]
                    val dy = particleEntity.y[cellB] - particleEntity.y[cellA]

                    val radiusA = particleEntity.radius[cellA]
                    val radiusB = particleEntity.radius[cellB]

                    val r = radiusA + radiusB

                    if (dx * dx + dy * dy > r * r) {
                        isDrawLinkByDistance = false
                    }
                }
            }

            if (isDrawLinkByDistance) {
                if (isNeural) {
                    if (isLink1NeuralDirected) {
                        shapeRenderer.color = Color.CYAN
                        shapeRenderer.drawTriangleMiddle(
                            particleEntity.x[cellB],
                            particleEntity.y[cellB],
                            particleEntity.x[cellA],
                            particleEntity.y[cellA],
                            2f / 20f
                        )
                    } else {
                        shapeRenderer.color = Color.CYAN
                        shapeRenderer.drawTriangleMiddle(
                            particleEntity.x[cellA],
                            particleEntity.y[cellA],
                            particleEntity.x[cellB],
                            particleEntity.y[cellB],
                            2f / 20f
                        )
                    }
                } else {
                    shapeRenderer.color = Color.RED
                }

                if (showPhysicalLink || isNeural) {
                    shapeRenderer.line(
                        particleEntity.x[cellB],
                        particleEntity.y[cellB],
                        particleEntity.x[cellA],
                        particleEntity.y[cellA]
                    )
                }
            }
        }

        cellReplay.forEachInTick(editorLogicSystem.currentTick) { cellType, index, _, angleCos, angleSin, _ ->
            if (editorLogicSystem.grabbedCellIndex != index) {
                when (cellType.toInt()) {
                    14 -> {
                        shapeRenderer.color = Color.CYAN
                        shapeRenderer.drawArrowWithRotationAngle(
                            startX = particleEntity.x[index],
                            startY = particleEntity.y[index],
                            angleCos = angleCos,
                            angleSin = angleSin,
                            length = cellEntity.specialEntity.getVisibilityRange(index),
                            isDrawWithoutTriangle = true,
                        )
                    }

                    3, 9, 15, 19, 21 -> {
                        shapeRenderer.color = Color.CYAN
                        shapeRenderer.drawArrowWithRotationAngle(
                            startX = particleEntity.x[index],
                            startY = particleEntity.y[index],
                            angleCos = angleCos,
                            angleSin = angleSin,
                            length = 15f / 40f
                        )
                    }
                }
            }
        }

        if (editorLogicSystem.previousCtrlClicked != -1 && cellReplay.getCellIndex(nextStageTick, editorLogicSystem.previousCtrlClicked) != null) {
            val x = particleEntity.x[editorLogicSystem.previousCtrlClicked]
            val y = particleEntity.y[editorLogicSystem.previousCtrlClicked]

            shapeRenderer.color = Color.CYAN
            shapeRenderer.circle(x, y,  5f / 40f)
        } else {
            editorLogicSystem.previousCtrlClicked = -1
        }

        shapeRenderer.end()
    }
}
