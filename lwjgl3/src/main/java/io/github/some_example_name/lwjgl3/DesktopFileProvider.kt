package io.github.some_example_name.lwjgl3

import com.badlogic.gdx.files.FileHandle
import io.github.some_example_name.old.core.FileProvider
import io.github.some_example_name.old.systems.simulation.SimulationSystem
import java.awt.Desktop
import java.io.File

class DesktopFileProvider : FileProvider {
    override fun getGenomeFile(fileName: String): File {
        val baseDir = getJarDir()
        return File(baseDir, fileName)
    }

    override fun exportGenome(fileName: String) {

    }

    override fun importGenome(callback: (FileHandle?) -> Unit) {

    }

    override fun getExternalFilesDir(type: String?): File? {
        return null
    }

    /** На ПК «поделиться» — это открыть папку с файлом: дальше игрок сам. */
    override fun shareFile(file: File, title: String): Boolean {
        val dir = file.absoluteFile.parentFile ?: return false
        val os = System.getProperty("os.name").lowercase()
        return try {
            // На macOS AWT рядом с GLFW может подвесить окно игры — там только системная команда.
            if ("mac" !in os && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir)
            } else {
                val command = when {
                    "win" in os -> listOf("explorer", dir.path)
                    "mac" in os -> listOf("open", dir.path)
                    else -> listOf("xdg-open", dir.path)
                }
                ProcessBuilder(command).start()
            }
            true
        } catch (e: Exception) {
            System.err.println("Не удалось открыть папку ${dir.path}: ${e.message}")
            false
        }
    }

    private fun getJarDir(): File {
        return try {
            val path: String = SimulationSystem::class.java.protectionDomain.codeSource.location
                .toURI().path
            val jarFile = File(path)
            if (jarFile.isDirectory) jarFile else jarFile.parentFile
        } catch (e: Exception) {
            File(System.getProperty("user.dir"))
        }
    }
}

