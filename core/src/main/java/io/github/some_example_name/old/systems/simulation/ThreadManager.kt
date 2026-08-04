package io.github.some_example_name.old.systems.simulation

import io.github.some_example_name.old.core.DISimulationContainer.chunkSize
import io.github.some_example_name.old.core.DISimulationContainer.gridSize
import io.github.some_example_name.old.core.DISimulationContainer.threadCount
import io.github.some_example_name.old.core.DISimulationContainer.totalChunks
import io.github.some_example_name.old.core.WorldResizable

class ThreadManager(
    val simulationData: SimulationData
): WorldResizable {

    /**
     * Пул постоянных воркеров вместо ExecutorService. Раньше каждая стадия стоила N
     * аллокаций FutureTask, N вставок в очередь пула, N пробуждений потоков и N парковок
     * главного потока на Future.get — порядка 15-40 мкс на стадию, то есть при 6-8 стадиях
     * 15-30% времени тика уходило в накладные расходы планировщика. См. ParallelExecutor.
     */
    var executor = ParallelExecutor(workerCount())
        private set

    var isRunning = false

    fun dispose() {
        isRunning = false
        executor.shutdown()
    }

    fun stopSimulationLoop() {
        isRunning = false
    }

    inline fun runUpdateLoop(onUpdateTick: () -> Unit) {
        var lastTime = System.nanoTime()
        var accumulator = 0.0

        // для UPS
        var updatesThisSecond = 0
        var lastUpsTime = System.nanoTime()

        // кэшируем последнее значение, чтобы реагировать на изменение на лету
        var lastTargetUPS = simulationData.targetUPS
        var deltaTimePerTick = 1.0 / lastTargetUPS.toDouble()

        try {
            while (isRunning) {
                if (!simulationData.isPlay) {
                    try {
                        Thread.sleep(16)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }

                val currentTime = System.nanoTime()
                val frameTime = minOf((currentTime - lastTime) / 1_000_000_000.0, 0.25)
                lastTime = currentTime

                // === Реакция на изменение targetUPS на лету ===
                if (!simulationData.maxSpeed && lastTargetUPS != simulationData.targetUPS) {
                    deltaTimePerTick = 1.0 / simulationData.targetUPS.toDouble()
                    accumulator = 0.0          // сбрасываем, чтобы не было резкого "взрыва" обновлений
                    lastTargetUPS = simulationData.targetUPS
                }

                accumulator += frameTime

                if (simulationData.maxSpeed) {
                    onUpdateTick.invoke()
                    updatesThisSecond++
                } else {
                    // обычный фиксированный timestep
                    while (accumulator >= deltaTimePerTick) {
                        onUpdateTick.invoke()
                        accumulator -= deltaTimePerTick
                        updatesThisSecond++
                    }

                    // === спим остаток тика (даже если обновлений в этом кадре не было) ===
                    val elapsed = (System.nanoTime() - currentTime) / 1_000_000_000.0
                    val sleepTime = deltaTimePerTick - elapsed
                    if (sleepTime > 0.001) {
                        try {
                            Thread.sleep(
                                (sleepTime * 1000).toLong(),
                                ((sleepTime * 1_000_000) % 1_000_000).toInt()
                            )
                        } catch (_: InterruptedException) {
                            break
                        }
                    } else if (sleepTime > 0) {
                        Thread.yield()   // совсем маленький остаток — просто уступаем CPU
                    }
                }

                // === UPS каждую секунду (реальное количество вызовов onUpdateTick) ===
                val now = System.nanoTime()
                if ((now - lastUpsTime) >= 1_000_000_000L) {
                    simulationData.ups = updatesThisSecond
                    updatesThisSecond = 0
                    lastUpsTime = now
                }
            }
        } catch (_: InterruptedException) {
            // выход // exit
        }
    }

    /**
     * Стадия по чанкам сетки одной чётности.
     *
     * Чанки раздаются динамически: освободившийся воркер берёт следующий сам. Раньше была
     * жёсткая привязка "чанк i потоку i", а работа между чанками распределена крайне
     * неравномерно — организмы кучкуются, и в одном чанке могут быть тысячи частиц, в
     * соседнем десятки. Время стадии определяется самым загруженным потоком, поэтому при
     * статической раздаче стадия упиралась в один перегруженный чанк, пока остальные ядра
     * стояли на барьере. Теперь ядро, разобравшее пустой чанк, идёт помогать с очередью.
     *
     * [slot] — это номер чанка внутри своей чётности (chunkIndex / 2 в старой нумерации).
     * По нему индексируются per-chunk структуры: буферы команд, стеки позиций, списки
     * связей. Два одновременно обрабатываемых чанка одной чётности не соседствуют в
     * пространстве, поэтому потоки не пишут в одни и те же частицы, а разные slot'ы
     * не пишут в одни и те же вспомогательные структуры — синхронизация не нужна.
     */
    inline fun runChunkStage(
        isOdd: Boolean,
        crossinline job: (start: Int, end: Int, slot: Int) -> Unit
    ) {
        val first = if (isOdd) 1 else 0
        val slots = (totalChunks - first + 1) / 2

        executor.runChunks(slots) { slot, _ ->
            val chunk = first + slot * 2
            val start = chunk * chunkSize
            val end = if (chunk == totalChunks - 1) gridSize else (chunk + 1) * chunkSize
            job(start, end, slot)
        }
    }

    /**
     * Стадия, работа которой уже разложена по слотам заранее (списки связей, стеки частиц).
     * Раздача тоже динамическая: слоты неравномерны по объёму работы ровно так же, как чанки.
     */
    inline fun runSlotStage(slotCount: Int, crossinline job: (slot: Int) -> Unit) {
        executor.runChunks(slotCount) { slot, _ -> job(slot) }
    }

    override fun resize() {
        val old = executor
        executor = ParallelExecutor(workerCount())
        old.shutdown()
    }

    companion object {
        /**
         * Число потоков теперь не равно числу чанков.
         *
         * threadCount по смыслу это количество ПРОСТРАНСТВЕННЫХ СЛОТОВ в стадии
         * (gridHeight / chunkHeight / 2), и оно задаётся геометрией мира, а не железом.
         * Уменьшать chunkHeight, чтобы получить больше слотов, нельзя: поток, считающий
         * связи своего чанка, пишет в скорости частиц на расстояние до sqrt(linkMaxLength2)
         * клеток за его границы, поэтому между чанками одной чётности нужен зазор не меньше
         * двух таких длин — при chunkHeight = 8 и максимальной длине связи 3 это выполняется
         * ровно с запасом 2. Уменьшение чанка сразу даёт гонку на vx/vy.
         *
         * Зато воркеров можно сделать МЕНЬШЕ, чем слотов, и от этого только лучше:
         *  - на машине с 4 ядрами 8 спинящих воркеров дрались бы за ядра, а спин при
         *    переподписке — это чистая потеря (воркер жжёт такты, пока владелец его чанка
         *    не получит квант);
         *  - когда слотов больше, чем воркеров, динамическая раздача наконец начинает
         *    работать по-настоящему: перегруженный слот берёт кто-то один, а его пустые
         *    слоты разбирают остальные.
         *
         * Верхняя граница — число слотов: больше воркеров, чем слотов, всё равно нечем занять.
         */
        fun workerCount(): Int =
            minOf(threadCount, Runtime.getRuntime().availableProcessors())
    }
}
