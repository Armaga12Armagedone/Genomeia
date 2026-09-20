package io.github.some_example_name.old.commands

/**
 * Пер-поточный буфер отложенных команд.
 *
 * Про false sharing: буферы создаются пачкой (Array(threadCount) { WorldCommandBuffer() }),
 * поэтому в памяти лежат подряд. Сам объект без padding'а занимает ~40 байт (заголовок +
 * 4 ссылки на массивы + int size), то есть в одну 64-байтную кэш-линию попадают поля
 * size СРАЗУ НЕСКОЛЬКИХ буферов. Каждый push() пишет size, и эта линия начинает
 * мотаться между ядрами (RFO + инвалидация в чужих L1) — при том, что логически
 * потоки не разделяют ничего.
 *
 * Лечим padding'ом: по 16 int (64 байта) до и после size, так что поле гарантированно
 * лежит в линии, где кроме наших же байтов ничего нет. Поля одного размера HotSpot
 * раскладывает в порядке объявления, поэтому padding реально окружает size.
 * Цена — ~128 байт на поток (всего ~2 КБ), то есть ноль.
 */
class WorldCommandBuffer (initialCapacity: Int = 1000) {  // Начальный размер — на 1000 команд
    // Массив типов команд (int — ordinal enum)
    var commandTypes = IntArray(initialCapacity) { -1 }  // -1 = пусто

    // Параметры: большие массивы, где для каждой команды — фиксированный слот (type.ordinal * MAX_PARAMS + offset)
    var intParams = IntArray(initialCapacity * WorldCommandType.MAX_INT_PARAMS) { 0 }
    var floatParams = FloatArray(initialCapacity * WorldCommandType.MAX_FLOAT_PARAMS) { 0f }
    var booleanParams = BooleanArray(initialCapacity * WorldCommandType.MAX_BOOLEAN_PARAMS) { false }

    // --- 64 байта padding перед size (не использовать) ---
    @JvmField var pad00 = 0; @JvmField var pad01 = 0; @JvmField var pad02 = 0; @JvmField var pad03 = 0
    @JvmField var pad04 = 0; @JvmField var pad05 = 0; @JvmField var pad06 = 0; @JvmField var pad07 = 0
    @JvmField var pad08 = 0; @JvmField var pad09 = 0; @JvmField var pad10 = 0; @JvmField var pad11 = 0
    @JvmField var pad12 = 0; @JvmField var pad13 = 0; @JvmField var pad14 = 0; @JvmField var pad15 = 0

    // Текущий размер (кол-во команд в буфере). Пишется на каждый push из своего потока.
    var size = 0

    // --- 64 байта padding после size (не использовать) ---
    @JvmField var pad16 = 0; @JvmField var pad17 = 0; @JvmField var pad18 = 0; @JvmField var pad19 = 0
    @JvmField var pad20 = 0; @JvmField var pad21 = 0; @JvmField var pad22 = 0; @JvmField var pad23 = 0
    @JvmField var pad24 = 0; @JvmField var pad25 = 0; @JvmField var pad26 = 0; @JvmField var pad27 = 0
    @JvmField var pad28 = 0; @JvmField var pad29 = 0; @JvmField var pad30 = 0; @JvmField var pad31 = 0


    // Добавление команды (push)
    fun push(type: WorldCommandType, ints: IntArray? = null, floats: FloatArray? = null, booleans: BooleanArray? = null) {
        if (size >= commandTypes.size) resize()  // Авто-ресайз

        val baseIndex = size
        commandTypes[baseIndex] = type.ordinal

        // Копируем параметры в слоты (только нужное кол-во, остальное игнорируем)
        ints?.let { System.arraycopy(it, 0, intParams, baseIndex * WorldCommandType.MAX_INT_PARAMS, minOf(it.size, type.intParamsCount)) }
        floats?.let { System.arraycopy(it, 0, floatParams, baseIndex * WorldCommandType.MAX_FLOAT_PARAMS, minOf(it.size, type.floatParamsCount)) }
        booleans?.let { System.arraycopy(it, 0, booleanParams, baseIndex * WorldCommandType.MAX_BOOLEAN_PARAMS, minOf(it.size, type.booleanParamsCount)) }

        size++
    }

