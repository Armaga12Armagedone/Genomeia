package io.github.some_example_name.old.systems.genomics.genome

/**
 * Номера типов клеток и формул, какими они были в файлах до строковых ключей:
 * .genome формата 1, геномы JSON и журналы .bug alpha-0.2.4 (миры с номерами в релиз не
 * выходили — MapSave их не читает).
 *
 * ТАБЛИЦА ЗАМОРОЖЕНА. Это не текущий порядок (он в CellTypes / Formulas и может меняться),
 * а словарь для чтения старых файлов: номер N в старом файле означает ровно этот ключ.
 * Удалили клетку — её строка здесь остаётся (старые файлы с ней всё ещё существуют);
 * новые клетки сюда не попадают никогда — в файлах они сразу пишутся ключом.
 */
object LegacyTypeIds {

    val CELL_TYPES = arrayOf(
        "leaf",              // 0
        "fat",               // 1
        "bone",              // 2
        "tail",              // 3
        "neuron",            // 4
        "muscle",            // 5
        "food_sensor",       // 6
        "sucker",            // 7
        "stem",              // 8
        "excreta",           // 9
        "suction_cup",       // 10
        "sticky",            // 11
        "pumper",            // 12
        "chameleon",         // 13
        "eye",               // 14
        "compass",           // 15
        "synapse",           // 16
        "touch_trigger",     // 17
        "zygote",            // 18
        "producer",          // 19
        "breakaway",         // 20
        "fin",               // 21
        "pheromone_emitter", // 22
        "pheromone_sensor",  // 23
        "punisher",          // 24
        "speed_sensor",      // 25
    )

    val FORMULAS = arrayOf(
        "linear",       // 0  y = ax + b
        "sin",          // 1  y = c * sin(ax + b)
        "cos",          // 2  y = c * cos(ax + b)
        "sigmoid",      // 3  y = sigmoid(ax + b) + c
        "step_le",      // 4  y = b, x <= a; y = c, x > a
        "step_lt",      // 5  y = b, x < a; y = c, x >= a
        "time",         // 6  y = t
        "impulse",      // 7  y = impulse(a), x>=1
        "in_range",     // 8  y = x in (a, b) else y = c
        "pow",          // 9  y = x^(a)
        "remember",     // 10 y = remember(x), x > 0
        "random",       // 11 y = random(a, b)
        "red",          // 12 y = r(x)
        "green",        // 13 y = g(x)
        "blue",         // 14 y = b(x)
        "energy",       // 15 y = energy
        "controller",   // 16 y = controller(a)
        "decay",        // 17 y = decay(x - a), (b, c)
        "spike",        // 18 y = spike(x) = 1, dx >= a
        "abs",          // 19 y = |x|
        "links_amount", // 20 y = links amount
    )

    fun cellType(number: Int): String =
        CELL_TYPES.getOrNull(number) ?: throw IllegalStateException("в файле неизвестный старый номер типа клетки $number")

    /**
     * Ключ формулы по старому номеру. Номера вне таблицы старая симуляция считала
     * тождеством (ветка else в activation), поэтому и здесь они становятся "identity".
     */
    fun formula(number: Int): String = FORMULAS.getOrNull(number) ?: IDENTITY_FORMULA

    const val IDENTITY_FORMULA = "identity"
}
