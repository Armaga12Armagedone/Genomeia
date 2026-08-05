package io.github.some_example_name.old.systems.simulation

import io.github.some_example_name.old.core.PROFILE_UTILIZATION
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.locks.LockSupport

/**
 * Пул постоянных воркеров с барьером на спине вместо ExecutorService + Future.get.
 *
 * ЗАЧЕМ
 * -----
 * За тик симуляция проходит 6-8 параллельных стадий, и каждая стадия раньше стоила:
 *  - N раз `executor.submit { ... }`: аллокация FutureTask, вставка в LinkedBlockingQueue
 *    (это CAS на общей голове очереди), затем unpark спящего потока пула — то есть
 *    системный вызов и планировщик ОС;
 *  - `future.get()` на каждой задаче: главный поток парковался (ещё один вызов планировщика),
 *    просыпался, и так N раз подряд.
 * Суммарно барьер обходился в 15-40 мкс. При тике ~1000 мкс это 150-300 мкс, то есть
 * 15-30% времени уходило не на физику, а на раздачу задач и сон/пробуждение потоков.
 *
 * Здесь потоки создаются один раз и никогда не завершаются между стадиями. Публикация
 * задания — это одна volatile-запись, ожидание — спин на атомарном счётчике. В горячем
 * режиме (симуляция считает без пауз) поток вообще не отдаёт управление ОС: он видит
 * новое задание через несколько десятков наносекунд, потому что запись в epoch
 * инвалидирует его кэш-линию, и следующий же spin-loop читает новое значение.
 *
 * ГЛАВНЫЙ ПОТОК — ТОЖЕ ВОРКЕР
 * ---------------------------
 * workerId 0 — это вызывающий поток. Он не спит на барьере, а сам разбирает чанки вместе
 * с остальными. Это убирает одно пробуждение на стадию и, что важнее, убирает ситуацию
 * "N-1 ядер работают, одно ядро ждёт".
 *
 * ДИНАМИЧЕСКАЯ РАЗДАЧА РАБОТЫ
 * ---------------------------
 * Раньше чанки раздавались статически: i-й чанк — i-му потоку. Работа между чанками
 * распределена крайне неравномерно (организмы кучкуются в пространстве: в одном чанке
 * может быть 5000 частиц, в соседнем 20), а время стадии определяется САМЫМ загруженным
 * потоком. Поэтому ускорение самой физики просто не проявлялось: критический путь
 * задавал один перегруженный чанк, пока остальные ядра стояли на барьере.
 *
 * Теперь чанки лежат в общей очереди-курсоре: освободившийся воркер берёт следующий
 * через getAndIncrement. Цена — один атомарный инкремент на чанк (десятки наносекунд,
 * и это единственная разделяемая запись), выигрыш — потоки заканчивают стадию почти
 * одновременно. Чем мельче чанки относительно числа воркеров, тем ровнее нагрузка.
 *
 * ПРО КОРРЕКТНОСТЬ ПАМЯТИ (JMM)
 * -----------------------------
 * Публикация: главный пишет job/chunkCount обычными записями, затем делает volatile-запись
 * в [epoch]. Воркер делает volatile-чтение [epoch] и только после этого читает job.
 * Volatile-запись/чтение дают release/acquire, поэтому воркер гарантированно видит
 * согласованное задание — без synchronized и без CAS на публикации.
 *
 * Завершение: воркер после работы делает getAndDecrement на PENDING, главный крутится
 * до нуля. getAndDecrement — атомарная read-modify-write операция с volatile-семантикой,
 * значит все записи воркера (vx/vy, буферы команд) видны главному после того, как тот
 * прочитал ноль. Это ровно тот же барьер памяти, что давал Future.get, но без парковки.
 *
 * ПРО FALSE SHARING
 * -----------------
 * Курсор чанков и счётчик незавершённых воркеров лежат в одном AtomicIntegerArray,
 * но разнесены на [PADDING] элементов = 64 байта, то есть в разные кэш-линии. Иначе
 * инкременты курсора (десятки раз за стадию, со всех ядер) гоняли бы туда-обратно ту же
 * линию, в которой живёт PENDING, и каждое чтение PENDING главным потоком тянуло бы
 * линию из чужого L1. Флаги парковки воркеров разнесены так же.
 *
 * ПРО СПИН И ЭНЕРГИЮ
 * ------------------
 * Бесконечный спин недопустим: симуляция может стоять на паузе или быть ограничена
 * targetUPS, и тогда воркеры жгли бы ядра впустую. Поэтому спин ограничен
 * [WORKER_SPIN_LIMIT] итерациями (десятки микросекунд — с запасом покрывает промежуток
 * между стадиями внутри тика), после чего воркер парковывается с растущим таймаутом.
 * Перед парковкой он выставляет свой флаг, и главный поток делает unpark только тем,
 * кто действительно уснул: в горячем режиме unpark не вызывается ни разу.
 *
 * Thread.onSpinWait компилируется в PAUSE (x86) / YIELD (ARM): подсказка процессору, что
 * это spin-loop. Она освобождает ресурсы SMT-соседа и, главное, снимает штраф за
 * memory order violation при выходе из цикла (иначе выход из спина стоит десятки циклов
 * на сброс конвейера).
 */
