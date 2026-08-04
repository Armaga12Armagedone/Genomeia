package io.github.some_example_name.old.systems.physics

import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.core.DISimulationContainer.HALF_CHUNK_HEIGHT
import io.github.some_example_name.old.core.SubstrateSettings
import io.github.some_example_name.old.core.utils.invSqrt
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.SubstancesEntity
import io.github.some_example_name.old.systems.pheromone.PheromonesManager
import io.github.some_example_name.old.systems.simulation.SimulationData

class MovementManager(
    val entity: ParticleEntity,
    val gridManager: GridManager,
    val substrateSettings: SubstrateSettings,
    val worldCommandsManager: WorldCommandsManager,
    val simulationData: SimulationData,
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val cellList: List<Cell>,
    val substancesEntity: SubstancesEntity,
    val pheromonesManager: PheromonesManager
) {

    fun moveParticle(particleIndex: Int, threadId: Int = 0) = with(entity) {
        val oldX = x[particleIndex].toInt()
        val oldY = y[particleIndex].toInt()
        vy[particleIndex] -= 0


        processCellFrictionOld(particleIndex)

        seedBump(particleIndex)

        x[particleIndex] += vx[particleIndex] * SIM_STEP
        y[particleIndex] += vy[particleIndex] * SIM_STEP

        processWorldBorders(particleIndex)
        val x = x[particleIndex]
        val y = y[particleIndex]

        val newX = x.toInt()
        val newY = y.toInt()
        if (newX != oldX || newY != oldY) {
            if (isPheromoneEmitter[particleIndex]) {
                pheromonesManager.newGridCell(x, y, particleIndex, threadId)
            }
            // Сетка больше не мутируется: сюда пишется только новый номер клетки этой
            // частицы, а сама сетка пересобирается counting sort'ом один раз в конце тика.
            //
            // Раньше здесь было removeParticle + addParticle: линейный поиск частицы среди
            // слотов старой клетки, запись двух счётчиков в разных концах массива
            // particleCounts и на переполнении — поход в Int2ObjectOpenHashMap. Всё это
            // случайный доступ, и всё это выполнялось для каждой частицы, сменившей клетку
            // (а их за тик тысячи). Теперь это одна запись в свой же элемент массива.
            gridId[particleIndex] = gridManager.cellIndexOfClamped(newX, newY)
        }

    }

    /**
     * Ограничение скорости частицы.
     *
     * Порог и присваиваемое значение обязаны быть выражены в одних единицах, а единицы
     * задаёт SIM_STEP: за тик частица смещается на |v| * SIM_STEP, и это смещение не должно
     * превышать HALF_CHUNK_HEIGHT — иначе частица уходит за пределы чанка, которым владеет
     * её поток. Отсюда предел именно по скорости: MAX_SPEED = HALF_CHUNK_HEIGHT / SIM_STEP.
     *
     * Раньше порог был отмасштабирован (16/56.25 это (4/7.5)^2), а присваивание нет: вектор
     * скорости нормировался на HALF_CHUNK_HEIGHT, то есть |v| становилось 4 вместо 0.53.
     * Получалось не ограничение, а разгон в 7.5 раз, причём самоподдерживающийся — 4 всегда
     * больше порога, поэтому следующий тик снова возвращал ровно 4, затирая трение.
     * Частица улетала на 30 единиц за тик и рвала все свои связи по linkMaxLength2.
     */
    private fun seedBump(particleIndex: Int) = with(entity) {
        val vxv = vx[particleIndex]
        val vyv = vy[particleIndex]

        val speed2 = vxv * vxv + vyv * vyv
        if (speed2 > MAX_SPEED_2) {
            // MAX_SPEED / |v| — множитель строго меньше единицы, скорость только падает.
            val scale = MAX_SPEED * invSqrt(speed2)
            vx[particleIndex] = vxv * scale
            vy[particleIndex] = vyv * scale
        }
    }

    private fun processWorldBorders(cellId: Int) = with(entity) {
        if (x[cellId] < radius[cellId]) {
            x[cellId] = radius[cellId]
            vx[cellId] *= -0.8f
        } else if (x[cellId] > gridManager.gridWidth - radius[cellId]) {
            x[cellId] = gridManager.gridWidth - radius[cellId]
            vx[cellId] *= -0.8f
        }

        if (y[cellId] < radius[cellId]) {
            y[cellId] = radius[cellId]
            vy[cellId] *= -0.8f
        } else if (y[cellId] > gridManager.gridHeight - radius[cellId]) {
            y[cellId] = gridManager.gridHeight - radius[cellId]
            vy[cellId] *= -0.8f
        }
    }

    private fun processCellFrictionOld(particleIndex: Int) = with(entity) {
        if (isCell[particleIndex]) {
            val cellIndex = holderEntityIndex[particleIndex]
            val linkAmount = cellEntity.linkAmount[cellIndex]
            val x = linkAmount * 0.25f
            val x2 = x * x
            val x4 = x2 * x2

            val friction = (1f / (x4 + 1f)) * dragCoefficient[particleIndex]
            vx[particleIndex] *= 1f - friction
            vy[particleIndex] *= 1f - friction
        } else {
            vx[particleIndex] *= 1f - dragCoefficient[particleIndex]
            vy[particleIndex] *= 1f - dragCoefficient[particleIndex]
        }
    }

    companion object {
        /** Во сколько раз позиция продвигается за тик относительно скорости. */
        const val SIM_STEP = 7.5f

        /** Предел скорости: за тик частица не должна уходить дальше HALF_CHUNK_HEIGHT. */
        const val MAX_SPEED = HALF_CHUNK_HEIGHT / SIM_STEP

        /** Он же в квадрате — чтобы сравнивать со speed2 без корня. */
        const val MAX_SPEED_2 = MAX_SPEED * MAX_SPEED
    }
}
