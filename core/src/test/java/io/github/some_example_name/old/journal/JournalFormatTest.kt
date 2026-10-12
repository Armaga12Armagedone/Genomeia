package io.github.some_example_name.old.journal

import io.github.some_example_name.old.bug.BugFile
import io.github.some_example_name.old.commands.GameCommand
import io.github.some_example_name.old.commands.GoSettings
import io.github.some_example_name.old.commands.PlayerCommand
import io.github.some_example_name.old.features.menu.MenuIntent
import io.github.some_example_name.old.features.simulation.SimulationIntent
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JournalFormatTest {

    private class Written(
        val channel: Channel,
        val source: CommandSource,
        val nested: Boolean,
        val epoch: Int,
        val tick: Int,
        val command: GameCommand
    )

    private val sample = listOf(
        Written(Channel.UI, CommandSource.MENU, false, 0, -1, MenuIntent.OpenSettings),
        Written(Channel.UI, CommandSource.NAVIGATION, true, 0, -1, GoSettings),
        Written(Channel.SIM, CommandSource.SYSTEM, false, 1, 0, SessionStarted(42L)),
        Written(Channel.SIM, CommandSource.PLAYER, false, 1, 17, PlayerCommand.Tap(3f, 4f, true, 0)),
        Written(Channel.UI, CommandSource.SIMULATION, false, 1, 18, SimulationIntent.SetPaused(true)),
        Written(Channel.SIM, CommandSource.SYSTEM, false, 1, 60, StateHashed(0x1234_5678_9abc_def0L)),
    )

    private fun write(entries: List<Written>): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        JournalFormat.writeHeader(out)
        entries.forEachIndexed { index, e ->
            val (id, payload) = CommandRegistry.encode(e.command)!!
            JournalFormat.writeEntry(out, e.channel, e.source, e.nested, e.epoch, e.tick, index * 10, id, payload)
        }
        out.flush()
        return bytes.toByteArray()
    }

    @Test
    fun `журнал читается обратно запись в запись`() {
        val result = JournalFormat.read(ByteArrayInputStream(write(sample)))
        assertFalse(result.truncated)
        assertEquals(0, result.unknownTypes)
        assertEquals(sample.size, result.entries.size)
        sample.zip(result.entries).forEachIndexed { index, (expected, actual) ->
            assertEquals(expected.channel, actual.channel)
            assertEquals(expected.source, actual.source)
            assertEquals(expected.nested, actual.nested)
            assertEquals(expected.epoch, actual.epoch)
            assertEquals(expected.tick, actual.tick)
            assertEquals(index * 10, actual.timeMs)
            assertEquals(expected.command.logNameForTest(), actual.command!!.logNameForTest())
        }
    }

    @Test
    fun `оборванный журнал отдаёт всё, что успело записаться`() {
        val full = write(sample)
        val cut = full.copyOf(full.size - 3)
        val result = JournalFormat.read(ByteArrayInputStream(cut))
        assertTrue(result.truncated)
        assertEquals(sample.size - 1, result.entries.size)
    }

    @Test
    fun `чужой файл не принимается за журнал`() {
        assertFailsWith<IllegalArgumentException> {
            JournalFormat.read(ByteArrayInputStream("definitely not a journal".toByteArray()))
        }
    }

    @Test
    fun `контрольные суммы различают порядок и тик`() {
        val (id, payload) = CommandRegistry.encode(SimulationIntent.SetPaused(true))!!
        val (id2, payload2) = CommandRegistry.encode(SimulationIntent.SetPaused(false))!!
        val ab = JournalFormat.hashUi(JournalFormat.hashUi(Fnv.OFFSET, id, payload), id2, payload2)
        val ba = JournalFormat.hashUi(JournalFormat.hashUi(Fnv.OFFSET, id2, payload2), id, payload)
        assertTrue(ab != ba)

        val atTen = JournalFormat.hashSim(Fnv.OFFSET, 1, 10, id, payload)
        val atEleven = JournalFormat.hashSim(Fnv.OFFSET, 1, 11, id, payload)
        assertTrue(atTen != atEleven)
    }

    @Test
    fun `bug упаковывается и распаковывается, путь из песочницы не выходит`() {
        val dir = createTempDirectory("genomeia-bug").toFile()
        try {
            val journal = File(dir, "journal.bin").apply { writeBytes(write(sample)) }
            val bug = File(dir, "test.bug")
            BugFile.write(
                bug, listOf(
                    BugFile.MANIFEST to BugFile.Content.Bytes(BugFile.formatManifest(mapOf("game" to "test", "description" to "жук"))),
                    BugFile.JOURNAL to BugFile.Content.FromFile(journal),
                    "attachments/genomes/a.genome" to BugFile.Content.Bytes(byteArrayOf(1, 2, 3))
                )
            )

            val all = BugFile.readAll(bug)
            assertEquals("жук", BugFile.parseManifest(all.getValue(BugFile.MANIFEST))["description"])
            assertContentEquals(journal.readBytes(), all.getValue(BugFile.JOURNAL))

            val unpacked = File(dir, "out")
            BugFile.unpack(bug, unpacked)
            assertContentEquals(byteArrayOf(1, 2, 3), File(unpacked, "attachments/genomes/a.genome").readBytes())

            val evil = File(dir, "evil.bug")
            BugFile.write(evil, listOf("../escape.txt" to BugFile.Content.Bytes(byteArrayOf(1))))
            assertFailsWith<IllegalArgumentException> { BugFile.unpack(evil, File(dir, "out2")) }
            assertFalse(File(dir, "escape.txt").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun GameCommand.logNameForTest() = "$name|$detail"
}
