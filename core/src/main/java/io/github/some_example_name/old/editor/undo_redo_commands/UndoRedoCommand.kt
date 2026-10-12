package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.systems.genomics.genome.GenomeStage

sealed interface StageResult {
    data class Keep(val stage: GenomeStage) : StageResult
    data object Remove : StageResult
}

/**
 * Команда редактора генома с отменой и повтором.
 *
 * КАК ХРАНИТСЯ ОТМЕНА
 * -------------------
 * Стадии генома неизменяемы: GenomeStage, CellAction, Action и LinkData — data class'ы на
 * val, и коллекции внутри них никто не меняет на месте (команды всегда собирают новые
 * через + / - / copy). Значит, снимок всего генома — это просто копия списка ССЫЛОК на
 * стадии: O(число стадий) указателей, без глубокого копирования. Команда держит снимок
 * до выполнения и после, а undo/redo подменяют содержимое списка целиком.
 *
 * Поэтому команда может переписать сколько угодно стадий — добавить, удалить, поменять
 * несколько сразу (RemoveCellCommand вычищает поддерево клеток по всем стадиям после
 * текущей), и отмена от этого никак не усложняется. Раньше хранилась одна стадия (номер
 * tick) до и после, и любая команда, задевающая больше одной стадии, отменялась неверно.
 *
 * ПОЧЕМУ execute ВОЗВРАЩАЕТ НОВЫЙ СПИСОК, А НЕ МЕНЯЕТ ЖИВОЙ
 * ---------------------------------------------------------
 * Если команда падает на полпути (MoveCellCommand и ChangeDivideCommand бросают, когда у
 * родителя нет деления), живой список остаётся нетронутым: результат применяется одним
 * действием и только после того, как execute отработал целиком.
 *
 * Живой список меняется на месте (clear + addAll), а не подменяется ссылкой: тот же
 * объект держит EditorSimulationSystem и отдаёт его в Genome на каждом прогоне.
 *
 * ГЕНОМ, ИЗМЕНЁННЫЙ МИМО ИСТОРИИ
 * ------------------------------
 * Стек команд живёт в object DIGenomeEditorContainer, то есть переживает выход из
 * редактора, а геном при входе загружается заново. Поэтому undo/redo сначала сверяют
 * живой список с состоянием, из которого команда уходит (after для undo, before для
 * redo). Не совпало — геном поменяли в обход истории (открыли другой), и команда ничего
 * не трогает, а возвращает false. Если же вернулись в редактор с тем же геномом (после
 * «сохранить и протестировать»), он совпадёт по содержимому, и история продолжит работать.
 *
 * Сверка дешёвая: в нормальном случае снимок и живой список состоят из одних и тех же
 * объектов, и equals каждой стадии заканчивается на проверке ссылок.
 */
abstract class UndoRedoCommand(
    val genomeStageInstruction: MutableList<GenomeStage>
) {
    private var before: List<GenomeStage>? = null
    private var after: List<GenomeStage>? = null

    /**
     * Первый вызов выполняет команду, следующие (после undo) повторяют сохранённый
     * результат без пересчёта.
     *
     * @return false, если повторить нельзя: живой геном уже не тот, от которого команда
     * отменялась. В этом случае геном не меняется.
     */
    fun redo(): Boolean {
        val after = after
        if (after != null) {
            if (genomeStageInstruction != before) return false
            replaceWith(after)
            return true
        }

        val before = genomeStageInstruction.toList()
        val result = execute(before).toList()
        this.before = before
        this.after = result
        replaceWith(result)
        return true
    }

    /**
     * @return false, если отменять нечего: живой геном не совпадает с результатом команды.
     * В этом случае геном не меняется.
     */
    fun undo(): Boolean {
        val before = before ?: throw IllegalStateException("undo() до первого redo()")
        if (genomeStageInstruction != after) return false
        replaceWith(before)
        return true
    }

    /**
     * Новый геном по текущему. [stages] — снимок, его менять нельзя: он же станет
     * состоянием для отмены.
     */
    protected abstract fun execute(stages: List<GenomeStage>): List<GenomeStage>

    private fun replaceWith(stages: List<GenomeStage>) {
        genomeStageInstruction.clear()
        genomeStageInstruction.addAll(stages)
    }
}

/**
 * Команда, которая меняет ровно одну стадию — номер [tick]. Такие все команды редактора,
 * кроме удаления клетки.
 *
 * [doesNeedAddNewStage] — команда выполняется на последнем тике, где стадии ещё нет:
 * тогда в конец генома добавляется пустая стадия, и команда пишет в неё.
 */
abstract class StageCommand(
    protected val tick: Int,
    genomeStageInstruction: MutableList<GenomeStage>,
    private val doesNeedAddNewStage: Boolean
) : UndoRedoCommand(genomeStageInstruction) {

    final override fun execute(stages: List<GenomeStage>): List<GenomeStage> {
        val result = stages.toMutableList()
        if (doesNeedAddNewStage) result.add(GenomeStage())

        when (val stageResult = executeOnStage(result[tick])) {
            is StageResult.Keep -> result[tick] = stageResult.stage
            StageResult.Remove -> result.removeAt(tick)
        }
        return result
    }

    /** Новая стадия вместо [stage] либо Remove — убрать стадию из генома совсем. */
    protected abstract fun executeOnStage(stage: GenomeStage): StageResult
}
