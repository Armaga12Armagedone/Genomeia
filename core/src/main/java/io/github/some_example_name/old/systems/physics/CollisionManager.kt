package io.github.some_example_name.old.systems.physics

import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.commands.WorldCommandType
import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.core.DEBUG_CHECKS
import io.github.some_example_name.old.core.PROFILE_COUNTERS
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.SubstancesEntity
import io.github.some_example_name.old.systems.simulation.SimCounters
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
     *
     * Про ссылки на массивы: поля ParticleEntity объявлены как var (их переоткрывают
     * при росте сущности), поэтому каждое обращение вида x[i] — это два чтения:
     * сначала поле x объекта entity, потом сам элемент. Пока метод прямолинейный,
     * JIT сворачивает повторные чтения поля, но в середине repulse стоят вызовы
     * onContact, которые для компилятора могут записать что угодно в любое поле —
     * после них все поля приходится перечитывать заново. Поэтому массивы поднимаются
     * в локальные переменные (то есть в регистры) до вызовов и дальше используются
     * напрямую.
     *
     * Инвариант, на котором это держится: в параллельной фазе физики массивы частиц
     * не пересоздаются. onContact и repulse пишут только в элементы (vx/vy/energy/radius)
     * и в отложенные команды, а рост сущностей происходит в однопоточной фазе применения
     * команд. Если это когда-нибудь изменится, кэширование ссылок придётся убрать.
     */
    fun repulse(particleAId: Int, particleBId: Int, threadId: Int = 0) {
        val entity = entity
        val positionsX = entity.x
        val positionsY = entity.y
        val radii = entity.radius

        val dx = positionsX[particleAId] - positionsX[particleBId]
        val dy = positionsY[particleAId] - positionsY[particleBId]
        val distanceSquared = dx * dx + dy * dy

        if (distanceSquared > MAX_RADIUS_SQUARED) return

        val radiusA = radii[particleAId]
        val radiusB = radii[particleBId]
        val particleRadius = radiusA + radiusB
        val radiusSquared = particleRadius * particleRadius

        if (distanceSquared >= radiusSquared) return

        // Отсюда и ниже — путь реального пересечения. Всё, что выше, это ранний выход,
        // и разница в цене между ними порядковая; отношение COLLISIONS к PAIR_CANDIDATES
        // говорит, насколько плотно сетка отбирает кандидатов, то есть имеет ли смысл
        // мельчить клетки.
        if (PROFILE_COUNTERS) SimCounters.increment(threadId, SimCounters.COLLISIONS)

        // Дальше начинается редкая часть (реальное пересечение), только здесь имеет смысл
        // поднимать остальные массивы: на пути раннего выхода они бы стоили лишних чтений.
        val isCellFlags = entity.isCell
        val holderIndices = entity.holderEntityIndex

        val isParticleAIsCell = isCellFlags[particleAId]
        val isParticleBIsCell = isCellFlags[particleBId]
        if (isParticleAIsCell && isParticleBIsCell) {
            // Связанные клетки не отталкиваются: их держит пружина из LinkPhysicsSystem.
            //
            // Раньше здесь был linkEntity.linkIndexMap.get(a, b) — Long2IntOpenHashMap
            // на 100k записей (~1.5+ МБ). Ключ считался из пары индексов, то есть
            // обращение было случайным по всей таблице: промах L3/RAM (~200 циклов) на
            // каждую пару касающихся клеток, дороже, чем вся остальная математика метода.
            // Теперь это скан инлайн-списка соседей клетки — 8 int'ов в одной кэш-линии
            // (CellEntity.cellLinks), ~150-200 циклов экономии на пару.
            if (cellEntity.areCellsLinked(
                    holderIndices[particleAId],
                    holderIndices[particleBId]
                )
            ) {
                // Пересечение было, но работы не будет. Если таких пар много, значит
                // заметная доля дорогого пути тратится впустую, и связанные пары стоит
                // отсеивать раньше — например, вообще не класть соседей по пружине
                // в один список кандидатов.
                if (PROFILE_COUNTERS) SimCounters.increment(threadId, SimCounters.LINKED_SKIPS)
                return
            }
        }


        // Две частицы могут совпасть ТОЧНО, и это не теоретическая возможность.
        //
        // processWorldBorders зажимает координату ровно в radius, поэтому все частицы,
        // прилетевшие в один угол мира, получают буквально одинаковые x и y. Тогда
        // distance == 0, invDistance == Infinity, направление становится 0 * Infinity = NaN,
        // и NaN расползается в vx/vy, а оттуда в позицию.
        //
        // Дальше начинается самое неприятное: x.toInt() для NaN даёт 0, поэтому все
        // испорченные частицы сваливаются в клетку сетки номер 0 и остаются там навсегда —
        // NaN самовоспроизводится. Наблюдалось 627 частиц в одной клетке, а это ~196 тысяч
        // пар внутри неё; клетка неделима и достаётся одному воркеру, поэтому фаза коллизий
        // сериализуется: util_collide падал с 0.70 до 0.175 при семикратном росте времени.
        //
        // Порог, а не проверка на строгий ноль: около нуля 1/distance уже даёт огромные
        // силы, то есть взрыв вместо разведения. Направление при совпадении берётся
        // детерминированное и зависящее от индексов, чтобы разные пары расходились в разные
        // стороны, а не выстраивались в линию.
        val distance: Float
        val invDistance: Float
        val dirX: Float
        val dirY: Float
        if (distanceSquared > MIN_SEPARATION_SQUARED) {
            distance = sqrt(distanceSquared)
            // Одна обратная длина на всё касание: нормализация обеих компонент — это
            // умножения, и 1/distanceSquared тоже получается умножением
            // (invDistance * invDistance), без второго деления.
            invDistance = 1f / distance
            dirX = dx * invDistance
            dirY = dy * invDistance
        } else {
            distance = MIN_SEPARATION
            invDistance = 1f / MIN_SEPARATION
            if ((particleAId + particleBId) and 1 == 0) {
                dirX = 1f
                dirY = 0f
            } else {
                dirX = 0f
                dirY = 1f
            }
        }

        val effectOnContact = entity.effectOnContact

        if (isParticleAIsCell) {
            if (effectOnContact[particleAId]) {
                // Самая дорогая точка метода: вызов через 27 подклассов Cell, то есть
                // vtable + непредсказуемый indirect branch + отсутствие инлайнинга.
                // Доля CONTACTS в COLLISIONS показывает, какую часть времени фазы
                // занимает именно она, и стоит ли городить группировку по cellType.
                if (PROFILE_COUNTERS) SimCounters.increment(threadId, SimCounters.CONTACTS)
                val cellAIndex = holderIndices[particleAId]
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
                if (PROFILE_COUNTERS) SimCounters.increment(threadId, SimCounters.CONTACTS)
                val cellBIndex = holderIndices[particleBId]
                val cellType = cellEntity.cellType[cellBIndex].toInt()
                cells[cellType].onContact(
                    cellIndex = cellBIndex,
                    particleIndexCollided = particleAId,
                    distance = distance,
                    threadId = threadId
                )
            }
        }

        // Скорости берутся после вызовов onContact, но ссылки на массивы всё равно
        // локальные: дальше идёт по 4 записи в vx/vy, и перечитывать поля перед каждой
        // не нужно.
        val velocitiesX = entity.vx
        val velocitiesY = entity.vy

        if (!isParticleAIsCell && !isParticleBIsCell) {
            //TODO вынести в SubManager
            val rA2 = radiusA * radiusA
            val rB2 = radiusB * radiusB
            val radiusSumSquared = rA2 + rB2

            if (radiusSumSquared < PARTICLE_MAX_RADIUS_SQUARED) {

                val maxRadius = if (radiusA > radiusB) radiusA else radiusB
                if (distance < maxRadius && entity.isSub[particleAId] && entity.isSub[particleBId]) {
                    val mergedRadius = sqrt(radiusSumSquared)
                    val deleteIndex = if (radiusA < radiusB) {
                        radii[particleBId] = mergedRadius
                        holderIndices[particleAId]
                    } else {
                        radii[particleAId] = mergedRadius
                        holderIndices[particleBId]
                    }

                    // Скалярный push: без intArrayOf и без arraycopy на два int'а.
                    worldCommandsManager.worldCommandBuffer[threadId].push(
                        WorldCommandType.DELETE_SUBSTANCE,
                        deleteIndex,
                        substancesEntity.getGeneration(deleteIndex)
                    )
                } else {
                    // 1/distanceSquared через уже посчитанный invDistance, без деления.
                    val invDistanceSquared = invDistance * invDistance
                    val force = 0.02f * rA2 * rB2 * invDistanceSquared
                    val fx = force * dirX
                    val fy = force * dirY
                    velocitiesX[particleBId] += fx
                    velocitiesY[particleBId] += fy
                    velocitiesX[particleAId] -= fx
                    velocitiesY[particleAId] -= fy
                }
            } else {

                val stiffness = 0.009f

                if (DEBUG_CHECKS && distanceSquared < 0) {
                    throw Exception("distanceSquared < 0, distanceSquared = $distanceSquared")
                }

                val force = (distance - 0.35f) * stiffness

                // Spring dampening
                val dvx = velocitiesX[particleAId] - velocitiesX[particleBId]
                val dvy = velocitiesY[particleAId] - velocitiesY[particleBId]

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

                velocitiesX[particleBId] += fx
                velocitiesY[particleBId] += fy
                velocitiesX[particleAId] -= fx
                velocitiesY[particleAId] -= fy
            }

            return
        }

        val isCollidable = entity.isCollidable
        if (isCollidable[particleAId] && isCollidable[particleBId]) {
            // Квадратичная зависимость силы
            val cellStiffness = entity.cellStiffness
            val stiffnessA = cellStiffness[particleAId]
            val stiffnessB = cellStiffness[particleBId]
            // Гармоническое среднее. У клеток одного типа жёсткости совпадают, а это
            // самый частый случай, поэтому равенство проверяется отдельно: ветка
            // предсказывается почти идеально и экономит деление.
            val cellStrengthAverage = if (stiffnessA == stiffnessB) stiffnessA
            else 2f * stiffnessA * stiffnessB / (stiffnessA + stiffnessB)

            val force = cellStrengthAverage * (1f - distanceSquared / radiusSquared)
            // Нормализация вектора расстояния — умножением на обратную длину.
            val vectorX = dirX * force
            val vectorY = dirY * force

            velocitiesX[particleAId] += vectorX
            velocitiesY[particleAId] += vectorY
            velocitiesX[particleBId] -= vectorX
            velocitiesY[particleBId] -= vectorY
        }
    }

    companion object {
        const val PARTICLE_MAX_RADIUS = 0.5f
        const val PARTICLE_MAX_RADIUS_SQUARED = 0.25f

        /**
         * Минимальное расстояние, на котором ещё считается направление отталкивания.
         *
         * Ниже него нормализация вырождается: 1/distance уходит в бесконечность, а при
         * ровном нуле направление становится NaN. Значение выбрано так, чтобы быть на
         * порядки меньше любого физически осмысленного расстояния (радиусы 0.1..0.5),
         * но заметно больше нуля для float.
         */
        const val MIN_SEPARATION = 1e-4f
        const val MIN_SEPARATION_SQUARED = MIN_SEPARATION * MIN_SEPARATION

        /**
         * Квадрат предельного расстояния, на котором пересечение ещё возможно: сумма двух
         * максимальных радиусов. Дальше него столкновения быть не может при любых радиусах,
         * поэтому предфильтр в repulse отсеивает по нему, не читая radius[a] и radius[b].
         *
         * Было 4f, то есть предел 2. Это не давало неверных результатов — слишком свободный
         * предфильтр лишь пропускает дальше пары, которые всё равно отсеет точная проверка,
         * — но не отсекало и ничего: обход соседей идёт по ±1 клетке при размере клетки в
         * 1 мировую единицу, поэтому расстояние между любой проверяемой парой и так меньше
         * 2 ПО ПОСТРОЕНИЮ. Проверка была мёртвой, а каждый кандидат доходил до чтения обоих
         * радиусов, чтобы отсеяться уже на точной проверке.
         *
         * ЭТО НЕ НОВОЕ ДОПУЩЕНИЕ. Обход ±1 клетки сам по себе уже требует, чтобы сумма
         * радиусов не превышала размер клетки: иначе две частицы, которые должны
         * столкнуться, могли бы оказаться в клетках через одну и не встретиться вовсе.
         * Константа лишь делает это требование явным, поэтому и выражена через
         * PARTICLE_MAX_RADIUS, а не числом.
         *
         * Само требование проверяется в repulse под DEBUG_CHECKS: радиус приходит в том
         * числе из генома (Genome.radius), где ничем не ограничен.
         */
        const val MAX_RADIUS_SQUARED =
            (PARTICLE_MAX_RADIUS + PARTICLE_MAX_RADIUS) * (PARTICLE_MAX_RADIUS + PARTICLE_MAX_RADIUS)
    }
}
