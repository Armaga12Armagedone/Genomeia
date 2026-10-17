package io.github.some_example_name.render.lab

import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL33

/**
 * Замер стоимости кадра рендера: один и тот же кадр [repeats] раз подряд.
 *
 * ЗАЧЕМ ПОВТОРЫ
 * -------------
 * Один кадр на 3060 стоит доли миллисекунды — это уровень шума: таймер, планировщик
 * драйвера, соседние процессы. Повтор того же рендера N раз за кадр поднимает полезный
 * сигнал в N раз, а шум остаётся прежним, и разницу между режимами становится видно.
 *
 * ЧЕМ МЕРИМ
 * ---------
 * Основная цифра — время GPU по таймер-запросу GL_TIME_ELAPSED (ядро GL 3.3, контекст у
 * стенда как раз 3.3). Это чистое время выполнения команд на видеокарте, без ожидания
 * vsync и без того, что творится на CPU. Рядом пишется настенное время CPU до готового
 * результата: оно включает ещё и подготовку команд драйвером, и если вдруг сильно
 * разойдётся с GPU — узкое место не в шейдерах.
 *
 * Результат запроса читается сразу, то есть блокирующе: для стенда это нормально, а
 * больше ничего в кадре и не происходит.
 */
internal class LabBenchmark(val repeats: Int) {

    class Sample(val gpuMs: Double, val cpuMs: Double)

    private val query = GL15.glGenQueries()

    /** Выполнить [block] [repeats] раз и вернуть время на ОДИН прогон. */
    fun measure(block: (repeat: Int) -> Unit): Sample {
        // Всё, что было до замера, должно закончиться до него, иначе попадёт в окно.
        GL15.glFinish()
        val cpuStart = System.nanoTime()
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query)
        repeat(repeats) { block(it) }
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED)
        val gpuNs = GL33.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT)
        val cpuNs = System.nanoTime() - cpuStart
        return Sample(
            gpuMs = gpuNs / 1e6 / repeats,
            cpuMs = cpuNs / 1e6 / repeats
        )
    }

    fun dispose() {
        GL15.glDeleteQueries(query)
    }

    /**
     * Скользящая медиана последних замеров — для подсказки на экране.
     *
     * Медиана, а не среднее: одиночный провал кадра (сборка мусора, переключение окна)
     * сдвигает среднее заметно, а медиану почти не трогает.
     */
    class Window(size: Int) {
        private val gpu = DoubleArray(size)
        private val cpu = DoubleArray(size)
        private var count = 0
        private var next = 0

        fun add(sample: Sample) {
            gpu[next] = sample.gpuMs
            cpu[next] = sample.cpuMs
            next = (next + 1) % gpu.size
            if (count < gpu.size) count++
        }

        fun clear() {
            count = 0
            next = 0
        }

        val size: Int get() = count
        val gpuMedian: Double get() = median(gpu, count)
        val cpuMedian: Double get() = median(cpu, count)
        val gpuMin: Double get() = gpu.take(count).minOrNull() ?: 0.0

        private fun median(values: DoubleArray, n: Int): Double {
            if (n == 0) return 0.0
            val sorted = values.copyOf(n).also { it.sort() }
            return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        }
    }
}
