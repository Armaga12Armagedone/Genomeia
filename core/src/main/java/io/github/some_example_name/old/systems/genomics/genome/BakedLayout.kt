package io.github.some_example_name.old.systems.genomics.genome

import io.github.some_example_name.old.core.utils.OrderedIntPairMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Запечённая раскладка организма: в каком порядке его клетки, связи и нейросвязи должны
 * лечь в арены.
 *
 * ЗАЧЕМ
 * -----
 * Арена даёт организму непрерывный кусок массивов, но порядок ВНУТРИ куска определялся
 * порядком роста — то есть тем, в какой последовательности геном делит клетки. Для кэша
 * важен не он, а ширина ленты графа связей: максимальное |slotA - slotB| по всем связям.
 * Именно она задаёт размер окна, которое фаза связей держит горячим.
 *
 * У порядка роста ширина ленты может выродиться до O(n) — достаточно генома, растящего
 * тело «в длину», отросток за отростком. Обратный алгоритм Катхилла-Макки приводит её
 * к O(sqrt(n)) для плоских сеток, то есть окно перестаёт зависеть от размера организма:
 * тело на 1500 клеток обходится тем же килобайтом горячих данных, что и на 150.
 *
 * ПОЧЕМУ ЭТО МОЖНО ПЕЧЬ ЗАРАНЕЕ
 * ----------------------------
 * Геном полностью определяет, какие клетки вырастут и как они будут связаны: топология
 * взрослого тела от физики не зависит, физика меняет только координаты. Значит порядок
 * считается один раз при сохранении генома в редакторе, где организм уже выращен целиком,
 * и дальше просто читается. Стоимость сортировки в рантайме — ноль.
 *
 * ЧЕМ АДРЕСУЕМСЯ
 * --------------
 * Индексами клеток здесь пользоваться нельзя: в редакторе и в симуляции они разные.
 * Устойчивый идентификатор клетки внутри генома — это cellGenomeId (по нему же построена
 * CellEntity.organToIdToIndex, и он уникален в пределах организма). Связь адресуется парой
 * cellGenomeId своих концов.
 *
 * ФОРМАТ
 * ------
 * Списки хранят ЗНАЧЕНИЯ в порядке слотов: позиция в списке и есть смещение внутри арены.
 * Обратные карты (идентификатор -> слот), которые нужны в рантайме, строятся один раз при
 * загрузке — держать их в файле смысла нет, они вдвое больше.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BakedLayout(
    /** Слот k занимает клетка с cellGenomeId == cellGenomeIdsInSlotOrder[k]. */
    @ProtoNumber(1) val cellGenomeIdsInSlotOrder: List<Int> = emptyList(),

    /**
     * Пары cellGenomeId концов, разложенные подряд: слот k — это элементы 2k и 2k+1.
     *
     * Плоский список, а не список пар: protobuf для List<Pair> завёл бы вложенное сообщение
     * с заголовком на каждую связь, а связей втрое больше, чем клеток.
     */
    @ProtoNumber(2) val linkPairsInSlotOrder: List<Int> = emptyList(),

    /** То же для нейросвязей. */
    @ProtoNumber(3) val neuralLinkPairsInSlotOrder: List<Int> = emptyList()
) {

    /**
     * cellGenomeId -> смещение слота в арене клеток.
     *
     * -1 для незнакомого идентификатора: геном мог мутировать после запекания, и клетка,
     * которой в раскладке нет, должна честно уйти на общий bump-путь, а не занять чужой слот.
     */
    @Transient
    val slotByCellGenomeId: Int2IntOpenHashMap =
        Int2IntOpenHashMap(cellGenomeIdsInSlotOrder.size).apply {
            defaultReturnValue(-1)
            cellGenomeIdsInSlotOrder.forEachIndexed { slot, genomeId -> put(genomeId, slot) }
        }

    /**
     * Пара cellGenomeId -> смещение слота в арене связей.
     *
     * Кладётся в обоих порядках: морфогенез создаёт связь той стороной, какой ему удобно,
     * а слот у неё один и тот же.
     */
    @Transient
    val slotByLinkPair: OrderedIntPairMap = buildPairMap(linkPairsInSlotOrder)

    @Transient
    val slotByNeuralLinkPair: OrderedIntPairMap = buildPairMap(neuralLinkPairsInSlotOrder)

    /** Сколько слотов реально занято раскладкой — ёмкость арены считается от них. */
    val cellsInLayout: Int get() = cellGenomeIdsInSlotOrder.size
    val linksInLayout: Int get() = linkPairsInSlotOrder.size / 2
    val neuralLinksInLayout: Int get() = neuralLinkPairsInSlotOrder.size / 2

    private companion object {
        fun buildPairMap(flatPairs: List<Int>): OrderedIntPairMap {
            val map = OrderedIntPairMap(flatPairs.size)
            var slot = 0
            var i = 0
            while (i + 1 < flatPairs.size) {
                val a = flatPairs[i]
                val b = flatPairs[i + 1]
                map.put(a, b, slot)
                map.put(b, a, slot)
                slot++
                i += 2
            }
            return map
        }
    }
}
