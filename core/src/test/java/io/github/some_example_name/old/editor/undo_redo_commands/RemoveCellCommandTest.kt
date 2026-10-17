package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Удаление клетки: команда, ради которой UndoRedoCommand научился отменять изменения
 * сразу многих стадий.
 */
class RemoveCellCommandTest {

    private fun remove(stages: MutableList<GenomeStage>, tick: Int, cellId: Int, parentId: Int = baseParents.getValue(cellId)) =
        RemoveCellCommand(
            clickedCell = cellAt(stages, tick, cellId, isPhantom = true, parentId = parentId),
            parentCell = cellAt(stages, tick, parentId, parentId = baseParents[parentId] ?: -1),
            stageInstruction = stages,
            currentTick = tick
        )

    private val base = baseGenome()

    @Test
    fun `клетка без потомков - уходит деление родителя, её действия и связи соседей с ней`() = checkCommand(
        initial = ::baseGenome,
        expected = listOf(
            base[0],
            base[1],
            // У 2 было только деление — запись ушла. Сосед 4 рождается на этой же стадии
            // и был связан с 3 — связь снята.
            stage(1 to CellAction(divide = divideOf(4, -0.5f, "bone", 1 to link()))),
            stage(
                4 to CellAction(divide = divideOf(5, 0f, "leaf", 4 to link())),
                0 to CellAction(mutate = Action(cellType = "muscle"))
            ),
            base[4]
        )
    ) { stages -> remove(stages, tick = 2, cellId = 3) }

    @Test
    fun `потомки удаляются по всем стадиям, опустевшая последняя стадия уходит`() = checkCommand(
        initial = ::baseGenome,
        expected = listOf(
            base[0],
            base[1],
            stage(2 to CellAction(divide = divideOf(3, 0.5f, "fat", 2 to link(), 0 to link()))),
            // 4 делилась на 5 — уходят обе.
            stage(
                3 to CellAction(mutate = Action(physicalLink = hashMapOf(0 to neural(0)))),
                0 to CellAction(mutate = Action(cellType = "muscle"))
            )
            // Стадия 4 мутировала только 5 — опустела и удалена.
        )
    ) { stages -> remove(stages, tick = 2, cellId = 4) }

    @Test
    fun `удаление с первой стадии - текущая стадия остаётся пустой, связи на следующих чистятся`() = checkCommand(
        initial = ::baseGenome,
        expected = listOf(
            // 0 больше ничего не делает на стадии 0, но стадия остаётся: она не последняя.
            GenomeStage(),
            // 2 рождается связанной с 0 и 1 — связь с удалённой 1 снята. Мутация 1 ушла.
            stage(0 to CellAction(divide = divideOf(2, 1f, "leaf", 0 to link()))),
            // Деление 1 -> 4 ушло вместе с 1.
            stage(2 to CellAction(divide = divideOf(3, 0.5f, "fat", 2 to link(), 0 to link()))),
            stage(
                3 to CellAction(mutate = Action(physicalLink = hashMapOf(0 to neural(0)))),
                0 to CellAction(mutate = Action(cellType = "muscle"))
            )
        )
    ) { stages -> remove(stages, tick = 0, cellId = 1) }

    @Test
    fun `нейросвязь живой клетки с удалённой снимается, остальные её связи остаются`() = checkCommand(
        initial = {
            baseGenome().withAction(
                4, 0, CellAction(mutate = Action(physicalLink = hashMapOf(5 to neural(5), 2 to neural(2))))
            )
        },
        expected = baseGenome()
            .withAction(3, 4, null)
            .withStage(4, stage(0 to CellAction(mutate = Action(physicalLink = mapOf(2 to neural(2))))))
    ) { stages -> remove(stages, tick = 3, cellId = 5) }

    @Test
    fun `мутация только из нейросвязей с удалённой уходит целиком, а с ней и опустевшая стадия`() = checkCommand(
        initial = {
            baseGenome().withAction(4, 0, CellAction(mutate = Action(physicalLink = hashMapOf(5 to neural(5)))))
        },
        expected = baseGenome().withAction(3, 4, null).take(4)
    ) { stages -> remove(stages, tick = 3, cellId = 5) }

