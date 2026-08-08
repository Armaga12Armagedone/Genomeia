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

    /**
     * Одна стадия на все связи: слот обрабатывает оба своих списка, нечётный и чётный.
     *
     * ПОЧЕМУ ЧЁТНОСТЬ БОЛЬШЕ НЕ НУЖНА
     * -------------------------------
     * Две стадии существовали ради корректности: слот задавался пространственной полосой,
     * связь дотягивалась на длину пружины за её границу, поэтому соседние полосы нельзя
     * было считать одновременно — их разводили по чётности.
     *
     * Теперь слот задаётся якорем организма (CellEntity.bodyAnchorY), и все связи одного
     * тела лежат в одном слоте. Конфликтовать могут только линки с ОБЩЕЙ КЛЕТКОЙ, а общие
     * клетки бывают исключительно внутри организма — значит любые два слота независимы,
     * и разводить их по стадиям незачем.
     *
     * ПОЧЕМУ ЭТО ЕЩЁ И БЫСТРЕЕ
     * -----------------------
     * Стадия заканчивается по самому загруженному слоту. Двумя стадиями цена была
     * max(нечётные) + max(чётные), одной стала max(нечётный + чётный) — а это никогда не
     * больше, потому что max(a + b) <= max(a) + max(b). Слот, тяжёлый по одной чётности,
     * обычно лёгкий по другой, и суммы выравниваются.
     *
     * Это лечит просадку балансировки, которая появилась вместе с якорной раскладкой:
     * организм теперь попадает в слот целиком, а размеры организмов разные, поэтому
     * util_links упал с 0.93 до 0.82.
     *
     * Плюс один барьер вместо двух.
     *
     * Число слотов при этом осталось прежним намеренно: slot уходит в processLink как
     * threadId и служит индексом в worldCommandBuffer, размер которого равен threadCount.
     * Удвоение слотов потребовало бы расширять все per-thread структуры, иначе два
     * одновременных слота начали бы писать в один буфер команд — новая гонка.
     *
     * Раздача слотов динамическая: в одном может быть несколько тысяч связей, в соседнем
     * десяток, и освободившийся воркер берёт следующий сам.
     */
    fun iterateLinksInParallel() {
        val oddLists = worldCommandsManager.oddLinkLists
        val evenLists = worldCommandsManager.evenLinkLists

        threadManager.runSlotStage(oddLists.size, Phase.LINKS) { slot ->
            val oddList = oddLists[slot]
            for (i in oddList.indices) {
                processLink(oddList.getInt(i), slot)
            }

            val evenList = evenLists[slot]
            for (i in evenList.indices) {
                processLink(evenList.getInt(i), slot)
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

            // Обе клетки связи обязаны принадлежать ОДНОМУ организму.
            //
            // Это и есть точное условие отсутствия гонки, без всяких допущений о геометрии.
            // Слот связи выбирается по якорю организма (CellEntity.bodyAnchorY), значит все
            // связи одного тела лежат в одном слоте и обрабатываются одним воркером
            // последовательно. Конфликтовать могут только линки с общей клеткой, а общие
            // клетки бывают исключительно внутри организма — следовательно, пока обе клетки
            // связи в одной системе отсчёта, гонка невозможна в принципе.
            //
            // Единственный способ это сломать — связать клетки разных организмов. Все пути
            // создания связей резолвят вторую клетку внутри одного organIndex, а морфогенез
            // вдобавок фильтрует по нему явно; проверка ловит любой путь, который это обошёл.
            //
            // Предыдущая версия сравнивала статические Y самих клеток с порогом
            // HALF_CHUNK_HEIGHT и ложно срабатывала на деформированных телах: карта тела
            // отражает форму на момент роста, а реальное тело со временем расходится с ней.
            if (cellEntity.bodyAnchorY[linkCellA] != cellEntity.bodyAnchorY[linkCellB]) {
                throw IllegalStateException(
                    "связь $linkIndex соединяет клетки разных организмов (по якорю): " +
                        "A=$linkCellA anchor=${cellEntity.bodyAnchorY[linkCellA]} " +
                        "organIndex=${cellEntity.organIndex[linkCellA]}, " +
                        "B=$linkCellB anchor=${cellEntity.bodyAnchorY[linkCellB]} " +
                        "organIndex=${cellEntity.organIndex[linkCellB]} " +
                        "— их связи попадут в разные слоты, возможна гонка на vx/vy"
                )
            }

            // Отдельно по organIndex. Якорь и organIndex — независимые признаки организма:
            // якорь ставится в addCell и наследуется от родителя, organIndex приходит из
            // команды и у зиготы какое-то время равен -1. Расхождение между ними само по
            // себе означает испорченную принадлежность клетки, даже если по якорю всё сошлось
            // (два разных организма могут случайно совпасть якорем — они спавнились на одной
            // высоте).
            if (cellEntity.organIndex[linkCellA] != cellEntity.organIndex[linkCellB]) {
                throw IllegalStateException(
                    "связь $linkIndex соединяет клетки разных организмов (по organIndex): " +
                        "A=$linkCellA organIndex=${cellEntity.organIndex[linkCellA]} " +
                        "anchor=${cellEntity.bodyAnchorY[linkCellA]}, " +
                        "B=$linkCellB organIndex=${cellEntity.organIndex[linkCellB]} " +
                        "anchor=${cellEntity.bodyAnchorY[linkCellB]}"
                )
            }
        }

        // Денормализовать индексы частиц в саму связь пробовали — стало медленнее,
        // подробности в комментарии к LinkEntity.links1. Эти два обращения случайные,
        // но particleIndexes всего ~780 КБ и живёт в L2/L3, так что они дёшевы.
        val linkParticleA = cellEntity.getParticleIndex(linkCellA)
        val linkParticleB = cellEntity.getParticleIndex(linkCellB)

        // Дубль проверки из LinkEntity.addLink, но уже по факту расчёта: ловит связи,
        // испортившиеся ПОСЛЕ создания.
        //
        // Ровно (0, 0) — точный признак удалённой частицы: ParticleEntity.deleteParticle
        // обнуляет x и y, а живая частица туда попасть не может, processWorldBorders
        // зажимает координаты в [radius, gridSize - radius] при ненулевом радиусе.
        if (DEBUG_CHECKS) {
            val particles = particleEntity
            if (linkParticleA == -1 || linkParticleB == -1) {
                throw IllegalStateException(
                    "связь $linkIndex ссылается на клетку без частицы: " +
                        "A=$linkCellA particle=$linkParticleA, B=$linkCellB particle=$linkParticleB"
                )
            }
            if (!particles.isAlive[linkParticleA] || !particles.isAlive[linkParticleB]) {
                throw IllegalStateException(
                    "связь $linkIndex ссылается на мёртвую частицу: " +
                        "A=$linkCellA particle=$linkParticleA alive=${particles.isAlive[linkParticleA]}, " +
                        "B=$linkCellB particle=$linkParticleB alive=${particles.isAlive[linkParticleB]}"
                )
            }
            if ((particles.x[linkParticleA] == 0f && particles.y[linkParticleA] == 0f) ||
                (particles.x[linkParticleB] == 0f && particles.y[linkParticleB] == 0f)
            ) {
                throw IllegalStateException(
                    "связь $linkIndex ссылается на частицу в нулевой координате: " +
                        "A=$linkCellA particle=$linkParticleA " +
                        "pos=(${particles.x[linkParticleA]}, ${particles.y[linkParticleA]}), " +
                        "B=$linkCellB particle=$linkParticleB " +
                        "pos=(${particles.x[linkParticleB]}, ${particles.y[linkParticleB]})"
                )
            }
        }

        val positionsX = particleEntity.x
        val positionsY = particleEntity.y

        val dx = positionsX[linkParticleA] - positionsX[linkParticleB]
        val dy = positionsY[linkParticleA] - positionsY[linkParticleB]
        val distanceSquared = dx * dx + dy * dy

        cellSystem.transportEnergy(linkCellA, linkCellB)

        //TODO можно попробовать сделать отдельную сущность под углы, там будут только пары родитель - потомок
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
