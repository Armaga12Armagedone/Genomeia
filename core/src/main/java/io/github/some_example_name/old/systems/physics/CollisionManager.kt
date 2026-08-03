package io.github.some_example_name.old.systems.physics

import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.commands.WorldCommandType
import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.core.DEBUG_CHECKS
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.SubstancesEntity
import kotlin.math.sqrt

class CollisionManager(
    val entity: ParticleEntity,
    val worldCommandsManager: WorldCommandsManager,
    val cellEntity: CellEntity,
    val linkEntity: LinkEntity,
    val cellList: List<Cell>,
    val substancesEntity: SubstancesEntity
) {

    /**
     * Тот же список типов клеток, но массивом.
     *
     * cellList — это List<Cell>, то есть каждое обращение это вызов через интерфейс
     * (invokeinterface List.get + приведение типа + проверка границ внутри ArrayList),
     * и делается он на каждое касание. Массив даёт один aaload, без диспетчеризации.
     * Содержимое списка после старта не меняется, так что копия безопасна.
     */
    private val cells: Array<Cell> = cellList.toTypedArray()

    /**
     * Самый горячий метод симуляции: вызывается для каждой пары частиц-кандидатов,
     * то есть миллионы раз за тик.
     *
     * Про деления: их было по 3-4 на каждое касание (dx/distance, dy/distance,
     * distanceSquared/radiusSquared, гармоническое среднее жёсткостей). Деление float —
     * ~11-14 циклов латентности, и главное, divider на ядре один и почти не пайплайнится,
     * поэтому несколько делений подряд не перекрываются, а выстраиваются в очередь.
     * Теперь на любой путь остаётся максимум два деления: invDistance = 1/distance
     * (нормализация обеих компонент и 1/distanceSquared через него же) и отношение
     * distanceSquared/radiusSquared. Всё остальное — умножения: латентность ~4 цикла,
     * throughput 2/такт, независимые умножения уходят в разные порты.
     */
    fun repulse(particleAId: Int, particleBId: Int, threadId: Int = 0) = with(entity) {
        val dx = x[particleAId] - x[particleBId]
        val dy = y[particleAId] - y[particleBId]
        val dx2 = dx * dx
        if (dx2 > MAX_RADIUS_SQUARED) return
        val dy2 = dy * dy
        if (dy2 > MAX_RADIUS_SQUARED) return

        val particleRadius = radius[particleAId] + radius[particleBId]
        val radiusSquared = particleRadius * particleRadius

        val distanceSquared = dx2 + dy2
        if (distanceSquared < radiusSquared) {

            val isParticleAIsCell = isCell[particleAId]
            val isParticleBIsCell = isCell[particleBId]
            if (isParticleAIsCell && isParticleBIsCell) {
                val linkIndex = linkEntity.linkIndexMap.get(
                    holderEntityIndex[particleAId],
                    holderEntityIndex[particleBId]
                )
                if (linkIndex != -1) {
                    return@with
                }
            }

            val distance = sqrt(distanceSquared)

            // Одна обратная длина на всё касание: нормализация обеих компонент — это
            // умножения, и 1/distanceSquared тоже получается умножением
            // (invDistance * invDistance), без второго деления.
            val invDistance = 1f / distance


            if (isParticleAIsCell) {
                if (effectOnContact[particleAId]) {
                    val cellAIndex = holderEntityIndex[particleAId]
                    val cellType = cellEntity.cellType[cellAIndex].toInt()
                    cells[cellType].onContact(
                        cellIndex = cellAIndex,
                        particleIndexCollided = particleBId,
                        distance = distance,
                        threadId = threadId
                    )
                }
            }
            if (isParticleBIsCell) {
                if (effectOnContact[particleBId]) {
                    val cellBIndex = holderEntityIndex[particleBId]
                    val cellType = cellEntity.cellType[cellBIndex].toInt()
                    cells[cellType].onContact(
                        cellIndex = cellBIndex,
                        particleIndexCollided = particleAId,
                        distance = distance,
                        threadId = threadId
                    )
                }
            }

            if (!isParticleAIsCell && !isParticleBIsCell) {
                //TODO вынести в SubManager
                val rA2 = radius[particleAId] * radius[particleAId]
                val rB2 = radius[particleBId] * radius[particleBId]
                val radiusSumSquared = rA2 + rB2
                val dirX = dx * invDistance
                val dirY = dy * invDistance

                if (radiusSumSquared < PARTICLE_MAX_RADIUS_SQUARED) {

                    val maxRadius = maxOf(radius[particleAId], radius[particleBId])
                    if (distance < maxRadius && isSub[particleAId] && isSub[particleBId]) {
                        val subAIndex = holderEntityIndex[particleAId]
                        val subBIndex = holderEntityIndex[particleBId]
                        val radius = sqrt(radiusSumSquared)
                        val deleteIndex = if (this.radius[particleAId] < this.radius[particleBId]) {
                            this.radius[particleBId] = radius
                            subAIndex
                        } else {
                            this.radius[particleAId] = radius
                            subBIndex
                        }

                        worldCommandsManager.worldCommandBuffer[threadId].push(
                            type = WorldCommandType.DELETE_SUBSTANCE,
                            ints = intArrayOf(
                                deleteIndex,
                                substancesEntity.getGeneration(deleteIndex)
                            )
                        )
                    } else {
                        // 1/distanceSquared через уже посчитанный invDistance, без деления.
                        val invDistanceSquared = invDistance * invDistance
                        val force = 0.02f * rA2 * rB2 * invDistanceSquared
                        val fx = force * dirX
                        val fy = force * dirY
                        vx[particleBId] += fx
                        vy[particleBId] += fy
                        vx[particleAId] -= fx
                        vy[particleAId] -= fy
                    }
                } else {

                    val stiffness = 0.009f

                    if (DEBUG_CHECKS && distanceSquared < 0) {
                        throw Exception("distanceSquared < 0, distanceSquared = $distanceSquared")
                    }

                    val force = (distance - 0.35f) * stiffness

                    // Spring dampening
                    val dvx = vx[particleAId] - vx[particleBId]
                    val dvy = vy[particleAId] - vy[particleBId]

                    val dampeningConstant = 0.3f
                    val dampeningForce = dampeningConstant * (dvx * dirX + dvy * dirY)

                    val cellStrengthAverage = 0.01f
                    // c - c * d2/r2 свёрнуто в c * (1 - d2/r2): на одно умножение меньше.
                    val forceRepulsion =
                        cellStrengthAverage * (1f - distanceSquared / radiusSquared)

                    // Сумма сил считается один раз, а не отдельно для каждой компоненты.
                    val totalForce = force + dampeningForce - forceRepulsion

                    val fx = totalForce * dirX
                    val fy = totalForce * dirY

                    vx[particleBId] += fx
                    vy[particleBId] += fy
                    vx[particleAId] -= fx
                    vy[particleAId] -= fy
                }

                return@with
            }

            if (isCollidable[particleAId] && isCollidable[particleBId]) {
                // Квадратичная зависимость силы
                val stiffnessA = cellStiffness[particleAId]
                val stiffnessB = cellStiffness[particleBId]
                // Гармоническое среднее. У клеток одного типа жёсткости совпадают, а это
                // самый частый случай, поэтому равенство проверяется отдельно: ветка
                // предсказывается почти идеально и экономит деление.
                val cellStrengthAverage = if (stiffnessA == stiffnessB) stiffnessA
                else 2f * stiffnessA * stiffnessB / (stiffnessA + stiffnessB)

                val force =
                    cellStrengthAverage * (1f - distanceSquared / radiusSquared)
                // Нормализация вектора расстояния — умножением на обратную длину.
                val vectorX = dx * invDistance * force
                val vectorY = dy * invDistance * force

                vx[particleAId] += vectorX
                vy[particleAId] += vectorY
                vx[particleBId] -= vectorX
                vy[particleBId] -= vectorY
            }
        }
    }

    companion object {
        const val PARTICLE_MAX_RADIUS = 0.5f
        const val PARTICLE_MAX_RADIUS_SQUARED = 0.25f

        /**
         * Именно 4f, а не 4: с Int'ом сравнение dx2 > MAX_RADIUS_SQUARED требует
         * преобразования типа, и полагаться на то, что компилятор свернёт константу
         * в float (а не расширит сравнение до double), не хочется.
         */
        const val MAX_RADIUS_SQUARED = 4f
    }
}
