package io.github.some_example_name.old.systems.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * API уровня для Lua: что скрипт может попросить показать, когда получает события и
 * что из окружения ему недоступно.
 *
 * Без Gdx: EnvRun создаёт только VM и правила, окна и лог трогают Gdx лишь при вызове
 * из игры.
 */
class LevelLuaApiTest {

    @Test
    fun `цель, прогресс, метка и зона доезжают до экрана`() {
        val env = EnvRun()
        env.setScript(
            """
            objective("Доплыви до метки")
            progress(0, 3)
            marker(10, 20)
            zone(10, 20, 5)
            """.trimIndent()
        )

        assertEquals("", env.run())

        val hud = env.env.rules.hud
        assertEquals("Доплыви до метки", hud.objective)
        assertEquals(0, hud.progress)
        assertEquals(3, hud.progressMax)
        assertTrue(hud.hasMarker)
        assertEquals(10f, hud.markerX)
        assertTrue(hud.hasZone)
        assertEquals(5f, hud.zoneRadius)
    }

    @Test
    fun `marker без аргументов убирает метку`() {
        val env = EnvRun()
        env.setScript("marker(1, 2) marker()")

        assertEquals("", env.run())
        assertFalse(env.env.rules.hud.hasMarker)
    }

    @Test
    fun `событие по клеткам срабатывает один раз`() {
        val env = EnvRun()
        env.setScript(
            """
            onCells(3, function() objective("клетки есть") end)
            """.trimIndent()
        )
        assertEquals("", env.run())

        env.env.rules.step(2)
        assertEquals("", env.env.rules.hud.objective)

        env.env.rules.step(3)
        assertEquals("клетки есть", env.env.rules.hud.objective)

        // Второй раз порог уже пройден — обработчик не повторяется.
        env.setScript("")
        env.env.rules.step(10)
        assertEquals("клетки есть", env.env.rules.hud.objective)
    }

    @Test
    fun `отложенное событие по тикам срабатывает на своём тике`() {
        val env = EnvRun()
        env.setScript("""afterTicks(2, function() objective("прошло два тика") end)""")
        assertEquals("", env.run())

        env.env.rules.step(1)
        assertEquals("", env.env.rules.hud.objective)

        env.env.rules.step(1)
        assertEquals("прошло два тика", env.env.rules.hud.objective)
    }

    @Test
    fun `лимит времени проваливает уровень без своего обработчика`() {
        val env = EnvRun()
        env.setScript("timeLimit(0.05)")
        assertEquals("", env.run())

        repeat(3) { env.env.rules.step(1) }
        assertNotNull(env.env.rules.failReason)
    }

    @Test
    fun `lose отдаёт причину провала`() {
        val env = EnvRun()
        env.setScript("""lose("организм погиб")""")

        assertEquals("", env.run())
        assertEquals("организм погиб", env.env.rules.failReason)
    }

    @Test
    fun `случайность уровня повторяется от сида`() {
        val first = LevelRules().apply { seed(12345) }
        val second = LevelRules().apply { seed(12345) }

        repeat(8) {
            assertEquals(first.randomInt(0, 100), second.randomInt(0, 100))
        }
    }

    @Test
    fun `новый запуск уровня стирает прошлые правила`() {
        val rules = LevelRules()
        rules.setObjective("старое")
        rules.fail("провал")
        rules.setTimeLimit(10f)

        rules.reset()

        assertEquals("", rules.hud.objective)
        assertEquals(null, rules.failReason)
    }

    @Test
    fun `os и io недоступны скрипту уровня`() {
        val env = EnvRun()

        for (name in listOf("os", "io", "debug", "require", "dofile", "loadfile", "load")) {
            assertFalse(env.globals.globals.containsKey(name), "глобал $name не вырезан")
        }

        // Обращение к os — обычная ошибка Lua, а не падение игры.
        env.setScript("""os.execute("echo 1")""")
        assertTrue(env.run().isNotEmpty())
    }

    @Test
    fun `чистые библиотеки остались`() {
        val env = EnvRun()

        for (name in listOf("string", "table", "math", "utf8")) {
            assertTrue(env.globals.globals.containsKey(name), "глобал $name пропал")
        }
    }
}
