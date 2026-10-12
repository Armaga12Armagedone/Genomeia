package io.github.some_example_name.old.core

import com.badlogic.gdx.files.FileHandle
import java.io.File

interface FileProvider {
    fun getGenomeFile(fileName: String): File

    fun exportGenome(fileName: String)
    fun importGenome(callback: (FileHandle?) -> Unit)

    fun getExternalFilesDir(type: String?): File?

    /**
     * Отдать файл игроку: на телефоне — системное окно «поделиться» (почта, мессенджер),
     * на ПК — открыть папку с файлом. false — не вышло.
     */
    fun shareFile(file: File, title: String): Boolean
}
