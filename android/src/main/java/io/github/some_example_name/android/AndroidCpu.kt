package io.github.some_example_name.android

import java.io.File

/**
 * Определение числа ядер, пригодных под спинящих воркеров симуляции.
 *
 * ПОЧЕМУ НЕ availableProcessors()
 * -------------------------------
 * ART реализует его через sysconf(_SC_NPROCESSORS_CONF), то есть возвращает все ядра всех
 * кластеров, без учёта аффинити процесса. На типичном 1+3+4 это 8, из которых четыре —
 * little-ядра, вдвое-втрое медленнее больших. Для пула со спин-барьером это плохо дважды:
 * воркер на медленном ядре дольше держит стадию, а остальные в это время не спят, а жгут
 * такты на барьере.
 *
 * КАК СЧИТАЕТСЯ
 * -------------
 * По cpuinfo_max_freq каждого ядра. Порог в 85% от максимальной частоты по системе
 * отсекает little-кластер, но оставляет mid: на 1+3+4 это даёт 4 (X-ядро плюс три A7xx),
 * что близко к правде — mid-ядра по производительности сопоставимы с большим, а little
 * отстают качественно, а не количественно.
 *
 * Читается один раз при старте: cpufreq на некоторых прошивках лежит в sysfs с заметной
 * задержкой на открытие, и делать это на горячем пути незачем.
 *
 * ЕСЛИ /sys ЗАКРЫТ
 * ----------------
 * На части устройств доступ к cpufreq ограничен политикой SELinux. Тогда возвращается
 * половина ядер: на всех актуальных SoC little-кластер занимает от трети до половины,
 * так что это консервативная, но не абсурдная оценка.
 */
object AndroidCpu {

    /** Ядра «быстрых» кластеров. Минимум 1, максимум — общее число ядер. */
    fun performanceCoreCount(): Int {
        val total = Runtime.getRuntime().availableProcessors()
        if (total <= 2) return total

        val freqs = IntArray(total) { readMaxFreq(it) }
        val max = freqs.max()
        if (max == 0) return maxOf(2, total / 2)

        val threshold = (max * 0.85).toInt()
        return freqs.count { it >= threshold }.coerceIn(1, total)
    }

    private fun readMaxFreq(cpu: Int): Int =
        try {
            File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")
                .readText()
                .trim()
                .toInt()
        } catch (_: Throwable) {
            0
        }
}
