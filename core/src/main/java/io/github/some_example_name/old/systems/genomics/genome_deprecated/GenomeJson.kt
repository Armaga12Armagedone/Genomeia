package io.github.some_example_name.old.systems.genomics.genome_deprecated

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import io.github.some_example_name.old.core.DISimulationContainer
import java.io.File

@Deprecated("Теперь используется protobuf")
class GenomeJsonReader() {

    private val json = Json()

    val saveDir: FileHandle = when (Gdx.app.type) {
        Application.ApplicationType.Desktop -> {
//            val jarFile = File(GenomeJsonReader::class.java.protectionDomain.codeSource.location.toURI())
//            Gdx.files.absolute(jarFile.parentFile.absolutePath)
            Gdx.files.local("")
        }
        Application.ApplicationType.Android -> {
            Gdx.files.local("")  // Локальное приватное хранилище приложения на Android
        }
        else -> {
            Gdx.files.local("")  // Для других платформ, например, iOS или Web
        }
    }

    init {
        json.setOutputType(JsonWriter.OutputType.json)
        json.setUsePrototypes(false)
    }

    fun getGenomeFileNamesFromAssetsFolder(relativeFolderName: String): List<String> {
        val folder: FileHandle = Gdx.files.internal(relativeFolderName.trimEnd('/'))
        if (!folder.exists()) {
            return emptyList()
        }
        val names = mutableListOf<String>()
        val files = folder.list(".json")
        if (files.isNotEmpty()) {
            files.filter { !it.isDirectory && it.extension() == "json" }
                .forEach { file ->
                    names.add(file.nameWithoutExtension())
                }
        } else {
            val assetsList: FileHandle? = Gdx.files.internal("assets.txt")
            if (assetsList != null && assetsList.exists()) {
                val allPaths = assetsList.readString().split("\n")
                val targetFolder = if (relativeFolderName.isEmpty()) "" else "${relativeFolderName.trimEnd('/')}/"
                allPaths.filter { it.startsWith(targetFolder) && !it.substring(targetFolder.length).contains("/") && it.endsWith(".json") }
                    .forEach { path ->
                        val fileName = path.substringAfterLast('/').removeSuffix(".json")
                        names.add(fileName)
                    }
            } else {
                println("assets.txt not found for workaround")
            }
        }
        return names
    }

    fun getGenomeFileNamesFromFolder(): List<String> {
        val folderHandle: FileHandle = saveDir.child("genomes")
//        if (!folderHandle.exists() || !folderHandle.isDirectory) {
//            println("Folder not found: ${folderHandle.path()}")
//            return emptyList()
//        }
        return folderHandle.list()
            .filter { !it.isDirectory && it.extension().equals("genome", ignoreCase = true) }
            .map { it.nameWithoutExtension() }
    }
}
