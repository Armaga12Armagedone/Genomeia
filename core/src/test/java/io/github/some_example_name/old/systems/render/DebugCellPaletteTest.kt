package io.github.some_example_name.old.systems.render

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Отладочная раскраска без Gdx: затемнение сжатой мышцы и буфер фигур снимка.
 *
 * Саму палитру (таблицы по списку клеток) здесь не строим: у клеток в конструкторе есть
 * обращение к I18NBundle из DI-контейнера, а он тянет OpenGL.
 */
class DebugCellPaletteTest {

    /** Color.toIntBits: младший байт — красный, старший — альфа. */
    private fun abgr(r: Int, g: Int, b: Int, a: Int) = (a shl 24) or (b shl 16) or (g shl 8) or r

    @Test
    fun `несжатая мышца — ровно свой цвет`() {
        val red = abgr(205, 0, 0, 255)
        assertEquals(red, DebugCellPalette.darken(red, 0f))
    }

    @Test
    fun `сжатая мышца темнеет до 45 процентов, альфа не трогается`() {
        val color = abgr(200, 100, 40, 180)
        val dark = DebugCellPalette.darken(color, 1f)
        assertEquals(abgr(90, 45, 18, 180), dark)
    }

    @Test
    fun `сжатие за пределами 0 1 зажимается, а не переворачивает цвет`() {
        val color = abgr(200, 100, 40, 255)
        assertEquals(DebugCellPalette.darken(color, 1f), DebugCellPalette.darken(color, 7f))
        assertEquals(color, DebugCellPalette.darken(color, -3f))
    }

    @Test
    fun `буфер снимка растёт и не теряет записанное`() {
        val overlay = RenderOverlayBufferData(initialCapacity = 1)
        repeat(100) { i -> overlay.add(i.toFloat(), -i.toFloat(), i + 0.5f, 0f, i, i * 2) }
        assertEquals(100, overlay.size)
        for (i in 0 until 100) {
            assertEquals(i.toFloat(), overlay.x0[i])
            assertEquals(-i.toFloat(), overlay.y0[i])
            assertEquals(i + 0.5f, overlay.x1[i])
            assertEquals(i, overlay.color[i])
            assertEquals(i * 2, overlay.params[i])
        }

        overlay.clear()
        assertEquals(0, overlay.size)
    }
}
