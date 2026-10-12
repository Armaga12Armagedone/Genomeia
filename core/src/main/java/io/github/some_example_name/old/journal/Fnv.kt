package io.github.some_example_name.old.journal

/**
 * 64-битный FNV-1a — для контрольных сумм журнала и состояния мира.
 *
 * Не криптография: нужен быстрый хэш, у которого случайное совпадение двух разных
 * потоков данных практически исключено (2^-64), а считать его можно на лету, по байту
 * или по целому числу, без буферов.
 */
object Fnv {
    const val OFFSET: Long = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val PRIME: Long = 0x100000001b3L

    fun byte(hash: Long, value: Int): Long = (hash xor (value.toLong() and 0xFF)) * PRIME

    fun int(hash: Long, value: Int): Long {
        var h = byte(hash, value)
        h = byte(h, value ushr 8)
        h = byte(h, value ushr 16)
        return byte(h, value ushr 24)
    }

    /**
     * Целое число за один шаг (FNV-1a по 32-битным словам, а не по байтам): в хэш
     * состояния мира идут миллионы чисел за раз, и вчетверо меньше умножений заметно.
     */
    fun word(hash: Long, value: Int): Long = (hash xor (value.toLong() and 0xFFFFFFFFL)) * PRIME

    fun long(hash: Long, value: Long): Long = int(int(hash, value.toInt()), (value ushr 32).toInt())

    fun float(hash: Long, value: Float): Long = int(hash, java.lang.Float.floatToRawIntBits(value))

    fun bytes(hash: Long, data: ByteArray, from: Int = 0, to: Int = data.size): Long {
        var h = hash
        for (i in from until to) h = (h xor (data[i].toLong() and 0xFF)) * PRIME
        return h
    }

    fun hex(hash: Long): String = java.lang.Long.toHexString(hash).padStart(16, '0')
}