class ParallelExecutor(val workerCount: Int) {

    /**
     * Единица работы стадии. Не Runnable, потому что нужен индекс чанка и id воркера,
     * а заводить объект на каждый чанк (как FutureTask) — ровно то, от чего мы уходим.
     */
    fun interface ChunkJob {
        fun run(chunkIndex: Int, workerId: Int)
    }

    /** Номер текущего задания. \Volatile-запись здесь — это и есть публикация стадии. */
    @Volatile
    private var epoch = 0L

    @Volatile
    private var running = true

    /**
     * Ошибка, вылетевшая в воркере. Пробрасывается на поток симуляции в конце стадии,
     * чтобы падение выглядело как падение, а не как зависший барьер.
     */
    @Volatile
    private var workerFailure: Throwable? = null

    /**
     * Задание и число чанков — обычные поля: их видимость обеспечивает volatile-запись
     * в epoch, которая идёт строго после них.
     */
    private var currentJob: ChunkJob? = null
    private var currentChunkCount = 0

    /**
     * Номер фазы для профайлера. Обычное поле: его видимость обеспечивает та же
     * volatile-запись в epoch, что и для currentJob.
     */
    private var currentStageId = -1

    /**
     * Время, проведённое каждым воркером внутри drain(), по фазам.
     * Индекс: (stageId * MAX_WORKERS + workerId) * PADDING — воркеры разнесены по
     * кэш-линиям, потому что пишут одновременно.
     */
    private val busyNanos =
        if (PROFILE_UTILIZATION) LongArray(MAX_STAGES * MAX_WORKERS * PADDING) else LongArray(0)

    /** Время стадии по стенным часам. Пишет только главный поток, padding не нужен. */
    private val stageWallNanos = if (PROFILE_UTILIZATION) LongArray(MAX_STAGES) else LongArray(0)

    private val control = AtomicIntegerArray(CONTROL_SIZE)
    private val parkFlags = AtomicIntegerArray(workerCount * PADDING)

    private val threads: Array<Thread> = Array(workerCount - 1) { i ->
        val workerId = i + 1
        Thread({ workerLoop(workerId) }, "Sim-Worker-$workerId").apply {
            isDaemon = true
            start()
        }
    }

    init {
        // Ждём, пока все воркеры войдут в свой цикл. Без этого возможен дедлок: поток,
        // стартовавший уже после публикации стадии, инициализировал бы свой seen текущим
        // epoch, не увидел бы задание и не уменьшил PENDING, а главный ждал бы нуля вечно.
        while (control.get(READY) < workerCount - 1) {
            //TODO на старых андроидах не будет работать
            Thread.onSpinWait()
        }
    }

