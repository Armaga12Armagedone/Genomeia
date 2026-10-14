package io.github.some_example_name.old.systems.maps

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Контейнер карты: 12 байт ASCII «genomeia-map», затем версия Int (big endian), затем ZIP.
 * Заголовок стоит перед архивом: чужой файл и новую версию отвергаем до распаковки.
 * Дополнительная цена — 16 байт на файл, никаких изменений в горячем пути симуляции.
 */
object MapFile {
    const val EXTENSION = ".map"
    const val VERSION = 1
    private val magic = "genomeia-map".toByteArray(Charsets.US_ASCII)

    fun write(output: OutputStream): ZipOutputStream {
        val header = DataOutputStream(output)
        header.write(magic)
        header.writeInt(VERSION)
        return ZipOutputStream(header)
    }

    fun read(input: InputStream): ZipInputStream {
        val stream = BufferedInputStream(input)
        try {
            val header = DataInputStream(stream)
            val actual = ByteArray(magic.size)
            header.readFully(actual)
            require(actual.contentEquals(magic)) { "не файл карты Genomeia" }
            val version = header.readInt()
            require(version == VERSION) { "неподдерживаемая версия .map: $version (поддерживается $VERSION)" }
            stream.mark(4)
            val prefix = ByteArray(4)
            header.readFully(prefix)
            require(prefix.contentEquals(byteArrayOf(0x50, 0x4b, 3, 4))) { "в .map нет ZIP-архива" }
            stream.reset()
            return ZipInputStream(stream)
        } catch (e: Exception) {
            stream.close()
            throw e
        }
    }
}
