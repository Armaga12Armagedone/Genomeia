package io.github.some_example_name.old.systems.genomics.genome

import io.github.some_example_name.old.cells.ControllerData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Формат Action в protobuf: тип клетки и формула — ключами, старые номера только читаются.
 *
 * От этого зависит, откроются ли у игроков геномы, сохранённые миры и журналы .bug,
 * записанные до перехода на ключи: все они хранят Action именно так.
 */
@OptIn(ExperimentalSerializationApi::class)
class ActionFormatTest {

    /** Action, каким его писала игра до ключей: тип клетки и формула — номерами. */
    @Serializable
    private class OldAction(
        @ProtoNumber(1) val id: Int = -1,
        @ProtoNumber(3) val cellType: Int? = null,
        @ProtoNumber(8) val funActivation: Int? = null,
        @ProtoNumber(9) val a: Float? = null
    )

    private fun decode(old: OldAction): Action =
        GenomeProto.decodeFromByteArray(Action.serializer(), ProtoBuf.encodeToByteArray(OldAction.serializer(), old))

    @Test
    fun `старые номера типа клетки и формулы читаются как ключи`() {
        val action = decode(OldAction(id = 7, cellType = 14, funActivation = 3, a = 2f))

        assertEquals(7, action.id)
        assertEquals("eye", action.cellType)
        assertEquals("sigmoid", action.funActivation)
        assertEquals(2f, action.a)
    }

    @Test
    fun `старый номер формулы вне списка — тождество, как в старой симуляции`() {
        assertEquals(LegacyTypeIds.IDENTITY_FORMULA, decode(OldAction(cellType = 4, funActivation = 25)).funActivation)
    }

    @Test
    fun `неизвестный старый номер клетки — понятная ошибка, а не чужая клетка`() {
        assertFailsWith<IllegalStateException> { decode(OldAction(cellType = 99)) }
    }

    @Test
    fun `новый Action пишется только ключами`() {
        val bytes = GenomeProto.encodeToByteArray(Action.serializer(), Action(cellType = "neuron", funActivation = "spike"))
        val asOld = ProtoBuf.decodeFromByteArray(OldAction.serializer(), bytes)

        assertNull(asOld.cellType)
        assertNull(asOld.funActivation)
        assertEquals(
            Action(cellType = "neuron", funActivation = "spike"),
            GenomeProto.decodeFromByteArray(Action.serializer(), bytes)
        )
    }

    @Test
    fun `свои данные клетки переживают запись и чтение генома`() {
        val action = Action(id = 3, cellType = "controller", funActivation = "linear", special = ControllerData("SPACE"))
        val genome = Genome(
            version = 24,
            name = "controller",
            stageInstruction = listOf(GenomeStage(mapOf(0 to CellAction(divide = action))))
        )

        val read = decodeGenome(encodeGenome(genome))
        val readAction = read.stageInstruction[0].cellActions.getValue(0).divide!!

        assertEquals(action, readAction)
        // Номер клавиши считается при чтении из её имени, в файл он не попадает.
        assertEquals(14, (readAction.special as ControllerData).keyIndex)
    }
}
