package io.github.some_example_name.old.features.levels.editor

import com.badlogic.gdx.files.FileHandle
import io.github.some_example_name.old.core.GameFiles
import kotlinx.serialization.json.Json

/**
 * Файлы уровней в папке levels/: имя файла — имя уровня.
 *
 * Формат — тот же [LevelInfo], что редактор отдаёт в симуляцию, а карта в нём — тот же
 * [io.github.some_example_name.old.features.worldeditor.WorldSpec], что едет в команде
 * GoSimulation. Поэтому карта в файле не хранится: она восстанавливается из сида и правок
 * кистью, и файл остаётся читаемым и маленьким.
 */
object LevelFiles {
    private const val EXTENSION = "json"

    private val json = Json {
        prettyPrint = true
        // Новое поле в уровне не должно делать старые файлы неоткрываемыми.
        ignoreUnknownKeys = true
    }

    /** Имена уровней без расширения, по алфавиту. */
    fun names(): List<String> = levelsDir().list()
        .filter { !it.isDirectory && it.extension().equals(EXTENSION, ignoreCase = true) }
        .map { it.nameWithoutExtension() }
        .sorted()

    fun read(name: String): LevelInfo =
        json.decodeFromString(LevelInfo.serializer(), file(name).readString("UTF-8"))

    fun write(level: LevelInfo): FileHandle {
        val file = file(level.nameKey)
        file.writeString(json.encodeToString(LevelInfo.serializer(), level), false, "UTF-8")
        return file
    }

    fun file(name: String): FileHandle = levelsDir().child("${fileName(name)}.$EXTENSION")

    private fun levelsDir(): FileHandle = GameFiles.levelsDir()

    /**
     * Имя уровня → имя файла. Слэши и служебные символы недопустимы в имени файла на
     * всех платформах; сам уровень при этом продолжает называться так, как его назвали.
     */
    private fun fileName(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { "level" }
}
