package io.github.some_example_name.android

import android.content.Context
import android.content.Intent
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import games.spooky.gdx.nativefilechooser.NativeFileChooser
import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration
import io.github.some_example_name.old.core.FileProvider
import java.io.File
import java.io.FilenameFilter

class AndroidFileProvider(private val context: Context, private val fileChooser: NativeFileChooser) : FileProvider {

    override fun getGenomeFile(fileName: String): File {
        return File(context.filesDir, fileName)
    }

    override fun exportGenome(fileName: String) {
        shareGenomeFile(context, fileName)
    }

    override fun importGenome(callback: (FileHandle?) -> Unit) {
        val conf = NativeFileChooserConfiguration().apply {
            title = "Genome File"
            mimeFilter = "application/octet-stream" // Бинарный формат

            // Реализация nameFilter через анонимный объект для совместимости
            nameFilter = FilenameFilter { dir, name ->
                name.endsWith(".genome")
            }
        }

        fileChooser.chooseFile(conf, object : NativeFileChooserCallback {
            override fun onFileChosen(file: FileHandle) {
                // Имя чистится, а не берётся как есть: см. sanitizeGenomeFileName.
                val importedName = sanitizeGenomeFileName(file.name())

                // Сохраняем в local с тем же именем (перезапись, если существует)
                val localFile = Gdx.files.local("genomes/$importedName")
                file.copyTo(localFile)

                Gdx.app.log("Import", "Name: ${localFile.path()}")
                callback(localFile)
            }

            override fun onCancellation() {
                callback(null)
            }

            override fun onError(exception: Exception?) {
                Gdx.app.log("FileChooser", "Error: ${exception?.message}")
                callback(null)
            }
        })
    }

    /**
     * Приводит имя выбранного файла к имени файла генома.
     *
     * ОТКУДА БЕРЁТСЯ ТИЛЬДА
     * ---------------------
     * На современном Android выбор файла всегда возвращает content:// URI, а не путь.
     * AndroidFileChooser.fileHandleFromUri в таком случае копирует поток во временный файл
     * в кеше приложения и даёт ему имя `"~" + nameFromUri(uri)` — тильда это МАРКЕР
     * временного файла библиотеки, а не часть имени. Мы же сохраняли файл под именем
     * file.name() как есть, поэтому геном ложился в genomes/~foo.genome, а его именем
     * (имя генома здесь — это имя файла без расширения) становилось "~foo".
     *
     * ПОЧЕМУ ЕЩЁ И ПУТЬ
     * -----------------
     * Если запрос DISPLAY_NAME у провайдера не отработал, nameFromUri падает в
     * uri.getLastPathSegment(), а это может быть "primary:Download/foo.genome" — с двоеточием
     * и слэшем внутри. Такое имя ломает Gdx.files.local("genomes/$name"): слэш превращается
     * в подкаталог, и геном перестаёт находиться в списке.
     *
     * Расширение дописывается, потому что имя генома выводится через nameWithoutExtension:
     * без ".genome" файл вообще не попадёт в выборку getGenomeFileNamesFromFolder.
     *
     * Побочный эффект: геном, чьё имя действительно начинается с тильды, потеряет её при
     * импорте. Отличить его от временного файла библиотеки нельзя — она добавляет тильду
     * ровно к тому же месту.
     */
    private fun sanitizeGenomeFileName(rawName: String): String {
        var name = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringAfterLast(':')
            .removePrefix("~")
            .trim()

        if (name.isEmpty()) name = "imported"
        if (!name.endsWith(GENOME_EXTENSION, ignoreCase = true)) name += GENOME_EXTENSION

        return name
    }

    fun importOldGenome(callback: (FileHandle?) -> Unit) {
        //TODO
        val conf = NativeFileChooserConfiguration().apply {
            title = "Genome JSON"
            mimeFilter = "application/json" // Фильтр по MIME-типу JSON

            // Реализация nameFilter через анонимный объект для совместимости
            nameFilter = FilenameFilter { dir, name ->
                name.endsWith(".json")  // Без ?., т.к. в интерфейсе name non-null, но на практике безопасно
            }
        }

        fileChooser.chooseFile(conf, object : NativeFileChooserCallback {
            override fun onFileChosen(file: FileHandle) {
                // Получаем оригинальное имя файла (например, "my_genome.json")
                val originalName = file.name()

                // Сохраняем в local с тем же именем (перезапись, если существует)
                val localFile = Gdx.files.local("user_genomes/$originalName")
                file.copyTo(localFile)

                Gdx.app.log("Import", "Name: ${localFile.path()}")
                callback(localFile)
            }

            override fun onCancellation() {
                callback(null)
            }

            override fun onError(exception: Exception?) {
                Gdx.app.log("FileChooser", "Error: ${exception?.message}")
                callback(null)
            }
        })
    }

    override fun getExternalFilesDir(type: String?): File? {
        return (Gdx.app as AndroidLauncher).getExternalFilesDir(null)
    }

    private companion object {
        const val GENOME_EXTENSION = ".genome"
    }
}

fun shareGenomeFile(context: Context, relativeFileName: String) {
    val file = File(context.filesDir, relativeFileName)
    if (!file.exists()) {
        println("File to share does not exist: ${file.absolutePath}")
        return
    }

    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream" // или другой тип, если знаешь
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Share genome file"))
}
