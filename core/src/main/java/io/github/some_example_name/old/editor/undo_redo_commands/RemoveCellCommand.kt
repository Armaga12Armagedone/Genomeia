package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage

/**
 * Удаление клетки, которая появляется делением на этой стадии, — вместе со всем, что из
 * неё выросло потом.
 *
 * [clickedCell] — клетка-призрак на стадии [currentTick], [parentCell] — её родитель:
 * у него на этой стадии деление, которое её и создаёт.
 *
 * ЧТО УДАЛЯЕТСЯ
 * -------------
 *  1. Деление родителя на стадии [currentTick]. Мутация родителя на той же стадии остаётся.
 *  2. Все потомки удаляемой клетки: идём по стадиям после [currentTick], и деление любой
 *     уже удаляемой клетки добавляет в удаляемые её дочернюю. Потомок рождается не раньше
 *     стадии, на которой делится его предок, и действует только позже, поэтому одного
 *     прохода по возрастанию стадий хватает.
 *  3. Действия удаляемых клеток на стадиях после [currentTick].
 *  4. Связи с удаляемыми клетками у всех остальных — и физические в divide, и нейросвязи
 *     в mutate, начиная с [currentTick]: на ней же могут рождаться соседи, связанные с
 *     удаляемой. Мутация, в которой были только нейросвязи и все они ушли, удаляется
 *     целиком — так же, как это делает AddNeuralLinkCommand при снятии последней связи.
 *  5. Стадии после [currentTick], которые ОПУСТЕЛИ из-за удаления: на них делали что-то
 *     только удалённые клетки. Пустые стадии, которые были в геноме и до этого, не
 *     трогаются — это чужая структура.
 *  6. Стадия [currentTick], если опустела и оказалась последней: пустой хвост генома
 *     ничего не делает. Опустевшая средняя стадия остаётся, иначе все следующие
 *     сдвинулись бы на тик, а игрок стоит именно на ней. Единственная стадия генома
 *     остаётся всегда, даже пустая.
 *
 * Логика повторяет прежнюю мутабельную версию (до перехода генома на неизменяемые
 * стадии). Отличия: удаляются только стадии, опустевшие из-за удаления, а не все пустые
 * после текущей (пункт 5); убирается пустой хвост на текущей стадии (пункт 6); мутация,
 * потерявшая все нейросвязи, удаляется (пункт 4); прежняя версия этого не делала.
 *
 * Отмена и повтор — целым снимком генома, см. UndoRedoCommand.
 */
class RemoveCellCommand(
    val clickedCell: EditorCell,
    val parentCell: EditorCell,
    stageInstruction: MutableList<GenomeStage>,
    val currentTick: Int
) : UndoRedoCommand(stageInstruction) {

    override fun execute(stages: List<GenomeStage>): List<GenomeStage> {
        val tick = currentTick
        val parentAction = stages[tick].cellActions[parentCell.id] ?: return stages
        val removedCellId = parentAction.divide?.id ?: return stages

        // --- 2. Поддерево удаляемой клетки ---
        val removedIds = hashSetOf(removedCellId)
        for (stageIndex in tick + 1 until stages.size) {
            for ((cellId, action) in stages[stageIndex].cellActions) {
                if (cellId in removedIds) action.divide?.let { removedIds += it.id }
            }
        }

        // --- 1, 3, 4. Переписываем стадии начиная с текущей ---
        val result = stages.toMutableList()
        for (stageIndex in tick until stages.size) {
            var actions = stages[stageIndex].cellActions

            if (stageIndex == tick) {
                val withoutDivide = parentAction.copy(divide = null)
                actions = if (withoutDivide.mutate == null) {
                    actions - parentCell.id
                } else {
                    actions + (parentCell.id to withoutDivide)
                }
            } else {
                actions = actions.filterKeys { it !in removedIds }
            }

            actions = actions.withoutLinksTo(removedIds)

            if (actions != stages[stageIndex].cellActions) {
                result[stageIndex] = GenomeStage(cellActions = actions)
            }
        }

        // --- 5. Стадии после текущей, опустевшие из-за удаления ---
        for (stageIndex in stages.lastIndex downTo tick + 1) {
            if (stages[stageIndex].cellActions.isNotEmpty() && result[stageIndex].cellActions.isEmpty()) {
                result.removeAt(stageIndex)
            }
        }

        // --- 6. Пустой хвост на текущей стадии ---
        // Но не единственная стадия: геном без стадий редактор и симуляция не переварят
        // (stageInstruction[0], dividedTimes[0]), а одна пустая стадия — валидный геном.
        if (tick == result.lastIndex && tick > 0 && result[tick].cellActions.isEmpty()) {
            result.removeAt(tick)
        }

        return result
    }

    /**
     * Действия стадии без связей с [removedIds]. Что не изменилось, остаётся тем же
     * объектом: и снимок отмены не раздувается, и сравнение стадий остаётся дешёвым.
     */
    private fun Map<Int, CellAction>.withoutLinksTo(removedIds: Set<Int>): Map<Int, CellAction> {
        if (values.none { it.linksTo(removedIds) }) return this

        val newActions = LinkedHashMap<Int, CellAction>(size)
        for ((cellId, action) in this) {
            if (!action.linksTo(removedIds)) {
                newActions[cellId] = action
                continue
            }
            val newAction = CellAction(
                divide = action.divide?.withoutLinksTo(removedIds),
                mutate = action.mutate?.let { mutate ->
                    val stripped = mutate.withoutLinksTo(removedIds)
                    // Мутация жила ради нейросвязей и все их потеряла — больше она ничего не делает.
                    if (stripped !== mutate && stripped == Action()) null else stripped
                }
            )
            if (newAction.divide != null || newAction.mutate != null) {
                newActions[cellId] = newAction
            }
        }
        return newActions
    }

    private fun CellAction.linksTo(removedIds: Set<Int>): Boolean =
        divide?.physicalLink?.keys?.any { it in removedIds } == true ||
            mutate?.physicalLink?.keys?.any { it in removedIds } == true

    private fun Action.withoutLinksTo(removedIds: Set<Int>): Action =
        if (physicalLink.keys.none { it in removedIds }) this
        else copy(physicalLink = physicalLink.filterKeys { it !in removedIds })
}
