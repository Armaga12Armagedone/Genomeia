package io.github.some_example_name.android

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import games.spooky.gdx.nativefilechooser.NativeFileChooser
import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration
import io.github.some_example_name.old.core.FileProvider
import io.github.some_example_name.old.core.GameFiles
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
                val localFile = GameFiles.genomesDir().child(importedName)
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

    /** Системное окно «поделиться»: файл уходит как вложение (почта, мессенджер, диск). */
    override fun shareFile(file: File, title: String): Boolean = try {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Gdx.app.error("Share", "не удалось поделиться ${file.name}", e)
        false
    }

    /** Картинка из буфера: на Android туда кладут не байты, а ссылку на картинку (content://). */
    override fun clipboardImage(): Pair<ByteArray, String>? = try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        var found: Pair<ByteArray, String>? = null
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                val uri = clip.getItemAt(i).uri ?: continue
                val type = context.contentResolver.getType(uri) ?: continue
                if (!type.startsWith("image/")) continue
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: continue
                found = bytes to type.substringAfter('/').substringBefore(';').ifEmpty { "png" }
                break
            }
        }
        found
    } catch (e: Exception) {
        Gdx.app.error("Clipboard", "не удалось взять картинку из буфера", e)
        null
    }

    /** Галерея: тот же системный выбор файла, что и у импорта генома, только картинки. */
    override fun pickImage(callback: (bytes: ByteArray?, name: String?) -> Unit) {
        val conf = NativeFileChooserConfiguration().apply {
            title = "Image"
            mimeFilter = "image/*"
        }
        fileChooser.chooseFile(conf, object : NativeFileChooserCallback {
            override fun onFileChosen(file: FileHandle) {
                val bytes = runCatching { file.readBytes() }.getOrNull()
                callback(bytes, file.name().removePrefix("~"))
            }

            override fun onCancellation() = callback(null, null)

            override fun onError(exception: Exception?) {
                Gdx.app.log("FileChooser", "Error: ${exception?.message}")
                callback(null, null)
            }
        })
    }

    override fun deviceInfo(): Map<String, String> = mapOf(
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "android" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT.toString(),
        "abi" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "?"),
        "cpuCores" to Runtime.getRuntime().availableProcessors().toString(),
        "maxMemoryMb" to (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toString()
    )

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
