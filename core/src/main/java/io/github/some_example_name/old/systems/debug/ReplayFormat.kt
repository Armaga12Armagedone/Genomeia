package io.github.some_example_name.old.systems.debug

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Формат файла записи сессии, общий для [LogSaver] и [LogReplay].
 *
 * Заголовок: MAGIC, VERSION, seed, размеры мира, threadCount, имена геномов, число
 * частиц на старте. Дальше поток записей "тег + тик + данные". Конец файла — либо
 * TAG_END, либо просто EOF (игра упала): тогда сессия длится до последнего тика,
 * упомянутого в файле.
 */
object ReplayFormat {

    private const val MAGIC = 0x474E5250 // "GNRP"
    const val VERSION = 1

    const val TAG_COMMAND = 1
    const val TAG_KEYS = 2
    const val TAG_TICK = 3
    const val TAG_END = 4

    const val CMD_STOP_DRAG = 0
    const val CMD_DRAG = 1
    const val CMD_TAP = 2
    const val CMD_TOUCH_DOWN = 3

    private const val FILE_NAME = "debug.bin"
    const val PREVIOUS_FILE_NAME = "debug.prev.bin"

    /**
     * Всё, от чего кроме команд зависит ход сессии. При воспроизведении сверяется с
     * текущим миром: расхождение не мешает запуску, но объясняет, почему вышло иначе.
     */
    class Header(
        val seed: Long,
        val gridWidth: Int,
        val gridHeight: Int,
        val threadCount: Int,
        val genomeNames: List<String>,
        val initialParticles: Int
    )

    fun writeHeader(out: DataOutputStream, header: Header) {
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeLong(header.seed)
        out.writeInt(header.gridWidth)
        out.writeInt(header.gridHeight)
        out.writeInt(header.threadCount)
        out.writeInt(header.genomeNames.size)
        header.genomeNames.forEach { out.writeUTF(it) }
        out.writeInt(header.initialParticles)
    }

    /** @throws IllegalStateException если это не файл записи или он другой версии. */
    fun readHeader(input: DataInputStream): Header {
        check(input.readInt() == MAGIC) { "это не файл записи сессии" }
        val version = input.readInt()
        check(version == VERSION) { "версия файла $version, а умеем читать $VERSION" }
        return Header(
            seed = input.readLong(),
            gridWidth = input.readInt(),
            gridHeight = input.readInt(),
            threadCount = input.readInt(),
            genomeNames = List(input.readInt()) { input.readUTF() },
            initialParticles = input.readInt()
        )
    }

    /**
     * Где лежит файл. null — платформа без файловой системы (веб), запись недоступна.
     *
     * На десктопе рабочий каталог зависит от способа запуска: gradlew :lwjgl3:run стартует
     * из assets/, IDE — из корня проекта. Класть файл в assets нельзя (уедет в сборку),
     * поэтому оттуда поднимаемся на уровень выше — как и дампы сцены в SimulationScreen.
     */
    fun file(): FileHandle? {
        val app = Gdx.app ?: return null
        return when (app.type) {
            Application.ApplicationType.Desktop -> {
                val working = File("").absoluteFile
                val root = if (working.name.equals("assets", ignoreCase = true)) working.parentFile else working
                Gdx.files.absolute(File(root, FILE_NAME).absolutePath)
            }
            Application.ApplicationType.WebGL -> null
            else -> Gdx.files.local(FILE_NAME)
        }
    }
}
