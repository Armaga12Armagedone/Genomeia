package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.systems.genomics.genome.GenomeStage

sealed interface StageResult {
    data class Keep(val stage: GenomeStage) : StageResult
    data object Remove : StageResult
}

abstract class UndoRedoCommand(
    protected val tick: Int,
    val genomeStageInstruction: MutableList<GenomeStage>,
    private val doesNeedAddNewStage: Boolean
) {
    private val oldStage: GenomeStage = if (doesNeedAddNewStage) { GenomeStage() } else {
        genomeStageInstruction[tick]
    }

    private var newStage: StageResult? = null

    fun redo() {
        if (doesNeedAddNewStage) {
            genomeStageInstruction.add(GenomeStage())
        }

        newStage?.let { stage ->
            when (stage) {
                is StageResult.Keep -> genomeStageInstruction[tick] = stage.stage
                StageResult.Remove -> genomeStageInstruction.removeAt(tick)
            }
            return
        }

        val result = execute()
        newStage = result

        when (result) {
            is StageResult.Keep -> {
                genomeStageInstruction[tick] = result.stage
            }
            StageResult.Remove -> {
                genomeStageInstruction.removeAt(tick)
            }
        }
    }

    protected abstract fun execute(): StageResult

    fun undo() {
        when (newStage) {
            is StageResult.Remove -> {
                if (doesNeedAddNewStage) {
                    genomeStageInstruction.add(GenomeStage())
                }
                genomeStageInstruction.add(tick, oldStage)
            }

            is StageResult.Keep -> {
                genomeStageInstruction[tick] = oldStage

                if (doesNeedAddNewStage) {
                    genomeStageInstruction.removeAt(genomeStageInstruction.lastIndex)
                }
            }

            null -> {
                throw Exception("Unreachable code")
            }
        }
    }
}
