package io.github.some_example_name.old.systems.physics

import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.core.SubstrateSettings
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.SubstancesEntity
import io.github.some_example_name.old.systems.pheromone.PheromonesManager
import io.github.some_example_name.old.systems.simulation.SimulationData

class ParticlePhysicsSystem(
    val entity: ParticleEntity,
    val gridManager: GridManager,
    val substrateSettings: SubstrateSettings,
    val worldCommandsManager: WorldCommandsManager,
    val simulationData: SimulationData,
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val cellList: List<Cell>,
    val substancesEntity: SubstancesEntity,
    val pheromonesManager: PheromonesManager,
    val collisionManager: CollisionManager
) {

    /**
     * Многопоточная фаза: обрабатывает клетки сетки [start, end) и складывает индексы
     * частиц в стек чанка для последующей фазы движения.
     */
    fun processGridChunkPhysics(start: Int, end: Int, threadId: Int, isOdd: Boolean) {
        processGridRangePhysics(start, end, threadId, isOdd, distributeIndices = true)
    }

    /**
     * Ядро широкой фазы.
     *
     * Ключевые моменты по производительности:
     *  - НЕТ аллокаций: частицы клетки читаются прямо из CSR-массива gridManager.particleIdx,
     *    без copyOfRange (раньше это было ~150k аллокаций массивов за тик).
     *  - НЕТ разреженной сетки: частицы лежат подряд (34 КБ), а не в 256 КБ слотов по 4
     *    на клетку, из которых заняты единицы. Меньше кэш-линий на тот же объём данных.
     *  - НЕТ ветки на переполнение клетки и походов в хэш-мапу хвостов: путь всегда один.
     *  - НЕТ повторного чтения границы клетки: правая граница предыдущей клетки — это
     *    левая граница следующей, поэтому на клетку приходится одно чтение cellStart.
     *  - НЕТ целочисленного деления на клетку: x/y ведутся инкрементально
     *    (раньше было i % gridWidth и i / gridWidth = 2 idiv на каждую из 16k клеток).

     *  - НЕТ false sharing на счётчике чанка: позиция в стеке живёт в локальной
     *    переменной и пишется в oddCellCounter/evenCellCounter один раз в конце.
     *    Раньше counters[threadId] писался на КАЖДУЮ частицу, а все счётчики
     *    (IntArray(threadCount), 8 int = 32 байта) лежат в одной кэш-линии — то есть
     *    ~30k записей за тик гоняли одну линию между всеми ядрами по кругу
     *    (RFO + инвалидация в остальных L1, десятки-сотни циклов на запись).
     *  - Ссылки на массивы подняты в локальные переменные: поля cellStart/particleIdx
     *    объявлены как var, поэтому JIT обязан перечитывать их после каждой записи в память.
     *

     * Прямой обход сетки безопасен, потому что в этой фазе сетка не мутирует:
     * repulse и onContact пишут только в vx/vy/energy/radius и в отложенные команды.
     */
    fun processGridRangePhysics(
        start: Int,
        end: Int,
        threadId: Int,
        isOdd: Boolean,
        distributeIndices: Boolean
    ) {
        val starts = gridManager.cellStart
        val indices = gridManager.particleIdx
        val width = gridManager.gridWidth


        val stacks: Array<IntArray>? = if (!distributeIndices) null
        else if (isOdd) worldCommandsManager.oddCellChunkPositionStack
        else worldCommandsManager.evenCellChunkPositionStack

        // Позиция в стеке чанка держится в регистре, а не в разделяемом массиве счётчиков.
        var stack = stacks?.get(threadId)
        var stackCount = if (stacks == null) 0
        else if (isOdd) worldCommandsManager.oddCellCounter[threadId]
        else worldCommandsManager.evenCellCounter[threadId]

        // Двойной цикл по ряду/столбцу вместо i % width и i / width на каждую клетку.
        // gridWidth — это var, поэтому JIT не может свернуть деление в сдвиг: раньше на
        // каждую из ~16k клеток приходилось два настоящих idiv (20-40 циклов, divider не
        // пайплайнится и блокирует порт). Теперь деление считается один раз на весь чанк,
        // дальше только инкременты, и заодно исчезла проверка "x == width" на клетку.
        var cellIndex = start
        var y = start / width
        var x = start - y * width

        // Левая граница первой клетки чанка. Дальше она не перечитывается: правая граница
        // клетки — это левая граница следующей, а клетки внутри чанка идут подряд.
        var from = starts[cellIndex]

        while (cellIndex < end) {
            // Конец текущего ряда сетки. Внутри ряда индексы клеток идут подряд, так что
            // внутренний цикл — это линейный проход по cellStart и по particleIdx,
            // идеальный для аппаратного префетчера.
            var rowEnd = (y + 1) * width
            if (rowEnd > end) rowEnd = end

            while (cellIndex < rowEnd) {
                val to = starts[cellIndex + 1]

                if (to > from) {
                    // Пары внутри одной клетки сетки: каждая пара ровно один раз (i < j).
                    // Частицы клетки лежат в particleIdx подряд, ограничения на их
                    // количество больше нет.
                    for (i in from until to) {
                        val particleA = indices[i]
                        for (j in i + 1 until to) {
                            collisionManager.repulse(particleA, indices[j], threadId)
                        }
                    }

                    // Соседние клетки + раскладка по чанкам.
                    for (i in from until to) {
                        val particleIndex = indices[i]
                        processNeighborsCellsCollision(particleIndex, x, y, threadId)

                        if (stack != null) {
                            if (stackCount >= stack.size) {
                                stack = growChunkStack(stacks!!, threadId, stack)
                            }
                            stack[stackCount] = particleIndex
                            stackCount++
                        }
                    }
                }

                from = to
                cellIndex++
                x++
            }

            x = 0
            y++
        }



        // Единственная запись в разделяемый счётчик за весь чанк.
        if (stacks != null) {
            if (isOdd) worldCommandsManager.oddCellCounter[threadId] = stackCount
            else worldCommandsManager.evenCellCounter[threadId] = stackCount
        }
    }

    /**
     * Растит стек чанка и публикует новую ссылку. Каждый поток трогает только свой слот,

     * поэтому синхронизация не нужна; путь редкий, из горячего цикла вынесен.
     */
    private fun growChunkStack(stacks: Array<IntArray>, threadId: Int, current: IntArray): IntArray {
        val grown = current.copyOf(current.size + (current.size shr 1))
        stacks[threadId] = grown
        return grown
    }

    /**
     * Полуобход соседей: три клетки сверху (x-1, x, x+1) и одна справа.
     * Так каждая пара соседних клеток обрабатывается ровно один раз.
     *
     * Верхний ряд идёт одним отрезком — индексы клеток там последовательные,
     * это дешевле трёх независимых обращений и дружелюбнее к префетчеру.
     */
    fun processNeighborsCellsCollision(cellId: Int, gridX: Int, gridY: Int, threadId: Int = 0) {
        gridManager.forEachParticleInRowSegment(gridY + 1, gridX - 1, gridX + 1) { id ->
            collisionManager.repulse(cellId, id, threadId)
        }
        gridManager.forEachParticleAt(gridX + 1, gridY) { id ->
            collisionManager.repulse(cellId, id, threadId)
        }
    }

    /**
     * Оставлено для совместимости: попарный перебор по готовому массиву индексов.
     * В горячем пути не используется — там работает processGridRangePhysics.
     */
    fun processCollisionsInTheSameCell(cells: IntArray, threadId: Int = 0) {
        processCollisionsInTheSameCell(cells, cells.size, threadId)
    }

    fun processCollisionsInTheSameCell(cells: IntArray, count: Int, threadId: Int = 0) {
        for (i in 0 until count) {
            val particleA = cells[i]
            for (j in i + 1 until count) {
                collisionManager.repulse(particleA, cells[j], threadId)
            }
        }
    }
}