    /**
     * Выполняет [chunkCount] чанков силами всех воркеров и возвращает управление только
     * когда обработаны все чанки (то есть это стадия с барьером на выходе).
     *
     * Вызывать можно только из одного потока — того, что владеет циклом симуляции.
     */
    fun runChunks(chunkCount: Int, stageId: Int, job: ChunkJob) {
        if (chunkCount <= 0) return

        val stageStart = if (PROFILE_UTILIZATION) System.nanoTime() else 0L

        // Один воркер — нет смысла в атомарном курсоре и барьере вообще.
        if (workerCount == 1) {
            for (i in 0 until chunkCount) job.run(i, 0)
            if (PROFILE_UTILIZATION && stageId in 0 until MAX_STAGES) {
                val elapsed = System.nanoTime() - stageStart
                stageWallNanos[stageId] += elapsed
                busyNanos[stageId * MAX_WORKERS * PADDING] += elapsed
            }
            return
        }

        currentJob = job
        currentChunkCount = chunkCount
        currentStageId = stageId
        control.set(CURSOR, 0)
        control.set(PENDING, workerCount)

        epoch++ // volatile write: с этого момента стадия видна воркерам

        // Будим только тех, кто успел уснуть. В горячем режиме этот цикл ничего не делает.
        for (i in threads.indices) {
            if (parkFlags.get((i + 1) * PADDING) != 0) LockSupport.unpark(threads[i])
        }

        // Главный поток работает наравне с остальными.
        drainTimed(0)
        control.getAndDecrement(PENDING)

        var spins = 0
        while (control.get(PENDING) != 0) {
            //TODO на старых андроидах не будет работать
            Thread.onSpinWait()
            // Страховка от ситуации "воркеров больше, чем свободных ядер": если ждём
            // подозрительно долго, уступаем квант, вместо того чтобы отбирать ядро
            // у потока, которого мы же и ждём.
            if (++spins >= MAIN_SPIN_LIMIT) {
                Thread.yield()
                spins = 0
            }
        }

        // Считается после ожидания: стадия заканчивается, когда закончил последний воркер.
        if (PROFILE_UTILIZATION && stageId in 0 until MAX_STAGES) {
            stageWallNanos[stageId] += System.nanoTime() - stageStart
        }

        // Ошибку воркера пробрасываем только здесь, когда все уже прошли барьер: иначе
        // выход из runChunks оставил бы работающих воркеров, а следующая стадия начала бы
        // переписывать курсор и счётчик у них под руками.
        val failure = workerFailure
        if (failure != null) {
            workerFailure = null
            throw IllegalStateException("воркер упал в стадии $stageId", failure)
        }
    }

    /**
     * drain() с замером.
     *
     * Меряется именно drain, а не весь цикл ожидания: воркер выходит из drain сразу,
     * как только очередь чанков опустела, поэтому это ровно время полезной работы.
     * Разница между суммой этих времён и (workerCount * время стадии) и есть простой
     * на барьере, то есть цена неравномерности нагрузки.
     *
     * Два nanoTime на воркера на стадию — порядка сотни вызовов за тик.
     */
    private fun drainTimed(workerId: Int) {
        if (!PROFILE_UTILIZATION) {
            drain(workerId)
            return
        }

        val stageId = currentStageId
        val start = System.nanoTime()
        drain(workerId)
        if (stageId in 0 until MAX_STAGES && workerId < MAX_WORKERS) {
            busyNanos[(stageId * MAX_WORKERS + workerId) * PADDING] += System.nanoTime() - start
        }
    }

    /**
     * Суммарное время работы всех воркеров в фазе за окно, с обнулением.
     * Читать только из потока симуляции между стадиями.
     */
    fun takeStageBusyNanos(stageId: Int): Long {
        if (!PROFILE_UTILIZATION || stageId !in 0 until MAX_STAGES) return 0L
        var total = 0L
        val base = stageId * MAX_WORKERS * PADDING
        for (worker in 0 until MAX_WORKERS) {
            val index = base + worker * PADDING
            total += busyNanos[index]
            busyNanos[index] = 0
        }
        return total
    }

