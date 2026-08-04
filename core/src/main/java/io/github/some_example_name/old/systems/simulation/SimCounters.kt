package io.github.some_example_name.old.systems.simulation

/**
 * Счётчики объёма работы, разложенные по слотам (= по воркерам) с разносом по кэш-линиям.
 *
 * ПОЧЕМУ ГЛОБАЛЬНЫЙ ОБЪЕКТ
 * -----------------------
 * Это измерительные леса, а не часть модели: их надо уметь снести одной правкой флага,
 * не таща ссылку через конструкторы CollisionManager, ParticlePhysicsSystem и всех, кто
 * появится дальше. При PROFILE_COUNTERS = false все обращения вырезаются компилятором,
 * и объект просто никогда не инициализируется.
 *
 * ПОЧЕМУ STRIDE 16
 * ----------------
 * 16 long = 128 байт, то есть заведомо больше кэш-линии (64 байта на x86 и ARM). Счётчики
 * ОДНОГО слота лежат в одной линии — это нормально, писать в них будет только один поток.
 * Разные слоты гарантированно в разных линиях, иначе инкременты с восьми ядер гоняли бы
 * одну линию по кругу, и измерение стоило бы дороже измеряемого (ровно те грабли, что
 * были с oddCellCounter).
 *
 * ПРО ВИДИМОСТЬ
 * -------------
 * Воркеры пишут в свои слоты, главный поток читает их в [take] уже после барьера стадии.
 * Барьер — это getAndDecrement/чтение PENDING в ParallelExecutor, то есть volatile-RMW и
 * volatile-чтение, дающие release/acquire. Поэтому обычного LongArray достаточно, атомики
 * не нужны — тот же аргумент, по которому главный поток видит записи в vx/vy.
 */
object SimCounters {

    /** Пар-кандидатов перебрано широкой фазой. */
    const val PAIR_CANDIDATES = 0

    /** Из них реально пересеклись (прошли distanceSquared < radiusSquared). */
    const val COLLISIONS = 1

    /** Из пересечений отброшено, потому что клетки связаны пружиной. */
    const val LINKED_SKIPS = 2

    /** Вызовов onContact (мегаморфная диспетчеризация по 27 типам клеток). */
    const val CONTACTS = 3

    /**
     * Связей, разорванных за тик (мёртвая клетка или превышение длины).
     *
     * Если это число близко к нулю, значит проверка "жива ли связь" в начале processLink —
     * четыре чтения из холодных массивов на КАЖДУЮ связь ради события, которого почти
     * никогда не происходит. Тогда упаковка alive+generation в одно слово окупается,
     * а событийная инвалидация при удалении клетки окупается ещё лучше.
     */
    const val LINK_BREAKS = 4

    /**
     * Вызовов processCellAngle. Для дерева связей родитель совпадает примерно у одной
     * из двух проверок, то есть это ~1 вызов на связь — отдельная статья расходов внутри
     * фазы связей, которую по времени фазы не видно.
     */
    const val LINK_ANGLES = 5

    const val COUNT = 6

    /** Слотов с запасом: при сетке 512 в высоту их было бы 32. */
    const val MAX_SLOTS = 128

    private const val STRIDE = 16

    private val data = LongArray(MAX_SLOTS * STRIDE)

    fun add(slot: Int, counter: Int, value: Long) {
        if (slot >= MAX_SLOTS) return
        data[slot * STRIDE + counter] += value
    }

    fun increment(slot: Int, counter: Int) {
        if (slot >= MAX_SLOTS) return
        data[slot * STRIDE + counter]++
    }

    /** Сумма по слотам с обнулением. Вызывать только из потока симуляции между стадиями. */
    fun take(counter: Int): Long {
        var total = 0L
        for (slot in 0 until MAX_SLOTS) {
            val index = slot * STRIDE + counter
            total += data[index]
            data[index] = 0
        }
        return total
    }
}
