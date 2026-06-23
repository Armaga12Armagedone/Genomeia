package io.github.some_example_name.old.editor.system.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.cells.Eye
import io.github.some_example_name.old.core.utils.drawArrowWithRotationAngle
import io.github.some_example_name.old.core.utils.drawTriangleMiddle
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.currentTick
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.linkColor
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.previousCtrlClicked
import io.github.some_example_name.old.editor.entities.CellReplay
import io.github.some_example_name.old.editor.entities.LinkReplay
import io.github.some_example_name.old.editor.system.CellSearchManager
import io.github.some_example_name.old.editor.system.simulation.EditorSimulationSystem
import io.github.some_example_name.old.editor.system.SymmetryManager
import io.github.some_example_name.old.editor.system.logic.EditorLogicSystem
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.systems.render.RenderSystem
import io.github.some_example_name.old.systems.render.ShaderManager
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class EditorRenderSystem(
    val shaderManager: ShaderManager,
    val cellReplay: CellReplay,
    val linkReplay: LinkReplay,
    val cellEntity: CellEntity,
    val particleEntity: ParticleEntity,
    val editorSimulationSystem: EditorSimulationSystem,
    val symmetryManager: SymmetryManager,
    val cellList: List<Cell>,
    val cellSearchManager: CellSearchManager
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

        val bRadius = (((radius  - 0.05f) / 0.7f) * 255f + 0.5f).toInt().coerceIn(0, 255)
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
            (buffer as Buffer).flip()
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
            DIGenomeEditorContainer.gridWidth.toFloat(),
            DIGenomeEditorContainer.gridHeight.toFloat()
        )

        symmetryManager.drawSymmetry(shapeRenderer)

        Gdx.gl.glLineWidth(2f)

        if (DIGenomeEditorContainer.grabbedCellIndex != -1) {
            cellSearchManager.getAllCloseNeighboursEditor(
                particleEntity.x[DIGenomeEditorContainer.grabbedCellIndex],
                particleEntity.y[DIGenomeEditorContainer.grabbedCellIndex],
                grabbedRadius = particleEntity.radius[DIGenomeEditorContainer.grabbedCellIndex],
                DIGenomeEditorContainer.grabbedCellIndex
            ).forEach {
                shapeRenderer.color = Color.RED
                shapeRenderer.line(
                    particleEntity.x[DIGenomeEditorContainer.grabbedCellIndex],
                    particleEntity.y[DIGenomeEditorContainer.grabbedCellIndex],
                    particleEntity.x[it],
                    particleEntity.y[it]
                )
            }
        }

        linkReplay.forEachInTick(DIGenomeEditorContainer.nextStageTick) { isNeural, isLink1NeuralDirected, color, links1, links2, isLongNeuralLink ->
            val cellA = links1
            val cellB = links2

            var isDrawLinkByDistance = true
            if (DIGenomeEditorContainer.grabbedCellIndex != -1) {
                if (DIGenomeEditorContainer.grabbedCellIndex == cellA || DIGenomeEditorContainer.grabbedCellIndex == cellB) {
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
                    val colorOfLink = Color().also {
                        val argb = color
                        val rgba = ((argb shr 16) and 0xFF) or (argb and 0xFF00) or ((argb shl 16) and 0xFF0000) or (argb and -0x1000000)
                        Color.argb8888ToColor(it,  rgba)
                    }
                    shapeRenderer.color = colorOfLink
                    if (isLink1NeuralDirected) {
                        shapeRenderer.drawTriangleMiddle(
                            particleEntity.x[cellB],
                            particleEntity.y[cellB],
                            particleEntity.x[cellA],
                            particleEntity.y[cellA],
                            0.1f
                        )
                    } else {
                        shapeRenderer.drawTriangleMiddle(
                            particleEntity.x[cellA],
                            particleEntity.y[cellA],
                            particleEntity.x[cellB],
                            particleEntity.y[cellB],
                            0.1f
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

        cellReplay.forEachInTick(currentTick) { cellType, index, _, angleCos, angleSin, _ ->
            if (DIGenomeEditorContainer.grabbedCellIndex != index) {
                val cell = cellList[cellType.toInt()]

                when {
                    cell is Eye -> {
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

                    cell.isDirected -> {
                        shapeRenderer.color = Color.CYAN
                        shapeRenderer.drawArrowWithRotationAngle(
                            startX = particleEntity.x[index],
                            startY = particleEntity.y[index],
                            angleCos = angleCos,
                            angleSin = angleSin,
                            length = 0.375f
                        )
                    }
                }
            }
        }

        if (previousCtrlClicked != -1 && cellReplay.getCellIndex(
                DIGenomeEditorContainer.nextStageTick, previousCtrlClicked) != null) {
            val x = particleEntity.x[previousCtrlClicked]
            val y = particleEntity.y[previousCtrlClicked]

            shapeRenderer.color = Color.CYAN
            shapeRenderer.circle(x, y,  0.125f, 32)
            shapeRenderer.circle(x, y,  3.0f, 64)

            val dx = touchedCellX - x
            val dy = touchedCellY - y

            val dist = sqrt(dx * dx + dy * dy)

            val maxDist = 3f

            shapeRenderer.color = linkColor

            var endX = touchedCellX
            var endY = touchedCellY

            if (dist > maxDist) {
                val scale = maxDist / dist
                val clampedX = x + dx * scale
                val clampedY = y + dy * scale

                shapeRenderer.line(x, y, clampedX, clampedY)
                endX = clampedX
                endY = clampedY
            } else {
                shapeRenderer.line(x, y, touchedCellX, touchedCellY)
            }

            val clickedCell = cellSearchManager.getClickedCellIndex(
                clickX = endX,
                clickY = endY
            )

            if (clickedCell != null) {
                shapeRenderer.color = Color.CYAN
                val x = particleEntity.x[clickedCell.first]
                val y = particleEntity.y[clickedCell.first]
                shapeRenderer.circle(x, y,  0.125f, 32)
            }
        } else {
            previousCtrlClicked = -1
        }

        shapeRenderer.end()
    }
}
