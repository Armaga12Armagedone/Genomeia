package io.github.some_example_name.old.bug

import com.badlogic.gdx.Gdx
import io.github.some_example_name.old.core.log.CrashReport
import kotlin.system.exitProcess

/**
 * Необработанное исключение в любом потоке игры: собрать .bug и дать игроку его отправить.
 *
 * ЧТО ПРОИСХОДИТ
 * --------------
 *  1. Отчёт (маршрут по экранам, хвост действий, стектрейс) печатается в System.err —
 *     то есть в терминал при разработке и в logcat на телефоне, как обычный краш.
 *  2. Собирается .bug с пометкой «краш» и помечается как непоказанный.
 *  3. Упал фоновый поток (обычно поток симуляции), а игра жива — окно отчёта
 *     показывается сразу; закрыли окно — игра завершается обычным порядком.
 *     Упал поток рендера — игре конец, и окно покажется при следующем запуске.
 *  4. Исключение отдаётся обработчику платформы, который стоял до нас. На Android это
 *     системный: FATAL EXCEPTION в logcat и штатное завершение — ровно то, что ждёт
 *     Android Studio. Раньше свой обработчик игры его глотал, и в терминале стектрейса
 *     было не найти.
 *
 * Во время повтора бага новый .bug не собирается: печатается, где именно краш
 * воспроизвёлся, и игра остаётся открытой, если может.
 */
object CrashHandler {

    private var previous: Thread.UncaughtExceptionHandler? = null
    private var renderThread: Thread? = null

    @Volatile
    private var handling = false

    fun install() {
        renderThread = Thread.currentThread()
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error -> handle(thread, error) }
    }

    private fun handle(thread: Thread, error: Throwable) {
        // Второй краш, пока разбираемся с первым (например, упал сам сборщик отчёта):
        // ничего не собираем, сразу платформе.
        if (handling) {
            finish(thread, error)
            return
        }
        handling = true

        try {
            System.err.println(CrashReport.build(error))
        } catch (_: Throwable) {
            error.printStackTrace()
        }

        if (BugReplay.isActive) {
            BugReplay.onCrash(thread, error)
            if (thread !== renderThread) {
                handling = false
                return
            }
            finish(thread, error)
            return
        }

        val bug = try {
            BugReports.create(BugKind.CRASH, description = "", crash = error, crashThread = thread).also {
                BugReports.markPendingCrash(it)
                System.err.println("[Genomeia] Отчёт о краше: ${it.file().absolutePath}")
            }
        } catch (reportFailure: Throwable) {
            // Сборщик отчёта не имеет права стать причиной второй ошибки: исходный
            // стектрейс уже напечатан, его достаточно.
            System.err.println("[Genomeia] Не удалось собрать отчёт о краше: $reportFailure")
            null
        }

        val app = Gdx.app
        if (thread !== renderThread && bug != null && app != null) {
            app.postRunnable {
                BugOverlay.showCrash(bug) { finish(thread, error) }
            }
            return
        }
        finish(thread, error)
    }

    /** Дальше — как без нас: обработчик платформы, а если его нет — выход с ошибкой. */
    private fun finish(thread: Thread, error: Throwable) {
        val platform = previous
        if (platform != null) {
            platform.uncaughtException(thread, error)
        } else {
            exitProcess(1)
        }
    }
}
