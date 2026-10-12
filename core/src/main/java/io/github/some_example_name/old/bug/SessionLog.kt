package io.github.some_example_name.old.bug

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationLogger
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.Charset

/**
 * Вывод игры за сессию — в файл session/console.log, оттуда он уезжает в .bug.
 *
 * ПОЧЕМУ «ТРОЙНИК», А НЕ ПЕРЕНАПРАВЛЕНИЕ
 * --------------------------------------
 * Раньше лог краша писался в файл ВМЕСТО консоли: в релизе его приходилось включать
 * руками, а с включённым при разработке стектрейс пропадал из терминала Android Studio.
 * Здесь каждая строка идёт и туда, где её ждали (консоль, logcat), и в файл, — включать
 * ничего не нужно, и разработчик видит всё как раньше.
 *
 * Перехватываются System.out / System.err (println и printStackTrace) и, на Android,
 * Gdx.app.log/error: там они уходят прямо в logcat, мимо System.out. На десктопе
 * Gdx.app.log сам печатает в System.out, поэтому второй раз его не перехватываем.
 *
 * Байты в файл идут в кодировке консоли ([charset]) — как их и печатает PrintStream; в
 * UTF-8 они переводятся один раз, при сборке .bug.
 *
 * Длинный лог не растёт без конца: дойдя до [MAX_BYTES], файл становится console.1.log
 * (прошлый такой удаляется), и запись начинается заново. В .bug уходят оба — то есть
 * последние 4–8 МБ, где и лежит то, что случилось перед багом.
 */
object SessionLog {

    private const val MAX_BYTES = 4L * 1024 * 1024

    private val lock = Any()
    private var dir: File? = null
    private var out: OutputStream? = null
    private var written = 0L

    /** Кодировка байтов в файле — та же, что у консоли. */
    var charset: Charset = Charsets.UTF_8
        private set

    fun install(sessionDir: FileHandle) {
        val target = sessionDir.file().apply { mkdirs() }
        synchronized(lock) {
            dir = target
            openFresh()
        }

        charset = consoleCharset()
        System.setOut(PrintStream(Tee(System.out), true, charset.name()))
        System.setErr(PrintStream(Tee(System.err), true, charset.name()))

        if (Gdx.app.type != Application.ApplicationType.Desktop) {
            Gdx.app.applicationLogger = TeeLogger(Gdx.app.applicationLogger)
        }
    }

    /** Файлы лога по порядку: начало (если было), потом текущий. */
    fun files(): List<File> = synchronized(lock) {
        flushLocked()
        val d = dir ?: return emptyList()
        listOf(File(d, PREVIOUS), File(d, CURRENT)).filter { it.exists() }
    }

    /** Лог целиком в UTF-8 — для .bug. */
    fun readUtf8(file: File): ByteArray =
        if (charset == Charsets.UTF_8) file.readBytes() else String(file.readBytes(), charset).toByteArray(Charsets.UTF_8)

    private fun write(bytes: ByteArray, offset: Int, length: Int) {
        synchronized(lock) {
            val stream = out ?: return
            try {
                stream.write(bytes, offset, length)
                written += length
                if (written >= MAX_BYTES) rotate()
            } catch (_: Exception) {
                // Лог не имеет права ронять игру: молча перестаём писать в файл.
                out = null
            }
        }
    }

    private fun writeLine(line: String) {
        val bytes = (line + "\n").toByteArray(charset)
        write(bytes, 0, bytes.size)
    }

    private fun rotate() {
        val d = dir ?: return
        out?.close()
        val previous = File(d, PREVIOUS)
        previous.delete()
        File(d, CURRENT).renameTo(previous)
        openFresh()
    }

    private fun openFresh() {
        val d = dir ?: return
        out = FileOutputStream(File(d, CURRENT), false).buffered(16 * 1024)
        written = 0
    }

    private fun flushLocked() {
        try {
            out?.flush()
        } catch (_: Exception) {
        }
    }

    private fun consoleCharset(): Charset = runCatching {
        System.getProperty("stdout.encoding")?.let { Charset.forName(it) }
    }.getOrNull() ?: Charset.defaultCharset()

    /** Пишет и в исходный поток, и в файл сессии. */
    private class Tee(private val original: OutputStream) : OutputStream() {
        override fun write(b: Int) {
            original.write(b)
            SessionLog.write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            original.write(b, off, len)
            SessionLog.write(b, off, len)
        }

        override fun flush() {
            original.flush()
            synchronized(lock) { flushLocked() }
        }
    }

    /** Android: Gdx.app.log/error идут в logcat мимо System.out — дублируем в файл. */
    private class TeeLogger(private val original: ApplicationLogger) : ApplicationLogger {
        override fun log(tag: String, message: String) {
            original.log(tag, message)
            writeLine("[$tag] $message")
        }

        override fun log(tag: String, message: String, exception: Throwable) {
            original.log(tag, message, exception)
            writeLine("[$tag] $message\n${exception.stackTraceToString()}")
        }

        override fun error(tag: String, message: String) {
            original.error(tag, message)
            writeLine("[$tag] ERROR $message")
        }

        override fun error(tag: String, message: String, exception: Throwable) {
            original.error(tag, message, exception)
            writeLine("[$tag] ERROR $message\n${exception.stackTraceToString()}")
        }

        override fun debug(tag: String, message: String) {
            original.debug(tag, message)
            writeLine("[$tag] DEBUG $message")
        }

        override fun debug(tag: String, message: String, exception: Throwable) {
            original.debug(tag, message, exception)
            writeLine("[$tag] DEBUG $message\n${exception.stackTraceToString()}")
        }
    }

    const val CURRENT = "console.log"
    const val PREVIOUS = "console.1.log"
}
