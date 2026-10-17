package io.github.some_example_name.old.journal

import io.github.some_example_name.old.commands.GameCommand
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream

/**
 * Одна запись журнала сессии.
 *
 * @param nested команда выполнена ВНУТРИ другой команды (пункт меню открыл экран, и
 *   переход записан отдельно). При повторе такие не подаются — их заново сделает
 *   внешняя команда, — в журнале они ради полноты картины.
 * @param epoch номер сессии симуляции за этот запуск игры: растёт при каждом входе в мир
 *   и при каждом «Перезапустить». Тик без эпохи ничего не значит — после рестарта тики
 *   снова идут с нуля.
 * @param tick для SIM — точный тик, в котором команда применилась. Для UI — тик, который
 *   шёл в мире в момент нажатия (или -1, если мира не было): при повторе UI-команда
 *   ждёт, пока мир дойдёт до этого тика.
 * @param timeMs миллисекунды от начала записи — только для чтения человеком.
 * @param command null — тип не знаком этой версии игры (журнал из более новой).
 */
class JournalEntry(
    val channel: Channel,
    val source: CommandSource,
    val nested: Boolean,
    val epoch: Int,
    val tick: Int,
    val timeMs: Int,
    val typeId: Int,
    val payload: ByteArray,
    val command: GameCommand?
)

/**
 * Двоичный формат журнала: заголовок, потом записи подряд до конца файла.
 *
 * Конец не помечается: журнал пишется на лету, и при краше или убитом процессе файл
 * просто обрывается. Чтение останавливается на первой неполной записи — всё, что до
 * неё, остаётся годным.
 *
 * Запись:
 *   byte  TAG
 *   byte  канал (бит 7 — nested)
 *   byte  источник
 *   int   эпоха
 *   int   тик
 *   int   мс от начала
 *   short id типа команды (CommandRegistry)
 *   int   длина тела
 *   bytes тело — ProtoBuf самой команды
 */
object JournalFormat {

    /** "genomjrn": та же манера, что у файлов геномов. */
    private val MAGIC = "genomjrn".toByteArray(Charsets.US_ASCII)
    const val VERSION = 1

    private const val TAG_ENTRY = 0x45
    private const val NESTED_BIT = 0x80

    /** Защита от мусора вместо длины: команда больше мегабайта — это порча, а не команда. */
    private const val MAX_PAYLOAD = 1 shl 20

    fun writeHeader(out: DataOutputStream) {
        out.write(MAGIC)
        out.writeInt(VERSION)
    }

    fun writeEntry(
        out: DataOutputStream,
        channel: Channel,
        source: CommandSource,
        nested: Boolean,
        epoch: Int,
        tick: Int,
        timeMs: Int,
        typeId: Int,
        payload: ByteArray
    ) {
        out.writeByte(TAG_ENTRY)
        out.writeByte(channel.code or (if (nested) NESTED_BIT else 0))
        out.writeByte(source.code)
        out.writeInt(epoch)
        out.writeInt(tick)
        out.writeInt(timeMs)
        out.writeShort(typeId)
        out.writeInt(payload.size)
        out.write(payload)
    }

    class ReadResult(
        val entries: List<JournalEntry>,
        /** Файл оборван посреди записи (краш, убитый процесс). Прочитанное — годное. */
        val truncated: Boolean,
        /** Записи с типами, которых эта версия игры не знает. */
        val unknownTypes: Int
    )

    fun read(input: InputStream): ReadResult {
        val data = DataInputStream(input.buffered())
        val magic = ByteArray(MAGIC.size)
        data.readFully(magic)
        require(magic.contentEquals(MAGIC)) { "это не журнал Genomeia" }
        val version = data.readInt()
        require(version == VERSION) { "журнал версии $version, а игра читает $VERSION" }

        val entries = ArrayList<JournalEntry>()
        var unknown = 0
        var truncated = false
        while (true) {
            val tag = data.read()
            if (tag == -1) break
            if (tag != TAG_ENTRY) {
                truncated = true
                break
            }
            try {
                val channelByte = data.readUnsignedByte()
                val source = CommandSource.of(data.readUnsignedByte())
                val epoch = data.readInt()
                val tick = data.readInt()
                val timeMs = data.readInt()
                val typeId = data.readUnsignedShort()
                val size = data.readInt()
                if (size < 0 || size > MAX_PAYLOAD) {
                    truncated = true
                    break
                }
                val payload = ByteArray(size)
                data.readFully(payload)

                val command = CommandRegistry.decode(typeId, payload)
                if (command == null) unknown++
                entries += JournalEntry(
                    channel = if (channelByte and 0x7F == Channel.SIM.code) Channel.SIM else Channel.UI,
                    source = source,
                    nested = channelByte and NESTED_BIT != 0,
                    epoch = epoch,
                    tick = tick,
                    timeMs = timeMs,
                    typeId = typeId,
                    payload = payload,
                    command = command
                )
            } catch (_: EOFException) {
                truncated = true
                break
            }
        }
        return ReadResult(entries, truncated, unknown)
    }

    /**
     * Контрольная сумма UI-канала: только внешние команды (их и подаёт повтор), только
     * тип и тело — тик UI-команды приблизительный и в сумму не входит.
     */
    fun hashUi(hash: Long, typeId: Int, payload: ByteArray): Long =
        Fnv.bytes(Fnv.int(hash, typeId), payload)

    /** Контрольная сумма SIM-канала: здесь тик точный и входит в сумму вместе с эпохой. */
    fun hashSim(hash: Long, epoch: Int, tick: Int, typeId: Int, payload: ByteArray): Long =
        Fnv.bytes(Fnv.int(Fnv.int(Fnv.int(hash, epoch), tick), typeId), payload)
}
