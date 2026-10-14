package io.github.some_example_name.old.systems.maps

import com.badlogic.gdx.files.FileHandle
import io.github.some_example_name.old.core.GameFiles
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import kotlin.test.*

class MapFileTest {
    private fun archive(): ByteArray {
        val bytes = ByteArrayOutputStream()
        MapFile.write(bytes).use {
            it.putNextEntry(ZipEntry("preview.png"))
            it.write(byteArrayOf(1, 2, 3))
            it.closeEntry()
        }
        return bytes.toByteArray()
    }

    @Test fun headerAndPayloadRoundTrip() {
        val bytes = archive()
        assertEquals("genomeia-map", String(bytes, 0, 12, Charsets.US_ASCII))
        assertContentEquals(byteArrayOf(0, 0, 0, 1), bytes.copyOfRange(12, 16))
        MapFile.read(bytes.inputStream()).use {
            assertEquals("preview.png", it.nextEntry.name)
            assertContentEquals(byteArrayOf(1, 2, 3), it.readBytes())
            assertNull(it.nextEntry)
        }
    }

    @Test fun zipWithoutMapHeaderIsRejected() {
        val bytes = archive().drop(16).toByteArray()
        assertFailsWith<IllegalArgumentException> { MapFile.read(bytes.inputStream()) }
    }

    @Test fun wrongMagicFutureVersionAndTruncatedHeaderAreRejected() {
        val original = archive()
        for (index in listOf(0, 15, 16)) {
            val bytes = original.copyOf()
            bytes[index] = 42
            assertFailsWith<IllegalArgumentException> { MapFile.read(bytes.inputStream()) }
        }
        for (size in 0 until 20) {
            assertFails { MapFile.read(original.copyOf(size).inputStream()) }
        }
    }

    @Test fun importChecksRequiredEntriesAndCrc() {
        val root = FileHandle(Files.createTempDirectory("map-import").toFile())
        try {
            val bytes = ByteArrayOutputStream()
            MapFile.write(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("meta.bin"))
                val out = java.io.DataOutputStream(zip)
                out.writeInt(2)
                out.writeInt(64)
                out.writeInt(64)
                out.writeInt(123)
                out.writeFloat(1f)
                out.writeBoolean(false)
                zip.closeEntry()
                for (name in listOf("types.bin", "genomes.bin", "cellActions.bin", "entities/cell.bin")) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(byteArrayOf(1, 2, 3))
                    zip.closeEntry()
                }
            }
            val file = root.child("outside.map")
            file.writeBytes(bytes.toByteArray(), false)
            MapSave.validateImport(file)
            val truncated = bytes.toByteArray().copyOf(70)
            file.writeBytes(truncated, false)
            assertFails { MapSave.validateImport(file) }
        } finally {
            root.deleteDirectory()
        }
    }

    @Test fun libraryIgnoresZipFiles() {
        val root = FileHandle(Files.createTempDirectory("map-files").toFile())
        val previous = GameFiles.dataRoot
        try {
            GameFiles.dataRoot = root
            val maps = GameFiles.mapsDir()
            maps.child("1.zip").writeBytes(archive().drop(16).toByteArray(), false)
            maps.child("2.zip").writeBytes(archive().drop(16).toByteArray(), false)
            maps.child("2.map").writeBytes(archive(), false)
            maps.child("3.map.tmp").writeBytes(byteArrayOf(), false)
            assertEquals(setOf("2"), MapSave.listMaps().map { it.slot }.toSet())
            assertEquals("1.map", MapSave.mapFile("1").name())
            assertEquals("2.map", MapSave.mapFile("2").name())
            assertEquals("4.map", MapSave.mapFile("4").name())
            assertFailsWith<IllegalArgumentException> { MapSave.validateImport(maps.child("2.map")) }
        } finally {
            GameFiles.dataRoot = previous
            root.deleteDirectory()
        }
    }
}