    /**
     * Скалярные перегрузки push для горячих путей.
     *
     * Обычный push(type, ints = intArrayOf(a, b)) на каждую команду:
     *  - аллоцирует IntArray в eden (заголовок 16 байт + данные, ~24-32 байта),
     *  - заполняет его,
     *  - вызывает System.arraycopy, чтобы переложить 1-3 int'а (вызов intrinsic'а
     *    с проверками границ и типов оправдан на килобайтах, а не на 8 байтах),
     *  - оставляет мусор, который потом собирает GC.
     * Escape analysis тут не спасает: массив передаётся в чужой метод, который его
     * копирует, а push при таком количестве вызовов не всегда инлайнится.
     * Команды летят из onContact и из физики связей, то есть десятки тысяч за тик
     * на все потоки — это заметный поток аллокаций, а аллокация это ещё и запись
     * в свежую (холодную) кэш-линию eden'а.
     *
     * Здесь значения пишутся прямо в слот буфера: ни аллокации, ни arraycopy, ни мусора.
     * Слот всегда вмещает 3 int'а: MAX_INT_PARAMS заведомо больше.
     */
    fun push(type: WorldCommandType, int0: Int) {
        if (size >= commandTypes.size) resize()

        val index = size
        commandTypes[index] = type.ordinal
        intParams[index * WorldCommandType.MAX_INT_PARAMS] = int0

        size = index + 1
    }

    fun push(type: WorldCommandType, int0: Int, int1: Int) {
        if (size >= commandTypes.size) resize()

        val index = size
        commandTypes[index] = type.ordinal
        val base = index * WorldCommandType.MAX_INT_PARAMS
        intParams[base] = int0
        intParams[base + 1] = int1

        size = index + 1
    }

    fun push(type: WorldCommandType, int0: Int, int1: Int, int2: Int) {
        if (size >= commandTypes.size) resize()

        val index = size
        commandTypes[index] = type.ordinal
        val base = index * WorldCommandType.MAX_INT_PARAMS
        intParams[base] = int0
        intParams[base + 1] = int1
        intParams[base + 2] = int2

        size = index + 1
    }


    // Обработка всех команд (consume) — итерация и вызов обработчика
    inline fun consume(processor: (WorldCommandType, IntArray, FloatArray, BooleanArray) -> Unit) {
        val tempInts = IntArray(WorldCommandType.MAX_INT_PARAMS)
        val tempFloats = FloatArray(WorldCommandType.MAX_FLOAT_PARAMS)
        val tempBooleans = BooleanArray(WorldCommandType.MAX_BOOLEAN_PARAMS)

        for (i in 0 until size) {
            val typeOrdinal = commandTypes[i]
            if (typeOrdinal == -1) continue  // Пусто

            val type = WorldCommandType.entries[typeOrdinal]
            val baseInt = i * WorldCommandType.MAX_INT_PARAMS
            val baseFloat = i * WorldCommandType.MAX_FLOAT_PARAMS
            val baseBool = i * WorldCommandType.MAX_BOOLEAN_PARAMS

            // Копируем параметры в темповые массивы (для безопасности, если processor мутирует)
            System.arraycopy(intParams, baseInt, tempInts, 0, type.intParamsCount)
            System.arraycopy(floatParams, baseFloat, tempFloats, 0, type.floatParamsCount)
            System.arraycopy(booleanParams, baseBool, tempBooleans, 0, type.booleanParamsCount)

            processor(type, tempInts, tempFloats, tempBooleans)
        }
        clear()
    }

    // Очистка буфера
    fun clear() {
        size = 0
        // Не нужно fill(-1), т.к. при push перезапишем
    }

    private fun resize() {
        val newCapacity = commandTypes.size * 2
        commandTypes = commandTypes.copyOf(newCapacity).apply { fill(-1, size, newCapacity) }
        intParams = intParams.copyOf(newCapacity * WorldCommandType.MAX_INT_PARAMS)
        floatParams = floatParams.copyOf(newCapacity * WorldCommandType.MAX_FLOAT_PARAMS)
        booleanParams = booleanParams.copyOf(newCapacity * WorldCommandType.MAX_BOOLEAN_PARAMS)
    }
}