    @Test
    fun `мутация родителя на той же стадии остаётся`() = checkCommand(
        initial = {
            baseGenome().withAction(
                2, 1, CellAction(divide = baseGenome()[2].cellActions.getValue(1).divide, mutate = Action(cellType = "bone"))
            )
        },
        expected = listOf(
            base[0],
            base[1],
            stage(
                2 to CellAction(divide = divideOf(3, 0.5f, "fat", 2 to link(), 0 to link())),
                1 to CellAction(mutate = Action(cellType = "bone"))
            ),
            stage(
                3 to CellAction(mutate = Action(physicalLink = hashMapOf(0 to neural(0)))),
                0 to CellAction(mutate = Action(cellType = "muscle"))
            )
        )
    ) { stages -> remove(stages, tick = 2, cellId = 4) }

    private fun genomeWithEmptyStageAfter() = baseGenome().toMutableList().apply {
        // Пустая стадия, которая была в геноме ДО удаления, — её удаление не касается.
        add(3, GenomeStage())
    }

    @Test
    fun `пустые стадии, которые были и до удаления, не трогаются`() = checkCommand(
        initial = ::genomeWithEmptyStageAfter,
        expected = listOf(
            base[0],
            base[1],
            stage(2 to CellAction(divide = divideOf(3, 0.5f, "fat", 2 to link(), 0 to link()))),
            GenomeStage(),
            stage(
                3 to CellAction(mutate = Action(physicalLink = hashMapOf(0 to neural(0)))),
                0 to CellAction(mutate = Action(cellType = "muscle"))
            )
        )
    ) { stages -> remove(stages, tick = 2, cellId = 4) }

    @Test
    fun `деление на последнем тике и его удаление возвращают исходный геном`() {
        val harness = EditorHarness(baseGenome())
        harness.execute(
            DivideCellCommand(
                clickedCell = cellAt(harness.stages, 5, 0),
                neighboursCells = listOf(cellAt(harness.stages, 5, 0)),
                divide = Action(cellType = "leaf"),
                newId = 6,
                newPoint = 0.6f to 0f,
                stageInstruction = harness.stages,
                currentTick = 5
            )
        )
        val afterDivide = harness.snapshot()
        assertEquals(6, afterDivide.size)

        harness.execute(remove(harness.stages, tick = 5, cellId = 6, parentId = 0))
        assertEquals(baseGenome(), harness.stages, "пустая последняя стадия не должна остаться хвостом")

        harness.undo()
        assertEquals(afterDivide, harness.stages)
        harness.undo()
        assertEquals(baseGenome(), harness.stages)
        harness.redo()
        harness.redo()
        assertEquals(baseGenome(), harness.stages)
    }

    @Test
    fun `удаление посреди цепочки команд откатывается вместе с ними`() {
        val harness = EditorHarness(baseGenome())
        val states = mutableListOf(harness.snapshot())

        harness.execute(MutateCellCommand(Action(cellType = "sucker"), cellAt(harness.stages, 3, 2), harness.stages, 3))
        states += harness.snapshot()
        // Удаление меняет число стадий — следующие команды работают уже с новой нумерацией.
        harness.execute(remove(harness.stages, tick = 2, cellId = 4))
        states += harness.snapshot()
        harness.execute(MutateCellCommand(Action(cellType = "stem"), cellAt(harness.stages, 3, 3), harness.stages, 3))
        states += harness.snapshot()

        assertEquals(4, states.toSet().size)
        for (i in 3 downTo 1) {
            harness.undo()
            assertEquals(states[i - 1], harness.stages, "отмена шага $i")
        }
        for (i in 1..3) {
            harness.redo()
            assertEquals(states[i], harness.stages, "повтор шага $i")
        }
    }

    /** Ровно то, что даёт getEmptyGenome(): зигота один раз делится на стадии 0. */
    private fun newUserGenome() = listOf(stage(0 to CellAction(divide = divideOf(1, 0f, "leaf", 0 to link()))))

    @Test
    fun `единственная стадия генома не удаляется, даже опустев`() = checkCommand(
        initial = ::newUserGenome,
        // Геном без стадий уронил бы редактор: stageInstruction[0], dividedTimes[0].
        expected = listOf(GenomeStage())
    ) { stages -> remove(stages, tick = 0, cellId = 1, parentId = 0) }

    @Test
    fun `без деления у родителя геном не меняется`() {
        val harness = EditorHarness(baseGenome())
        harness.execute(remove(harness.stages, tick = 4, cellId = 3))
        assertEquals(baseGenome(), harness.stages)
        harness.undo()
        assertEquals(baseGenome(), harness.stages)
    }
}
