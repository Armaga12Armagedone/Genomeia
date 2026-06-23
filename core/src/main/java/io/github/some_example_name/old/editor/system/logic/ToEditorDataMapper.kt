package io.github.some_example_name.old.editor.system.logic

import io.github.some_example_name.old.core.utils.invSqrt
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.currentTick
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.lastTick
import io.github.some_example_name.old.editor.entities.CellReplay
import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.editor.system.simulation.EditorSimulationSystem
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.ParticleEntity
import kotlin.math.atan2

class ToEditorDataMapper(
    val cellEntity: CellEntity,
    val cellReplay: CellReplay,
    val editorSimulationSystem: EditorSimulationSystem,
    val particleEntity: ParticleEntity
) {

    fun mapToEditorData(index: Int): EditorCell {
        val id = cellEntity.cellGenomeId[index]
        val currentCellIndex = cellReplay.getCellIndex(currentTick, index)
        val isPhantom = currentCellIndex == null
        val parentIndex = if (index != 0) { cellEntity.parentIndex[index] } else -1
        val parentId = if (index != 0) { cellEntity.cellGenomeId[parentIndex] } else -1
        val action = if (currentTick != lastTick){
            editorSimulationSystem.genome.genomeStageInstruction[currentTick]
                .cellActions[if (isPhantom) parentId else id]
        } else null

        val angleToParent = if (index != 0) {
            val dx = particleEntity.x[index] - particleEntity.x[parentIndex]
            val dy = particleEntity.y[index] - particleEntity.y[parentIndex]

            val len = 1f / invSqrt(dx * dx + dy * dy)
            val toChildCos = dx / len
            val toChildSin = dy / len

            atan2(toChildSin, toChildCos)
        } else 0f

        return EditorCell(
            id = id,
            index = index,
            parentIndex = parentIndex,
            parentId = parentId,
            x = particleEntity.x[index],
            y = particleEntity.y[index],
            radius = particleEntity.radius[index],
            isPhantom = isPhantom,
            angleToParent = angleToParent,
            divide = action?.divide,
            mutate = action?.mutate
        )
    }

}


