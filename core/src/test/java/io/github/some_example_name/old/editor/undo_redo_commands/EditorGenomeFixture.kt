package io.github.some_example_name.old.editor.undo_redo_commands

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.editor.system.command.CommandEditorStackManager
import io.github.some_example_name.old.editor.system.logic.RestartSimulationCallBack
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage
import io.github.some_example_name.old.systems.genomics.genome.LinkData
import kotlin.math.sqrt
import kotlin.test.assertEquals

/*
 * Общая обвязка тестов команд редактора.
 *
 * Тесты намеренно не знают, КАК команда хранит отмену: они строят команду теми же
 * конструкторами, что и редактор, и гонят её через настоящий CommandEditorStackManager.
 * Поэтому одни и те же тесты проверяют и старую реализацию UndoRedoCommand, и новую.
 *
 * Никаких DIGenomeEditorContainer здесь быть не может: это object, и его инициализация
 * тянет DIGameGlobalContainer с WorldRenderer, то есть OpenGL.
 */

val GREEN = Color(0.133f, 0.545f, 0.133f, 1f)
val YELLOW = Color(1f, 0.84f, 0f, 1f)
val RED = Color(1f, 0f, 0f, 1f)
val BLUE = Color(0f, 0f, 1f, 1f)
val CYAN = Color(0f, 1f, 1f, 1f)

/** Физическая связь, как её пишет DivideCellCommand. */
fun link(length: Float = 0.6f) = LinkData(length = length)

/** Нейросвязь, как её пишет AddNeuralLinkCommand. */
fun neural(directedTo: Int, color: Color = CYAN) =
    LinkData(isNeuronal = true, directedNeuronLink = directedTo, color = color)

fun stage(vararg actions: Pair<Int, CellAction>) = GenomeStage(cellActions = hashMapOf(*actions))

fun divideOf(
    id: Int,
    angle: Float,
    cellType: Int,
    vararg links: Pair<Int, LinkData?>,
    color: Color = GREEN
) = Action(id = id, angle = angle, cellType = cellType, physicalLink = hashMapOf(*links), color = color)

/** Длина связи ровно тем же выражением, что в DivideCellCommand / MoveCellCommand. */
fun len(dx: Float, dy: Float): Float = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

/**
 * Небольшой организм на пять стадий. Каждый вызов строит НОВЫЕ объекты — так тест может
 * сравнить исходный геном со свежей копией и поймать команду, которая поменяла чужую
 * стадию на месте вместо того, чтобы собрать новую.
 *
 *   стадия 0: 0 делится -> 1
 *   стадия 1: 0 делится -> 2 (связи с 0 и 1); 1 мутирует в тип 3
 *   стадия 2: 2 делится -> 3 (связи с 2 и 0); 1 делится -> 4 (связи с 1 и с 3 — соседом,
 *             который появляется на той же стадии)
 *   стадия 3: 3 мутирует (только нейросвязь к 0); 4 делится -> 5; 0 мутирует в тип 5
 *   стадия 4: 5 мутирует в тип 4
 */
fun baseGenome(): List<GenomeStage> = listOf(
    stage(0 to CellAction(divide = divideOf(1, 0f, 0, 0 to link()))),
    stage(
        0 to CellAction(divide = divideOf(2, 1f, 0, 0 to link(), 1 to link())),
        1 to CellAction(mutate = Action(cellType = 3, color = YELLOW))
    ),
    stage(
        2 to CellAction(divide = divideOf(3, 0.5f, 1, 2 to link(), 0 to link())),
        1 to CellAction(divide = divideOf(4, -0.5f, 2, 1 to link(), 3 to link()))
    ),
    stage(
        3 to CellAction(mutate = Action(physicalLink = hashMapOf(0 to neural(0)))),
        4 to CellAction(divide = divideOf(5, 0f, 0, 4 to link())),
        0 to CellAction(mutate = Action(cellType = 5))
    ),
    stage(5 to CellAction(mutate = Action(cellType = 4)))
)

/** Предок каждой клетки baseGenome. */
val baseParents = mapOf(0 to -1, 1 to 0, 2 to 0, 3 to 2, 4 to 1, 5 to 4)

/**
 * EditorCell так же, как его собирает ToEditorDataMapper: у клетки-призрака (она появится
 * делением на этой стадии) divide/mutate берутся из действия РОДИТЕЛЯ, у живой — из её
 * собственного. За концом генома действий нет.
 */
fun cellAt(
    stages: List<GenomeStage>,
    tick: Int,
    id: Int,
    isPhantom: Boolean = false,
    x: Float = 0f,
    y: Float = 0f,
    angleToParent: Float = 0f,
    parentId: Int = baseParents.getValue(id)
): EditorCell {
    val action = stages.getOrNull(tick)?.cellActions?.get(if (isPhantom) parentId else id)
    return EditorCell(
        id = id,
        index = id,
        parentIndex = parentId,
        parentId = parentId,
        x = x,
        y = y,
        radius = 0.5f,
        angleToParent = angleToParent,
        angleDirected = if (isPhantom) null else 0f,
        isPhantom = isPhantom,
        divide = action?.divide,
        mutate = action?.mutate,
        actual = null
    )
}

/** Список стадий редактора, стек команд и счётчик перезапусков симуляции. */
class EditorHarness(initial: List<GenomeStage>) {
    val stages: MutableList<GenomeStage> = initial.toMutableList()
    var restarts = 0
        private set

    val stack = CommandEditorStackManager().apply {
        bind(object : RestartSimulationCallBack {
            override fun restartSimulation() {
                restarts++
            }
        })
    }

    fun execute(command: UndoRedoCommand) = stack.executeCommand(command)
    fun undo() = stack.undo()
    fun redo() = stack.redo()

    /** Снимок для сравнения: копия списка, чтобы последующие команды его не меняли. */
    fun snapshot(): List<GenomeStage> = stages.toList()
}

/**
 * Полный цикл одной команды через стек, как в редакторе:
 * выполнение -> отмена -> повтор -> отмена.
 *
 * Дополнительно проверяется, что исходные объекты генома не тронуты: [initial] строит
 * геном заново, и копия, отданная команде, после всего цикла должна совпасть со свежей.
 */
fun checkCommand(
    initial: () -> List<GenomeStage>,
    expected: List<GenomeStage>,
    command: (stages: MutableList<GenomeStage>) -> UndoRedoCommand
) {
    val original = initial()
    val harness = EditorHarness(original)

    harness.execute(command(harness.stages))
    assertEquals(expected, harness.stages, "после выполнения")
    assertEquals(1, harness.restarts, "симуляция перезапускается после выполнения")

    harness.undo()
    assertEquals(initial(), harness.stages, "после отмены")

    harness.redo()
    assertEquals(expected, harness.stages, "после повтора")

    harness.undo()
    assertEquals(initial(), harness.stages, "после второй отмены")
    assertEquals(4, harness.restarts, "перезапуск на каждое выполнение/отмену/повтор")

    assertEquals(initial(), original, "команда поменяла исходные стадии на месте")
}

/** Копия генома с заменой одной стадии. */
fun List<GenomeStage>.withStage(index: Int, stage: GenomeStage): List<GenomeStage> =
    toMutableList().also { it[index] = stage }

/** Копия генома с заменой действия одной клетки на стадии (null — убрать действие). */
fun List<GenomeStage>.withAction(index: Int, cellId: Int, action: CellAction?): List<GenomeStage> {
    val actions = this[index].cellActions
    val newActions = if (action == null) actions - cellId else actions + (cellId to action)
    return withStage(index, GenomeStage(cellActions = newActions))
}
