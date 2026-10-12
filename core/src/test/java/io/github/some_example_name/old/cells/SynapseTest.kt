package io.github.some_example_name.old.cells

import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Правила обучения Synapse без симуляции: чтение параметров из полей a, b, c,
 * окно обучения и шаг веса.
 */
class SynapseTest {

    // ==================== Параметры ====================

    @Test
    fun `learningRate и depression - ноль и меньше значат значение по умолчанию`() {
        // Старые геномы: синапс получал b = c = 0 от обычного нейрона.
        assertEquals(Synapse.DEFAULT_LEARNING_RATE, Synapse.learningRate(0f))
        assertEquals(Synapse.DEFAULT_LEARNING_RATE, Synapse.learningRate(-0.5f))
        assertEquals(Synapse.DEFAULT_LEARNING_RATE, Synapse.learningRate(Float.NaN))
        assertEquals(Synapse.DEFAULT_DEPRESSION, Synapse.depression(0f))
        assertEquals(Synapse.DEFAULT_DEPRESSION, Synapse.depression(Float.NaN))

        assertEquals(0.05f, Synapse.learningRate(0.05f))
        assertEquals(0.02f, Synapse.depression(0.02f))
    }

    @Test
    fun `learningRate и depression - зажаты сверху единицей`() {
        assertEquals(1f, Synapse.learningRate(Float.POSITIVE_INFINITY))
        assertEquals(1f, Synapse.depression(7f))
    }

    @Test
    fun `decayRate - ноль без затухания, отрицательное и бесконечность зажаты`() {
        assertEquals(0f, Synapse.decayRate(0f))
        assertEquals(0f, Synapse.decayRate(-1f))
        assertEquals(0.3f, Synapse.decayRate(0.3f))
        assertEquals(Synapse.DEFAULT_DECAY_RATE, Synapse.decayRate(Float.NaN))
        assertTrue(Synapse.decayRate(Float.POSITIVE_INFINITY).isFinite())
    }

    // ==================== Окно обучения ====================

    @Test
    fun `окно - то же, что считала прежняя версия при параметрах по умолчанию`() {
        val legacy = (-ln(0.001f / Synapse.DEFAULT_LEARNING_RATE) / Synapse.DEFAULT_DECAY_RATE).roundToInt()
        val window = Synapse.learningWindowTicks(
            Synapse.DEFAULT_DECAY_RATE,
            Synapse.DEFAULT_LEARNING_RATE,
            Synapse.DEFAULT_DEPRESSION
        )
        assertEquals(legacy, window)
        assertEquals(92, window)
    }

    @Test
    fun `окно - без затухания фиксированное`() {
        assertEquals(Synapse.NO_DECAY_WINDOW_TICKS, Synapse.learningWindowTicks(0f, 0.01f, 0.008f))
    }

    @Test
    fun `окно - считается по большей из двух скоростей`() {
        val byDepression = Synapse.learningWindowTicks(0.025f, 0.01f, 0.1f)
        val byLearning = Synapse.learningWindowTicks(0.025f, 0.1f, 0.01f)
        assertEquals(byLearning, byDepression)
        assertTrue(byDepression > Synapse.learningWindowTicks(0.025f, 0.01f, 0.008f))
    }

    @Test
    fun `окно - не меньше тика даже при огромном затухании и крошечных скоростях`() {
        assertEquals(1, Synapse.learningWindowTicks(Synapse.decayRate(1e30f), 1f, 1f))
        assertEquals(1, Synapse.learningWindowTicks(0.025f, 0.0005f, 0.0005f))
    }

    // ==================== Шаг веса ====================

    @Test
    fun `вход раньше учителя - усиление, учитель раньше входа - ослабление`() {
        val up = Synapse.plasticity(0.5f, dt = 10, learningRate = 0.01f, depression = 0.008f, decayRate = 0.025f)
        val down = Synapse.plasticity(0.5f, dt = -10, learningRate = 0.01f, depression = 0.008f, decayRate = 0.025f)

        assertEquals(0.5f + 0.01f * exp(-10 * 0.025f), up, 1e-6f)
        assertEquals(0.5f - 0.008f * exp(-10 * 0.025f), down, 1e-6f)
    }

    @Test
    fun `одновременные спайки - полное усиление`() {
        assertEquals(0.51f, Synapse.plasticity(0.5f, 0, 0.01f, 0.008f, 0.025f), 1e-6f)
    }

    @Test
    fun `вес не выходит из 0-1`() {
        assertEquals(1f, Synapse.plasticity(0.99f, 0, 1f, 1f, 0f))
        assertEquals(0f, Synapse.plasticity(0.01f, -1, 1f, 1f, 0f))
    }

    @Test
    fun `вес не становится NaN при крайних значениях полей`() {
        val learningRate = Synapse.learningRate(Float.POSITIVE_INFINITY)
        val depression = Synapse.depression(Float.POSITIVE_INFINITY)
        val decayRate = Synapse.decayRate(Float.POSITIVE_INFINITY)

        for (dt in intArrayOf(-100_000, -1, 0, 1, 100_000)) {
            val weight = Synapse.plasticity(0.5f, dt, learningRate, depression, decayRate)
            assertTrue(weight in 0f..1f, "dt=$dt дал вес $weight")
        }
    }
}