    /** Время фазы по стенным часам за окно, с обнулением. */
    fun takeStageWallNanos(stageId: Int): Long {
        if (!PROFILE_UTILIZATION || stageId !in 0 until MAX_STAGES) return 0L
        val total = stageWallNanos[stageId]
        stageWallNanos[stageId] = 0
        return total
    }

    /**
     * Разбор общей очереди чанков. Пустой цикл выхода дешевле, чем деление работы заранее:
     * воркер уходит из стадии сразу, как только чанки закончились.
     */
    private fun drain(workerId: Int) {
        val job = currentJob ?: return
        val count = currentChunkCount
        while (true) {
            val index = control.getAndIncrement(CURSOR)
            if (index >= count) return
            job.run(index, workerId)
        }
    }

    private fun workerLoop(workerId: Int) {
        // seen = 0, а первая стадия получит epoch = 1, поэтому даже поток, стартовавший
        // с задержкой, не пропустит задание.
        var seen = 0L
        val parkSlot = workerId * PADDING
        control.getAndIncrement(READY)

        while (running) {
            var spins = 0
            var parkNanos = PARK_NANOS_START

            while (epoch == seen) {
                if (!running) return

                if (spins < WORKER_SPIN_LIMIT) {
                    //TODO на старых андроидах не будет работать
                    Thread.onSpinWait()
                    spins++
                } else {
                    // Флаг ставится ДО повторной проверки epoch: иначе главный поток мог
                    // бы опубликовать стадию между проверкой и парковкой, не увидеть флаг
                    // и не разбудить нас. parkNanos сам по себе тоже страхует от этого.
                    parkFlags.set(parkSlot, 1)
                    if (epoch == seen && running) LockSupport.parkNanos(parkNanos)
                    parkFlags.set(parkSlot, 0)
                    if (parkNanos < PARK_NANOS_MAX) parkNanos *= 2
                }
            }

            seen = epoch
            // try/finally обязателен: без него исключение из job уносит воркера из цикла,
            // PENDING остаётся ненулевым, и главный поток спинит на барьере вечно —
            // вместо падения получается зависание. Именно так вели себя все проверки
            // под DEBUG_CHECKS внутри параллельных фаз.
            try {
                drainTimed(workerId)
            } catch (t: Throwable) {
                // Первая ошибка выигрывает: остальные воркеры почти наверняка упадут
                // на том же самом, и перетирать исходную причину незачем.
                if (workerFailure == null) workerFailure = t
            } finally {
                control.getAndDecrement(PENDING)
            }
        }
    }

    fun shutdown() {
        running = false
        epoch++ // выбиваем воркеров из ожидания
        for (thread in threads) LockSupport.unpark(thread)
        for (thread in threads) {
            try {
                thread.join(200)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    companion object {
        /**
         * Шаг между управляющими словами: 16 int = 64 байта = кэш-линия. Курсор чанков
         * и счётчик незавершённых воркеров не должны делить линию (см. про false sharing).
         */
        private const val PADDING = 16

        private const val CURSOR = 0
        private const val PENDING = PADDING
        private const val READY = PADDING * 2
        private const val CONTROL_SIZE = PADDING * 3

        /**
         * ~20k итераций PAUSE — это десятки микросекунд. Промежуток между стадиями внутри
         * тика на порядок меньше, так что в рабочем режиме воркер не паркуется вообще.
         */
        private const val WORKER_SPIN_LIMIT = 20_000

        private const val PARK_NANOS_START = 100_000L
        private const val PARK_NANOS_MAX = 2_000_000L

        /** Главный поток спинит агрессивнее: он ждёт заведомо короткий хвост стадии. */
        private const val MAIN_SPIN_LIMIT = 200_000

        /** Буферы профайлера индексируются номером фазы, поэтому размер берётся оттуда же. */
        private const val MAX_STAGES = Phase.COUNT

        /** Потолок для индексации буферов занятости, а не ограничение на воркеров. */
        private const val MAX_WORKERS = 64
    }
}
