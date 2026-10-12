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
     * на ПК — открыть папку с файлом. false — не вышло (или платформа не умеет).
     */
    fun shareFile(file: File, title: String): Boolean = false

    /**
     * Картинка из буфера обмена — байты файла (PNG, JPEG...) и расширение без точки,
     * или null: в буфере не картинка, или платформа так не умеет. Для отчёта о баге.
     */
    fun clipboardImage(): Pair<ByteArray, String>? = null

    /**
     * Выбрать картинку: галерея на телефоне, файл на ПК. [callback] получает байты и имя
     * файла (null — отменили или не вышло) и может прийти в любом потоке.
     */
    fun pickImage(callback: (bytes: ByteArray?, name: String?) -> Unit) = callback(null, null)

    /**
     * Сведения об устройстве для отчёта о баге: модель, версия системы и т. п. — то, по
     * чему баги группируются («падает только на таком-то телефоне»). Без личных данных.
     */
    fun deviceInfo(): Map<String, String> = emptyMap()
}
