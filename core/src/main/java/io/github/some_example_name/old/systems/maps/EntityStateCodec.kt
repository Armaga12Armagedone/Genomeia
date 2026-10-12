package io.github.some_example_name.old.systems.maps

import io.github.some_example_name.old.entities.Entity
import io.github.some_example_name.old.entities.EntityState
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import kotlin.reflect.KProperty0

/**
 * Двоичный формат одной сущности: подряд записи «имя, тип, длина, данные» до конца потока.
 *
 * Не protobuf, потому что массивы здесь — сотни тысяч элементов, а kotlinx-protobuf пишет
 * их поэлементно с тегом на каждый. Здесь массив ложится одним блоком через ByteBuffer.
 */
private const val TYPE_INT = 1
private const val TYPE_FLOAT = 2
private const val TYPE_BOOLEAN = 3
private const val TYPE_BYTE = 4

fun Entity.encodeState(): ByteArray {
    // Пишутся слоты 0..lastId: дальше только запас ёмкости со значениями по умолчанию.
    val writer = EntityStateWriter(slots = lastId + 1)
    saveState(writer)
    return writer.toByteArray()
}

fun Entity.decodeState(bytes: ByteArray) {
    loadState(EntityStateReader(bytes))
}

private class EntityStateWriter(private val slots: Int) : EntityState {
    private val bytes = ByteArrayOutputStream()
    private val out = DataOutputStream(bytes)

    override val isReading = false

    fun toByteArray(): ByteArray {
        out.flush()
        return bytes.toByteArray()
    }

    override fun ints(field: KProperty0<IntArray>, perSlot: Int) = writeInts(field.name, field.get(), slots * perSlot)

    override fun floats(field: KProperty0<FloatArray>) {
        val count = slots
        val buffer = header(field.name, TYPE_FLOAT, count, Float.SIZE_BYTES)
        buffer.asFloatBuffer().put(field.get(), 0, count)
        out.write(buffer.array())
    }

    override fun booleans(field: KProperty0<BooleanArray>) {
        val array = field.get()
        val count = slots
        val data = ByteArray(count) { if (array[it]) 1 else 0 }
        header(field.name, TYPE_BOOLEAN, count, 0)
        out.write(data)
    }

    override fun bytes(field: KProperty0<ByteArray>) {
        val count = slots
        header(field.name, TYPE_BYTE, count, 0)
        out.write(field.get(), 0, count)
    }

    override fun intList(field: KProperty0<IntArrayList>) {
        val list = field.get()
        writeInts(field.name, list.elements(), list.size)
    }

    override fun int(name: String, value: Int): Int {
        writeInts(name, intArrayOf(value), 1)
        return value
    }

    override fun packed(name: String, write: () -> IntArray, read: (IntArray) -> Unit) {
        val values = write()
        writeInts(name, values, values.size)
    }

    private fun writeInts(name: String, array: IntArray, count: Int) {
        val buffer = header(name, TYPE_INT, count, Int.SIZE_BYTES)
        buffer.asIntBuffer().put(array, 0, count)
        out.write(buffer.array())
    }

    /** Пишет заголовок записи и отдаёт буфер под данные, если элемент шире байта. */
    private fun header(name: String, type: Int, count: Int, elementBytes: Int): ByteBuffer {
        out.writeUTF(name)
        out.writeByte(type)
        out.writeInt(count)
        return ByteBuffer.allocate(count * elementBytes)
    }
}

private class EntityStateReader(private val bytes: ByteArray) : EntityState {

    private class Field(val type: Int, val offset: Int, val count: Int)

    private val fields = HashMap<String, Field>()

    init {
        val input = DataInputStream(bytes.inputStream())
        while (input.available() > 0) {
            val name = input.readUTF()
            val type = input.readByte().toInt()
            val count = input.readInt()
            val length = count * elementBytes(type)
            fields[name] = Field(type, offset = bytes.size - input.available(), count = count)
            check(input.skipBytes(length) == length) { "поле $name обрезано: файл повреждён" }
        }
    }

    override val isReading = true

    override fun ints(field: KProperty0<IntArray>, perSlot: Int) {
        val saved = find(field.name, TYPE_INT) ?: return
        val array = field.get()
        checkFits(field.name, saved.count, array.size)
        ByteBuffer.wrap(bytes, saved.offset, saved.count * Int.SIZE_BYTES).asIntBuffer().get(array, 0, saved.count)
    }

    override fun floats(field: KProperty0<FloatArray>) {
        val saved = find(field.name, TYPE_FLOAT) ?: return
        val array = field.get()
        checkFits(field.name, saved.count, array.size)
        ByteBuffer.wrap(bytes, saved.offset, saved.count * Float.SIZE_BYTES).asFloatBuffer().get(array, 0, saved.count)
    }

    override fun booleans(field: KProperty0<BooleanArray>) {
        val saved = find(field.name, TYPE_BOOLEAN) ?: return
        val array = field.get()
        checkFits(field.name, saved.count, array.size)
        for (i in 0 until saved.count) array[i] = bytes[saved.offset + i] != 0.toByte()
    }

    override fun bytes(field: KProperty0<ByteArray>) {
        val saved = find(field.name, TYPE_BYTE) ?: return
        val array = field.get()
        checkFits(field.name, saved.count, array.size)
        System.arraycopy(bytes, saved.offset, array, 0, saved.count)
    }

    override fun intList(field: KProperty0<IntArrayList>) {
        val values = readInts(field.name) ?: return
        val list = field.get()
        list.clear()
        list.addElements(0, values)
    }

    override fun int(name: String, value: Int): Int = readInts(name)?.singleOrNull() ?: value

    override fun packed(name: String, write: () -> IntArray, read: (IntArray) -> Unit) {
        readInts(name)?.let(read)
    }

    private fun readInts(name: String): IntArray? {
        val saved = find(name, TYPE_INT) ?: return null
        val values = IntArray(saved.count)
        ByteBuffer.wrap(bytes, saved.offset, saved.count * Int.SIZE_BYTES).asIntBuffer().get(values)
        return values
    }

    private fun find(name: String, type: Int): Field? {
        val saved = fields[name] ?: return null
        check(saved.type == type) { "поле $name сохранено с типом ${saved.type}, а читается как $type" }
        return saved
    }

    /**
     * Массивы к этому моменту уже выращены под сохранённый lastId (Entity.loadState), так
     * что не влезть сохранённое может только из битого файла или после смены perSlot.
     */
    private fun checkFits(name: String, count: Int, capacity: Int) {
        check(count <= capacity) { "поле $name: в сохранении $count элементов, места под $capacity" }
    }
}

private fun elementBytes(type: Int) = when (type) {
    TYPE_INT -> Int.SIZE_BYTES
    TYPE_FLOAT -> Float.SIZE_BYTES
    TYPE_BOOLEAN, TYPE_BYTE -> 1
    else -> throw IllegalStateException("неизвестный тип поля $type")
}
