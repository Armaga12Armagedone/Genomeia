package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.systems.genomics.genome.GenomeStage

abstract class UndoRedoCommand(
    val tick: Int,
    val genomeStageInstruction: MutableList<GenomeStage>,
) {
    //TODO копировать только одну стадию на которой происходит изменение, в RemoveCellCommand осторожно, так как там меняется куча стадий
    private val oldGenomeStageInstruction = genomeStageInstruction.map { it.deepCopy() }
    private var newGenomeStageInstruction: List<GenomeStage>? = null

    fun redo() {
        if (newGenomeStageInstruction != null) {
            genomeStageInstruction.clear()
            genomeStageInstruction.addAll(newGenomeStageInstruction!!)
            return
        }

        execute()

        newGenomeStageInstruction = genomeStageInstruction.map { it.deepCopy() }
    }

    protected abstract fun execute()

    fun undo() {
        genomeStageInstruction.clear()
        genomeStageInstruction.addAll(oldGenomeStageInstruction)
    }
}
