package io.github.some_example_name.old.systems.simulation

import kotlin.test.Test
import kotlin.test.assertTrue

class SimulationDataTest {

    /**
     * Пауза принадлежит экрану: диалог провала и окно уровня ставят её напрямую в
     * SimulationData, а уходя из симуляции её никто не снимает. Новый мир собирается
     * через clear(), и он обязан начаться идущим — иначе следующий уровень из редактора
     * открывается стоящим на тике 0 (поток обновления при isPlay=false просто спит).
     */
    @Test
    fun `clear снимает паузу прошлого экрана`() {
        val data = SimulationData()
        data.isPlay = false

        data.clear()

        assertTrue(data.isPlay)
    }
}
