package io.github.some_example_name.old.systems.genomics.genome

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Номера полей (@ProtoNumber) фиксируйте раз и навсегда. Никогда не меняйте существующие номера и не переиспользуйте удалённые */


@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Genome(
    @ProtoNumber(1) val version: Int,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val stageInstruction: List<GenomeStage> = emptyList(),
    @ProtoNumber(4) val subGenomes: Map<Int, SubGenome> = emptyMap()
) {
    @Transient
    var genomeStageInstruction: List<GenomeStage> = stageInstruction

    @Transient
    var dividedTimes = IntArray(0)

    @Transient
    var mutatedTimes = IntArray(0)
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SubGenome(
    @ProtoNumber(1) val genomeStageInstruction: List<GenomeStage> = emptyList()
) {
    @Transient
    var dividedTimes = IntArray(0)

    @Transient
    var mutatedTimes = IntArray(0)
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GenomeStage(
    @ProtoNumber(1) val cellActions: Map<Int, CellAction> = emptyMap()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CellAction(
    @ProtoNumber(1) val divide: Action? = null,
    @ProtoNumber(2) val mutate: Action? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Action(
    @ProtoNumber(1) val id: Int = -1,
    @ProtoNumber(2) val angle: Float? = null,
    @ProtoNumber(3) val cellType: Int? = null,
    @ProtoNumber(4) val physicalLink: Map<Int, LinkData?> = emptyMap(),
    @ProtoNumber(5) @Serializable(with = ColorSerializer::class) val color: Color? = null,
    @ProtoNumber(6) val radius: Float? = null,
    @ProtoNumber(7) val angleDirected: Float? = null,
    @ProtoNumber(8) val funActivation: Int? = null,
    @ProtoNumber(9) val a: Float? = null,
    @ProtoNumber(10) val b: Float? = null,
    @ProtoNumber(11) val c: Float? = null,
    @ProtoNumber(12) val isSum: Boolean? = null,
    @ProtoNumber(13) val colorRecognition: Int? = null,
    @ProtoNumber(14) val lengthDirected: Float? = null,
    @ProtoNumber(15) val pheromoneType: Int? = null
) {

    @Transient
    val physicalLinkMirroredForCell: MutableMap<Int, LinkData?> = physicalLink.toMutableMap()

}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LinkData(
    @ProtoNumber(1) val length: Float? = null,
    @ProtoNumber(2) val isNeuronal: Boolean = false,
    @ProtoNumber(3) @Serializable(with = ColorSerializer::class) val color: Color? = null,
    @ProtoNumber(4) val weight: Float? = null,
    @ProtoNumber(5) val directedNeuronLink: Int? = null,
    @ProtoNumber(6) val isExtra: Boolean = false
)


object ColorSerializer : KSerializer<Color> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("gdx.Color", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeInt(value.toIntBits())
    }

    override fun deserialize(decoder: Decoder): Color {
        val intBits = decoder.decodeInt()
        val color = Color()
        Color.abgr8888ToColor(color, intBits)
        return color
    }
}


private const val MAGIC = "genomeia"
private const val CURRENT_FORMAT_VERSION = 1

// ==================== СОХРАНЕНИЕ ====================
@OptIn(ExperimentalSerializationApi::class)
fun saveGenome(genome: Genome, fileName: String) {
    val protobufBytes = ProtoBuf.encodeToByteArray(genome)

    val byteArrayOutput = ByteArrayOutputStream()
    val dataOutput = DataOutputStream(byteArrayOutput)

    try {
        // 1. Magic Number (8 байт)
        dataOutput.writeBytes(MAGIC)

        // 2. Версия формата (4 байта)
        dataOutput.writeInt(CURRENT_FORMAT_VERSION)

        // 3. Данные protobuf
        dataOutput.write(protobufBytes)

        // Сохраняем файл с расширением .genome
        val file = Gdx.files.local("genomes/$fileName.genome")
        file.writeBytes(byteArrayOutput.toByteArray(), false)

    } finally {
        dataOutput.close()
    }
}

// ==================== ЗАГРУЗКА ====================
@OptIn(ExperimentalSerializationApi::class)
fun loadGenome(fileName: String): Genome {
    val file = Gdx.files.local("genomes/$fileName.genome")

    if (!file.exists()) {
        throw IllegalStateException("Файл генома не найден: $fileName.genome")
    }

    val allBytes = file.readBytes()
    val byteArrayInput = ByteArrayInputStream(allBytes)
    val dataInput = DataInputStream(byteArrayInput)

    try {
        // 1. Проверяем Magic Number
        val magicBytes = ByteArray(8)
        dataInput.readFully(magicBytes)
        val magic = String(magicBytes, Charsets.UTF_8)

        if (magic != MAGIC) {
            throw IllegalStateException("Неверный формат файла (Magic Number не совпадает)")
        }

        // 2. Читаем версию формата
        val fileVersion = dataInput.readInt()

        // Здесь можно добавить логику миграции, если версия старая
        if (fileVersion > CURRENT_FORMAT_VERSION) {
            throw IllegalStateException("Файл сохранён в более новой версии игры")
        }

        // 3. Читаем оставшиеся байты как protobuf
        val remainingBytes = ByteArray(dataInput.available())
        dataInput.readFully(remainingBytes)

        return ProtoBuf.decodeFromByteArray(remainingBytes)

    } finally {
        dataInput.close()
    }
}
