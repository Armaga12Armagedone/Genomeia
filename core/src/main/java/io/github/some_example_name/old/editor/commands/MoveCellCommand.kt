package io.github.some_example_name.old.editor.commands

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
    val currentStage: Int,
    val autoLinking: Boolean,
    val genomeStageInstruction: MutableList<GenomeStage>
): Command  {

    override val stage = currentStage

    //TODO здесь по сути сохраняется 2 глубокие копии всего генома, это сильно проще, но это может жрать много памяти
    private val oldGenomeStageInstruction = genomeStageInstruction.map { it.deepCopy() }
    private var newGenomeStageInstruction: List<GenomeStage>? = null

    override fun execute() {
        if (newGenomeStageInstruction != null) {
            genomeStageInstruction.clear()
            genomeStageInstruction.addAll(newGenomeStageInstruction!!)
            return
        }

        move(oldNeighboursJustAdded, newNeighbours, newX, newY)

        newGenomeStageInstruction = genomeStageInstruction.map { it.deepCopy() }
    }

    override fun undo() {
        genomeStageInstruction.clear()
        genomeStageInstruction.addAll(oldGenomeStageInstruction)
    }

    private fun move(
        oldNeighboursJustAdded: List<EditorCell>,
        newNeighbours: List<EditorCell>,
        newX: Float,
        newY: Float
    ) {
        if (autoLinking) {
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
