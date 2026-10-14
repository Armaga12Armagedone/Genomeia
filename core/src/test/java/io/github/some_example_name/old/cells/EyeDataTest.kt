package io.github.some_example_name.old.cells

import io.github.some_example_name.old.systems.genomics.genome.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Параметры глаза в своих данных клетки ведут себя так же, как в прежней EyeEntity. */
class EyeDataTest {

    private val eye = Eye(14)

    @Test
    fun `новый глаз — из полей генома, без них — по умолчанию`() {
        assertEquals(EyeData(3, 2.5f), eye.createSpecialData(Action(colorRecognition = 3, lengthDirected = 2.5f)))
        assertEquals(EyeData(3, 4.25f), eye.createSpecialData(Action(colorRecognition = 3)))
        // Без полей — общий объект по умолчанию, без аллокации.
        assertSame(EyeData.DEFAULT, eye.createSpecialData(Action()))
    }

    @Test
    fun `мутация глаза в глаз меняет только заданные поля`() {
        val current = EyeData(5, 6f)
        assertEquals(EyeData(2, 6f), eye.mutateSpecialData(current, Action(colorRecognition = 2)))
        assertEquals(EyeData(5, 1f), eye.mutateSpecialData(current, Action(lengthDirected = 1f)))
        assertNull(eye.mutateSpecialData(current, Action(a = 3f)))
    }
}
