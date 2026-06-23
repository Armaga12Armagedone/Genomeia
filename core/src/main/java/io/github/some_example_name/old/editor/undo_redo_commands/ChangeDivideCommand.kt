package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage

class ChangeDivideCommand(
    val clickedCell: EditorCell,
    val divide: Action,
    stageInstruction: MutableList<GenomeStage>,
    currentTick: Int
) : UndoRedoCommand(
    tick = currentTick,
    genomeStageInstruction = stageInstruction
) {

    override fun execute() {
        genomeStageInstruction[tick].cellActions.compute(clickedCell.parentId) { _, oldValue ->
            oldValue?.copy(divide = oldValue.divide?.copy(
                cellType = divide.cellType,
                radius = divide.radius,
                color = divide.color,
                angleDirected = divide.angleDirected,
                funActivation = divide.funActivation,
                a = divide.a,
                b = divide.b,
                c = divide.c,
                isSum = divide.isSum,
                colorRecognition = divide.colorRecognition,
                lengthDirected = divide.lengthDirected,
                pheromoneType = divide.pheromoneType
            ))
        }
    }
}
