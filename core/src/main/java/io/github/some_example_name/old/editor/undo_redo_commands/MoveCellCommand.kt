package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage
import io.github.some_example_name.old.systems.genomics.genome.LinkData
import kotlin.math.atan2
import kotlin.math.sqrt

class MoveCellCommand(
    val grabbedEditorCell: EditorCell,
    val parentEditorCell: EditorCell,
    val oldNeighboursJustAdded: List<EditorCell>,
    val newNeighbours: List<EditorCell>,
    val newX: Float,
    val newY: Float,
    currentTick: Int,
    stageInstruction: MutableList<GenomeStage>
) : UndoRedoCommand(
    tick = currentTick,
    genomeStageInstruction = stageInstruction
) {

    override fun execute() {
        //Удаление всех прошлых связок с новыми клетками
        oldNeighboursJustAdded.forEach {
            if (it.isPhantom) {
                it.divide?.physicalLink?.remove(grabbedEditorCell.id)
            }
        }

        val physicalLink =
            newNeighbours.filter { it.id != grabbedEditorCell.id }.associate { it ->
                val deltaX = newX - it.x
                val deltaY = newY - it.y
                val length = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                it.id to LinkData(length = length)
            }

        grabbedEditorCell.divide?.also { it ->
            it.physicalLink.clear()
            it.physicalLink.putAll(physicalLink)
        }

        //Добавлением новых связок
        val deltaX = newX - parentEditorCell.x
        val deltaY = newY - parentEditorCell.y

        val angle = atan2(deltaY, deltaX) - parentEditorCell.angleToParent

        grabbedEditorCell.divide?.also { it ->
            it.angle = angle
        }
    }
}
