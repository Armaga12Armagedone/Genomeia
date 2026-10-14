package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Поведение стека undo/redo редактора на цепочках команд — так, как им пользуется игрок:
 * несколько разных правок подряд, откат, повтор, новая правка посреди истории.
 */
class CommandStackTest {

    private fun mutateCell0OnLastStage(n: Int, harness: EditorHarness) =
        MutateCellCommand(Action(cellType = "type$n"), cellAt(harness.stages, 4, 0), harness.stages, currentTick = 4)

    @Test
    fun `цепочка разных команд откатывается и повторяется по шагам`() {
        val harness = EditorHarness(baseGenome())
        val states = mutableListOf(harness.snapshot())

        // Каждая следующая команда строится по ТЕКУЩЕМУ геному, как в редакторе.
        harness.execute(MutateCellCommand(Action(cellType = "sucker"), cellAt(harness.stages, 3, 2), harness.stages, 3))
        states += harness.snapshot()

        harness.execute(
            DivideCellCommand(
                clickedCell = cellAt(harness.stages, 4, 5, x = 1f),
                neighboursCells = listOf(cellAt(harness.stages, 4, 4)),
                divide = Action(cellType = "fat"),
                newId = 6,
                newPoint = 1.5f to 0.5f,
                stageInstruction = harness.stages,
                currentTick = 4
            )
        )
        states += harness.snapshot()

        harness.execute(
            AddNeuralLinkCommand(
                cellFrom = cellAt(harness.stages, 2, 4, isPhantom = true),
                cellTo = cellAt(harness.stages, 2, 0),
                color = CYAN,
                linkIndex = -1,
                isLink1NeuralDirected = false,
                stageInstruction = harness.stages,
                currentTick = 2
            )
        )
        states += harness.snapshot()

        harness.execute(
            ChangeDivideCommand(cellAt(harness.stages, 2, 4, isPhantom = true), Action(cellType = "tail"), harness.stages, 2)
        )
        states += harness.snapshot()

        // Все пять состояний разные, иначе тест ничего бы не доказывал.
        assertEquals(5, states.toSet().size)

        for (i in 4 downTo 1) {
            harness.undo()
            assertEquals(states[i - 1], harness.stages, "отмена шага $i")
        }
        assertEquals(baseGenome(), harness.stages)

        for (i in 1..4) {
            harness.redo()
            assertEquals(states[i], harness.stages, "повтор шага $i")
        }

        assertEquals(12, harness.restarts)
    }

    @Test
    fun `новая команда после отмены сбрасывает повтор`() {
        val harness = EditorHarness(baseGenome())
        harness.execute(mutateCell0OnLastStage(1, harness))
        harness.undo()
        harness.execute(MutateCellCommand(Action(cellType = "bone"), cellAt(harness.stages, 1, 0), harness.stages, 1))

        assertTrue(harness.stack.redoStack.isEmpty())
        val afterSecond = harness.snapshot()
        val restarts = harness.restarts

        harness.redo()
        assertEquals(afterSecond, harness.stages, "повторять нечего")
        assertEquals(restarts, harness.restarts, "пустой повтор не перезапускает симуляцию")

        assertEquals(
            baseGenome().withAction(1, 0, CellAction(divide = baseGenome()[1].cellActions.getValue(0).divide, mutate = Action(cellType = "bone"))),
            harness.stages
        )
    }

    @Test
    fun `история ограничена 32 командами, самые старые выпадают`() {
        val harness = EditorHarness(baseGenome())
        for (n in 1..33) harness.execute(mutateCell0OnLastStage(n, harness))
        assertEquals(32, harness.stack.undoStack.size)

        repeat(32) { harness.undo() }
        // Первая команда выпала из истории — её результат откатить уже нельзя.
        assertEquals(Action(cellType = "type1"), harness.stages[4].cellActions.getValue(0).mutate)

        val restarts = harness.restarts
        harness.undo()
        assertEquals(Action(cellType = "type1"), harness.stages[4].cellActions.getValue(0).mutate)
        assertEquals(restarts, harness.restarts)

        repeat(32) { harness.redo() }
        assertEquals(Action(cellType = "type33"), harness.stages[4].cellActions.getValue(0).mutate)
    }

    @Test
    fun `отмена и повтор на пустой истории ничего не делают`() {
        val harness = EditorHarness(baseGenome())
        harness.undo()
        harness.redo()
        assertEquals(baseGenome(), harness.stages)
        assertEquals(0, harness.restarts)
    }

    /**
     * Так выглядит открытие другого генома: EditorSimulationSystem.reinitGenome переписывает
     * тот же список стадий, а стек команд живёт в object DIGenomeEditorContainer и остаётся.
     */
    private fun EditorHarness.openGenome(stages: List<io.github.some_example_name.old.systems.genomics.genome.GenomeStage>) {
        this.stages.clear()
        this.stages.addAll(stages)
    }

    private fun otherGenome() = listOf(stage(0 to CellAction(mutate = Action(cellType = "excreta"))))

    @Test
    fun `отмена не перетирает геном, открытый мимо истории, а сбрасывает историю`() {
        val harness = EditorHarness(baseGenome())
        harness.execute(mutateCell0OnLastStage(1, harness))
        harness.openGenome(otherGenome())
        val restarts = harness.restarts

        harness.undo()

        assertEquals(otherGenome(), harness.stages)
        assertTrue(harness.stack.undoStack.isEmpty())
        assertTrue(harness.stack.redoStack.isEmpty())
        assertEquals(restarts, harness.restarts)
    }

    @Test
    fun `повтор не перетирает геном, открытый мимо истории, а сбрасывает историю`() {
        val harness = EditorHarness(baseGenome())
        harness.execute(mutateCell0OnLastStage(1, harness))
        harness.undo()
        harness.openGenome(otherGenome())

        harness.redo()

        assertEquals(otherGenome(), harness.stages)
        assertTrue(harness.stack.undoStack.isEmpty())
        assertTrue(harness.stack.redoStack.isEmpty())
    }

    @Test
    fun `тот же геном, загруженный заново, историю сохраняет`() {
        val harness = EditorHarness(baseGenome())
        harness.execute(mutateCell0OnLastStage(1, harness))
        // «Сохранить и протестировать», потом обратно в редактор: геном читается из файла —
        // объекты новые, содержимое то же.
        harness.openGenome(baseGenome().withAction(4, 0, CellAction(mutate = Action(cellType = "type1"))))

        harness.undo()

        assertEquals(baseGenome(), harness.stages)
        assertEquals(1, harness.stack.redoStack.size)
    }

    @Test
    fun `упавшая команда не ломает уже накопленную историю`() {
        val harness = EditorHarness(baseGenome())
        harness.execute(mutateCell0OnLastStage(1, harness))
        val afterFirst = harness.snapshot()
        assertNotEquals(baseGenome(), afterFirst)

        assertFails {
            harness.execute(ChangeDivideCommand(cellAt(harness.stages, 4, 3, isPhantom = true), Action(), harness.stages, 4))
        }
        assertEquals(afterFirst, harness.stages)
        assertEquals(1, harness.stack.undoStack.size)

        harness.undo()
        assertEquals(baseGenome(), harness.stages)
    }
}
