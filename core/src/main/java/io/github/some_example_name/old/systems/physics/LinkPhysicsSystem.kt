package io.github.some_example_name.old.systems.physics

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.commands.WorldCommandType
import io.github.some_example_name.old.core.DEBUG_CHECKS
import io.github.some_example_name.old.core.PROFILE_COUNTERS
import io.github.some_example_name.old.core.DIContext
import io.github.some_example_name.old.core.DISimulationContainer.linkMaxLength2
import io.github.some_example_name.old.core.DISimulationContainer.threadManager
import io.github.some_example_name.old.core.SubstrateSettings
import io.github.some_example_name.old.core.utils.invSqrt
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.systems.genomics.CellSystem
import io.github.some_example_name.old.systems.simulation.Phase
import io.github.some_example_name.old.systems.simulation.SimCounters
import io.github.some_example_name.old.systems.simulation.SimulationData
import it.unimi.dsi.fastutil.ints.IntArrayList

class LinkPhysicsSystem(
    val linkEntity: LinkEntity,
    val particleEntity: ParticleEntity,
    val substrateSettings: SubstrateSettings,
    val cellEntity: CellEntity,
    val cellSystem: CellSystem,
    val worldCommandsManager: WorldCommandsManager,
    val diContext: DIContext,
    val simulationData: SimulationData
) {

    fun iterateLinksInParallel() {
        processPhase(worldCommandsManager.oddLinkLists)
        processPhase(worldCommandsManager.evenLinkLists)
    }

    /**
     * Связи заранее разложены по слотам (слот = пространственный чанк одной чётности),
     * поэтому здесь остаётся только раздать слоты воркерам.
     *
     * Раздача динамическая: в одном слоте может быть несколько тысяч связей, в соседнем
     * десяток, а стадия заканчивается по самому загруженному слоту — при статической
     * привязке "слот i потоку i" остальные ядра просто стояли на барьере. Плюс сам барьер
     * теперь спиновый: без submit, аллокации FutureTask и парковки потоков.
     */
    private fun processPhase(lists: Array<IntArrayList>) {
        threadManager.runSlotStage(lists.size, Phase.LINKS) { slot ->
            val list = lists[slot]
            for (i in list.indices) {
                processLink(list.getInt(i), slot)
            }
        }
    }

    /**
     * Обработка одной связи. Вызывается для каждой связи каждый тик, из нескольких потоков.
     *
     * Раньше тело метода жило в трёх вложенных with (particleEntity, cellEntity, linkEntity),
     * из-за чего каждое обращение вида x[i] или links1[i] превращалось в чтение поля
     * соответствующего объекта плюс чтение элемента. Поля сущностей — var (их
     * переоткрывают при росте), а в середине метода стоят вызовы transportEnergy,
     * processCellAngle и reinitParentIndex, которые для JIT могут записать что угодно
     * в любое поле, поэтому после каждого вызова все ссылки на массивы приходилось
     * перечитывать. Теперь массивы поднимаются в локальные переменные (регистры) один раз.
     *
     * Инвариант тот же, что и в repulse: в параллельной фазе массивы не пересоздаются,
     * меняются только элементы, а рост сущностей идёт в однопоточной фазе применения команд.
     */
    fun processLink(linkIndex: Int, threadId: Int = 0) {
        val linkEntity = linkEntity
        val cellEntity = cellEntity
        val particleEntity = particleEntity

        val linkCellA = linkEntity.links1[linkIndex]
        val linkCellB = linkEntity.links2[linkIndex]

        // Проверки "жива ли клетка на конце связи" здесь больше нет.
        //
        // Она стоила шесть чтений из шести разных массивов (isAlive дважды, generation
        // клетки дважды, linksGeneration1/2 связи) на КАЖДУЮ связь КАЖДЫЙ тик — то есть
        // примерно треть всех обращений к памяти в этом методе, — и вся эта работа была
        // пропорциональна числу связей в мире, хотя само событие пропорционально числу
        // смертей. Теперь связи умирающей клетки снимаются сразу, в LinkEntity.detachAllLinks
        // из обработчика DELETE_CELL, и до этого метода мёртвая связь просто не доходит:
        // detachAllLinks убирает её из списков слотов до начала следующего тика.
        //
        // Инвариант, на котором это держится: единственный путь смерти клетки — команда
        // DELETE_CELL, а она однопоточная и идёт до фазы связей следующего тика.
        //
        // Если инвариант когда-нибудь нарушат (появится ещё один путь смерти, который не
        // зовёт detachAllLinks), симуляция начнёт молча считать физику по мёртвым индексам.
        // Поэтому под DEBUG_CHECKS он проверяется явно — включать при любой правке путей
        // жизненного цикла клетки.
        if (DEBUG_CHECKS) {
            val isAlive = cellEntity.isAlive
            if (!isAlive[linkCellA] || !isAlive[linkCellB] ||
                cellEntity.getGeneration(linkCellA) != linkEntity.linksGeneration1[linkIndex] ||
                cellEntity.getGeneration(linkCellB) != linkEntity.linksGeneration2[linkIndex]
            ) {
                throw IllegalStateException(
                    "живая связь $linkIndex ссылается на мёртвую клетку: " +
                        "A=$linkCellA B=$linkCellB — detachAllLinks не был вызван"
                )
            }
        }

        // Денормализовать индексы частиц в саму связь пробовали — стало медленнее,
        // подробности в комментарии к LinkEntity.links1. Эти два обращения случайные,
        // но particleIndexes всего ~780 КБ и живёт в L2/L3, так что они дёшевы.
        val linkParticleA = cellEntity.getParticleIndex(linkCellA)
        val linkParticleB = cellEntity.getParticleIndex(linkCellB)

        val positionsX = particleEntity.x
        val positionsY = particleEntity.y

        val dx = positionsX[linkParticleA] - positionsX[linkParticleB]
        val dy = positionsY[linkParticleA] - positionsY[linkParticleB]
        val distanceSquared = dx * dx + dy * dy

        cellSystem.transportEnergy(linkCellA, linkCellB)

        val parentIndices = cellEntity.parentIndex
        val parentCellA = parentIndices[linkCellA]
        val parentCellB = parentIndices[linkCellB]
        if (linkCellA == parentCellB) {
            if (PROFILE_COUNTERS) SimCounters.increment(threadId, SimCounters.LINK_ANGLES)
            cellSystem.processCellAngle(linkCellB, linkCellA)
        }
        if (linkCellB == parentCellA) {
            if (PROFILE_COUNTERS) SimCounters.increment(threadId, SimCounters.LINK_ANGLES)
            cellSystem.processCellAngle(linkCellA, linkCellB)
        }

        if (distanceSquared > linkMaxLength2) {
            if (PROFILE_COUNTERS) SimCounters.increment(threadId, SimCounters.LINK_BREAKS)
            linkEntity.reinitParentLink(linkIndex)
            worldCommandsManager.worldCommandBuffer[threadId].push(
                WorldCommandType.DELETE_LINK,
                linkIndex,
                linkEntity.getGeneration(linkIndex)
            )
            cellEntity.isOnEdge[linkCellB] = true
            cellEntity.setColor(linkCellB, Color.RED.toIntBits())
            cellEntity.isOnEdge[linkCellA] = true
            cellEntity.setColor(linkCellA, Color.RED.toIntBits())
            return
        }

        val cellStiffness = particleEntity.cellStiffness
        val stiffnessA = cellStiffness[linkParticleA]
        val stiffnessB = cellStiffness[linkParticleB]
        // Гармоническое среднее. У клеток одного типа значения совпадают — самый частый
        // случай, — поэтому равенство проверяется отдельно и деление пропускается по
        // хорошо предсказываемой ветке.
        val stiffness = if (stiffnessA == stiffnessB) stiffnessA
        else 2f * stiffnessA * stiffnessB / (stiffnessA + stiffnessB)

        // Отладочная проверка: distanceSquared это сумма двух квадратов, отрицательной
        // она может стать только при NaN/inf в координатах. При DEBUG_CHECKS = false
        // конструкция вырезается компилятором, в байткоде не остаётся ни сравнения,
        // ни конкатенации строки, ни throw внутри горячего метода.
        if (DEBUG_CHECKS && distanceSquared < 0) {
            throw Exception("distanceSquared < 0, distanceSquared = $distanceSquared")
        }

        val invDist = invSqrt(distanceSquared)
        val dist = distanceSquared * invDist

        val dirX = dx * invDist
        val dirY = dy * invDist

        val degreeOfShorteningArray = cellEntity.degreeOfShortening
        val degreeOfShorteningA = degreeOfShorteningArray[linkCellA]
        val degreeOfShorteningB = degreeOfShorteningArray[linkCellB]
        val degreeOfShortening = if (degreeOfShorteningA == degreeOfShorteningB) degreeOfShorteningA
        else 2f * degreeOfShorteningA * degreeOfShorteningB /
            (degreeOfShorteningA + degreeOfShorteningB)

        val force =
            (dist - linkEntity.linksNaturalLength[linkIndex] * degreeOfShortening) * stiffness

        // Spring dampening
        val velocitiesX = particleEntity.vx
        val velocitiesY = particleEntity.vy

        val dvx = velocitiesX[linkParticleA] - velocitiesX[linkParticleB]
        val dvy = velocitiesY[linkParticleA] - velocitiesY[linkParticleB]

        val dampeningConstant = 0.3f
        val dampeningForce = dampeningConstant * (dvx * dirX + dvy * dirY)

        val totalForce = force + dampeningForce
        val fx = totalForce * dirX
        val fy = totalForce * dirY

        velocitiesX[linkParticleB] += fx
        velocitiesY[linkParticleB] += fy
        velocitiesX[linkParticleA] -= fx
        velocitiesY[linkParticleA] -= fy

        // Элементы читаются заново (а не берутся из parentCellA/B): reinitParentIndex
        // мог только что записать сюда значение.
        if (parentIndices[linkCellA] == -1) linkEntity.reinitParentIndex(linkCellA, linkCellB)
        if (parentIndices[linkCellB] == -1) linkEntity.reinitParentIndex(linkCellB, linkCellA)

    }
}
