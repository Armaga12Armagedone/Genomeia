package io.github.some_example_name.old.editor.undo_redo_commands

import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage
import org.junit.Test
import kotlin.math.atan2
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Поведение каждой команды редактора: что именно она пишет в геном и что возвращает
 * отмена. Тесты сняты с работающих команд ДО переделки UndoRedoCommand и обязаны
 * проходить без изменений после неё — это и есть гарантия, что переделка ничего не
 * сломала.
 *
 * Каждый случай проверяется полным циклом checkCommand: выполнение, отмена, повтор,
 * отмена — плюс проверка, что исходные стадии не поменяны на месте.
 */
class EditorCommandsTest {

    private val mutation = Action(cellType = "sucker", color = RED)

    private fun dividesOf(cellId: Int, tick: Int) = baseGenome()[tick].cellActions.getValue(cellId).divide!!

    // ==================== MutateCellCommand ====================

    @Test
    fun `mutate - клетка без действия на стадии получает новую запись`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome().withAction(3, 2, CellAction(mutate = mutation))
    ) { stages ->
        MutateCellCommand(mutation, cellAt(stages, 3, 2), stages, currentTick = 3)
    }

    @Test
    fun `mutate - деление клетки на той же стадии сохраняется`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome().withAction(2, 2, CellAction(divide = dividesOf(2, 2), mutate = mutation))
    ) { stages ->
        MutateCellCommand(mutation, cellAt(stages, 2, 2), stages, currentTick = 2)
    }

    @Test
    fun `mutate - замена мутации сохраняет её нейросвязи`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome().withAction(
            3, 3, CellAction(mutate = mutation.copy(physicalLink = mapOf(0 to neural(0))))
        )
    ) { stages ->
        MutateCellCommand(mutation, cellAt(stages, 3, 3), stages, currentTick = 3)
    }

    @Test
    fun `mutate - на последнем тике добавляется новая стадия`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome() + stage(4 to CellAction(mutate = mutation))
    ) { stages ->
        MutateCellCommand(mutation, cellAt(stages, 5, 4), stages, currentTick = 5)
    }

    // ==================== DivideCellCommand ====================

    @Test
    fun `divide - угол от родителя и длины связей до соседей, мутация на стадии сохраняется`() {
        val expectedDivide = Action(
            id = 6,
            angle = atan2(0.5f, 0.5f) - 0.3f,
            cellType = "bone",
            physicalLink = mapOf(4 to link(len(1.5f, 0.5f)), 2 to link(len(0.5f, -0.5f))),
            color = RED,
            radius = 0.4f
        )
        checkCommand(
            initial = ::baseGenome,
            expected = baseGenome().withAction(
                4, 5, CellAction(divide = expectedDivide, mutate = Action(cellType = "neuron"))
            )
        ) { stages ->
            DivideCellCommand(
                clickedCell = cellAt(stages, 4, 5, x = 1f, y = 0f, angleToParent = 0.3f),
                neighboursCells = listOf(
                    cellAt(stages, 4, 4, x = 0f, y = 0f),
                    cellAt(stages, 4, 2, x = 1f, y = 1f)
                ),
                divide = Action(cellType = "bone", color = RED, radius = 0.4f),
                newId = 6,
                newPoint = 1.5f to 0.5f,
                stageInstruction = stages,
                currentTick = 4
            )
        }
    }

    @Test
    fun `divide - на последнем тике добавляется новая стадия`() {
        val expectedDivide = Action(
            id = 6,
            angle = atan2(0f, 0.6f) - 0f,
            cellType = "leaf",
            physicalLink = mapOf(0 to link(len(0.6f, 0f)))
        )
        checkCommand(
            initial = ::baseGenome,
            expected = baseGenome() + stage(0 to CellAction(divide = expectedDivide))
        ) { stages ->
            DivideCellCommand(
                clickedCell = cellAt(stages, 5, 0),
                neighboursCells = listOf(cellAt(stages, 5, 0)),
                divide = Action(cellType = "leaf"),
                newId = 6,
                newPoint = 0.6f to 0f,
                stageInstruction = stages,
                currentTick = 5
            )
        }
    }

    // ==================== ChangeDivideCommand ====================

    private val changedDivide = Action(
        id = 99, angle = 9f, physicalLink = mapOf(7 to link(1f)),
        cellType = "food_sensor", color = BLUE, radius = 0.3f, angleDirected = 0.7f,
        funActivation = "cos", a = 1.5f, b = 0.5f, c = 0.25f, isSum = false,
        colorRecognition = 3, lengthDirected = 4f, pheromoneType = 1
    )

    @Test
    fun `change - меняет параметры деления, но не id, угол и связи`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome().withAction(
            2, 1, CellAction(
                divide = dividesOf(1, 2).copy(
                    cellType = "food_sensor", color = BLUE, radius = 0.3f, angleDirected = 0.7f,
                    funActivation = "cos", a = 1.5f, b = 0.5f, c = 0.25f, isSum = false,
                    colorRecognition = 3, lengthDirected = 4f, pheromoneType = 1
                )
            )
        )
    ) { stages ->
        ChangeDivideCommand(cellAt(stages, 2, 4, isPhantom = true), changedDivide, stages, currentTick = 2)
    }

    @Test
    fun `change - без деления у родителя падает и ничего не трогает`() {
        val harness = EditorHarness(baseGenome())
        assertFails {
            harness.execute(
                ChangeDivideCommand(cellAt(harness.stages, 4, 3, isPhantom = true), changedDivide, harness.stages, 4)
            )
        }
        assertEquals(baseGenome(), harness.stages)
        assertTrue(harness.stack.undoStack.isEmpty())
        assertEquals(0, harness.restarts)
    }

    // ==================== MoveCellCommand ====================

    @Test
    fun `move - пересчитывает угол и связи деления, снимает связи старых соседей-призраков`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome()
            .withAction(
                2, 2, CellAction(
                    divide = dividesOf(2, 2).copy(
                        physicalLink = mapOf(0 to link(len(2f, 0.5f)), 2 to link(len(1f, -0.5f))),
                        angle = atan2(-0.5f, 1f) - 0.25f
                    )
                )
            )
            .withAction(2, 1, CellAction(divide = dividesOf(1, 2).copy(physicalLink = mapOf(1 to link()))))
    ) { stages ->
        val grabbed = cellAt(stages, 2, 3, isPhantom = true, x = 0.5f, y = 1.5f)
        MoveCellCommand(
            grabbedEditorCell = grabbed,
            parentEditorCell = cellAt(stages, 2, 2, x = 1f, y = 1f, angleToParent = 0.25f),
            // 4 — призрак, его деление связано с 3: связь должна уйти. 0 — живая: не трогается.
            oldNeighboursJustAdded = listOf(
                cellAt(stages, 2, 4, isPhantom = true, x = 0.2f, y = 1.4f),
                cellAt(stages, 2, 0)
            ),
            // Сама перетаскиваемая клетка в списке соседей отфильтровывается.
            newNeighbours = listOf(cellAt(stages, 2, 0), cellAt(stages, 2, 2, x = 1f, y = 1f), grabbed),
            newX = 2f,
            newY = 0.5f,
            currentTick = 2,
            stageInstruction = stages
        )
    }

    @Test
    fun `move - без деления у родителя падает и ничего не трогает`() {
        val harness = EditorHarness(baseGenome())
        assertFails {
            harness.execute(
                MoveCellCommand(
                    grabbedEditorCell = cellAt(harness.stages, 4, 4, isPhantom = true),
                    parentEditorCell = cellAt(harness.stages, 4, 1),
                    oldNeighboursJustAdded = emptyList(),
                    newNeighbours = emptyList(),
                    newX = 1f,
                    newY = 1f,
                    currentTick = 4,
                    stageInstruction = harness.stages
                )
            )
        }
        assertEquals(baseGenome(), harness.stages)
        assertTrue(harness.stack.undoStack.isEmpty())
        assertEquals(0, harness.restarts)
    }

    // ==================== AddNeuralLinkCommand ====================

    @Test
    fun `neural - призрак и живая клетка, связь пишется в деление родителя призрака`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome().withAction(
            2, 1, CellAction(
                divide = dividesOf(1, 2).copy(physicalLink = mapOf(1 to link(), 3 to link(), 0 to neural(0)))
            )
        )
    ) { stages ->
        AddNeuralLinkCommand(
            cellFrom = cellAt(stages, 2, 4, isPhantom = true),
            cellTo = cellAt(stages, 2, 0),
            color = CYAN,
            linkIndex = -1,
            isLink1NeuralDirected = false,
            stageInstruction = stages,
            currentTick = 2
        )
    }

    @Test
    fun `neural - два призрака, существующая физическая связь получает нейро-флаг и длину сохраняет`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome().withAction(
            2, 1, CellAction(
                divide = dividesOf(1, 2).copy(
                    physicalLink = mapOf(
                        1 to link(),
                        3 to link().copy(isNeuronal = true, directedNeuronLink = 3, color = CYAN)
                    )
                )
            )
        )
    ) { stages ->
        AddNeuralLinkCommand(
            cellFrom = cellAt(stages, 2, 4, isPhantom = true),
            cellTo = cellAt(stages, 2, 3, isPhantom = true),
            color = CYAN,
            linkIndex = -1,
            isLink1NeuralDirected = false,
            stageInstruction = stages,
            currentTick = 2
        )
    }

    @Test
    fun `neural - снятие связи между призраками убирает запись из деления`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome().withAction(
            2, 1, CellAction(divide = dividesOf(1, 2).copy(physicalLink = mapOf(1 to link())))
        )
    ) { stages ->
        AddNeuralLinkCommand(
            cellFrom = cellAt(stages, 2, 4, isPhantom = true),
            cellTo = cellAt(stages, 2, 3, isPhantom = true),
            color = CYAN,
            linkIndex = 0,
            isLink1NeuralDirected = false,
            stageInstruction = stages,
            currentTick = 2
        )
    }

    @Test
    fun `neural - две живые клетки, связь пишется в мутацию исходной`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome().withAction(4, 0, CellAction(mutate = Action(physicalLink = mapOf(5 to neural(5)))))
    ) { stages ->
        AddNeuralLinkCommand(
            cellFrom = cellAt(stages, 4, 0),
            cellTo = cellAt(stages, 4, 5),
            color = CYAN,
            linkIndex = -1,
            isLink1NeuralDirected = false,
            stageInstruction = stages,
            currentTick = 4
        )
    }

    @Test
    fun `neural - удаление связи с прошлых стадий пишется как null в мутацию целевой клетки`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome().withAction(4, 3, CellAction(mutate = Action(physicalLink = mapOf(0 to null))))
    ) { stages ->
        AddNeuralLinkCommand(
            cellFrom = cellAt(stages, 4, 0),
            cellTo = cellAt(stages, 4, 3),
            color = CYAN,
            linkIndex = 2,
            isLink1NeuralDirected = true,
            stageInstruction = stages,
            currentTick = 4
        )
    }

    /** Геном, где на средней стадии нет ничего, кроме одной нейросвязи. */
    private fun genomeWithLinkOnlyStage() = listOf(
        stage(0 to CellAction(divide = divideOf(1, 0f, "leaf", 0 to link()))),
        stage(1 to CellAction(mutate = Action(physicalLink = hashMapOf(0 to neural(0))))),
        stage(0 to CellAction(mutate = Action(cellType = "bone")))
    )

    @Test
    fun `neural - опустевшая средняя стадия удаляется, отмена возвращает её на место`() = checkCommand(
        initial = ::genomeWithLinkOnlyStage,
        expected = genomeWithLinkOnlyStage().let { listOf(it[0], it[2]) }
    ) { stages ->
        AddNeuralLinkCommand(
            cellFrom = cellAt(stages, 1, 1, parentId = 0),
            cellTo = cellAt(stages, 1, 0, parentId = -1),
            color = CYAN,
            linkIndex = 3,
            isLink1NeuralDirected = false,
            stageInstruction = stages,
            currentTick = 1
        )
    }

    @Test
    fun `neural - на последнем тике добавляется новая стадия`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome() + stage(0 to CellAction(mutate = Action(physicalLink = mapOf(5 to neural(5)))))
    ) { stages ->
        AddNeuralLinkCommand(
            cellFrom = cellAt(stages, 5, 0),
            cellTo = cellAt(stages, 5, 5),
            color = CYAN,
            linkIndex = -1,
            isLink1NeuralDirected = false,
            stageInstruction = stages,
            currentTick = 5
        )
    }

    // ==================== CancelMutationCommand ====================

    @Test
    fun `cancel - мутация убирается вместе с опустевшей записью клетки`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome().withAction(1, 1, null)
    ) { stages ->
        CancelMutationCommand(cellAt(stages, 1, 1), stages, currentTick = 1)
    }

    @Test
    fun `cancel - деление клетки на той же стадии остаётся`() = checkCommand(
        initial = {
            baseGenome().withAction(2, 1, CellAction(divide = dividesOf(1, 2), mutate = Action(cellType = "bone")))
        },
        expected = baseGenome()
    ) { stages ->
        CancelMutationCommand(cellAt(stages, 2, 1), stages, currentTick = 2)
    }

    @Test
    fun `cancel - нейросвязи мутации остаются`() = checkCommand(
        initial = {
            baseGenome().withAction(
                3, 0, CellAction(mutate = Action(cellType = "muscle", physicalLink = hashMapOf(3 to neural(3))))
            )
        },
        expected = baseGenome().withAction(3, 0, CellAction(mutate = Action(physicalLink = mapOf(3 to neural(3)))))
    ) { stages ->
        CancelMutationCommand(cellAt(stages, 3, 0), stages, currentTick = 3)
    }

    @Test
    fun `cancel - опустевшая последняя стадия удаляется`() = checkCommand(
        initial = ::baseGenome,
        expected = baseGenome().take(4)
    ) { stages ->
        CancelMutationCommand(cellAt(stages, 4, 5), stages, currentTick = 4)
    }

    private fun genomeWithMutateOnlyStage() = listOf(
        stage(0 to CellAction(divide = divideOf(1, 0f, "leaf", 0 to link()))),
        stage(1 to CellAction(mutate = Action(cellType = "bone"))),
        stage(0 to CellAction(mutate = Action(cellType = "tail")))
    )

    @Test
    fun `cancel - опустевшая средняя стадия остаётся пустой, а не сдвигает следующие`() = checkCommand(
        initial = ::genomeWithMutateOnlyStage,
        expected = genomeWithMutateOnlyStage().let { listOf(it[0], GenomeStage(), it[2]) }
    ) { stages ->
        CancelMutationCommand(cellAt(stages, 1, 1, parentId = 0), stages, currentTick = 1)
    }

    @Test
    fun `cancel - единственная стадия генома не удаляется, даже опустев`() = checkCommand(
        initial = { listOf(stage(0 to CellAction(mutate = Action(cellType = "bone")))) },
        expected = listOf(GenomeStage())
    ) { stages ->
        CancelMutationCommand(cellAt(stages, 0, 0), stages, currentTick = 0)
    }

    @Test
    fun `hasCellMutation - нейросвязи мутацией не считаются`() {
        assertFalse((null as Action?).hasCellMutation())
        assertFalse(Action().hasCellMutation())
        assertFalse(Action(physicalLink = mapOf(0 to neural(0))).hasCellMutation())
        assertFalse(Action(physicalLink = mapOf(0 to null)).hasCellMutation())
        assertTrue(Action(cellType = "fat").hasCellMutation())
        assertTrue(Action(color = RED, physicalLink = mapOf(0 to neural(0))).hasCellMutation())
    }
}
