package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage

/**
 * Отмена мутации клетки на этой стадии.
 *
 * В mutate кроме самой мутации (тип, цвет, параметры нейрона/глаза/феромона) лежат ещё и
 * нейросвязи этой стадии — physicalLink, их туда пишет AddNeuralLinkCommand. Отмена
 * мутации их не трогает: остаётся mutate, в котором только physicalLink, и MutateManager
 * на нём ничего в клетке не меняет, а только расставляет связи.
 *
 * Если после отмены у клетки на стадии не осталось ни divide, ни mutate, запись клетки
 * из стадии удаляется. Опустевшая стадия удаляется, только если она последняя: удаление
 * промежуточной сдвинуло бы на тик все последующие. Единственная стадия генома остаётся
 * всегда, даже пустая.
 */
class CancelMutationCommand(
    val clickedCell: EditorCell,
    stageInstruction: MutableList<GenomeStage>,
    val currentTick: Int
) : UndoRedoCommand(stageInstruction) {

    override fun execute(stages: List<GenomeStage>): List<GenomeStage> {
        val stage = stages[currentTick]
        val oldValue = stage.cellActions[clickedCell.id] ?: throw Exception("Nothing to cancel")
        val oldMutate = oldValue.mutate ?: throw Exception("Nothing to cancel")

        val newMutate = if (oldMutate.physicalLink.isEmpty()) null else Action(physicalLink = oldMutate.physicalLink)
        val newValue = oldValue.copy(mutate = newMutate)

        val newCellActions = if (newValue.divide == null && newValue.mutate == null) {
            stage.cellActions - clickedCell.id
        } else {
            stage.cellActions + (clickedCell.id to newValue)
        }

        val result = stages.toMutableList()
        // Единственную стадию не удаляем: геном без стадий редактор и симуляция не переварят.
        if (newCellActions.isEmpty() && currentTick == stages.lastIndex && currentTick > 0) {
            result.removeAt(currentTick)
        } else {
            result[currentTick] = stage.copy(cellActions = newCellActions)
        }
        return result
    }
}

/** Есть ли в mutate что-то кроме нейросвязей, то есть есть ли что отменять. */
fun Action?.hasCellMutation(): Boolean =
    this != null && this != Action(physicalLink = physicalLink)
