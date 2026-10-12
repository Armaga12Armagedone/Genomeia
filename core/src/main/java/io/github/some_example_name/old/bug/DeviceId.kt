package io.github.some_example_name.old.bug

import com.badlogic.gdx.Gdx
import java.util.UUID

/**
 * Идентификатор установки игры — чтобы отличать .bug разных игроков и видеть, что два
 * отчёта пришли с одного устройства.
 *
 * Не железный ID устройства: такие на Android закрыты разрешениями и по-хорошему не
 * должны покидать устройство. Это случайное число, которое игра заводит при первом
 * запуске и хранит в своих настройках. Переустановил игру — новый ID; для отчётов о
 * багах этого достаточно.
 */
object DeviceId {

    private const val PREFERENCES = "genomeia-device"
    private const val KEY = "id"

    @Volatile
    private var cached: String? = null

    fun get(): String {
        cached?.let { return it }
        val id = try {
            val prefs = Gdx.app.getPreferences(PREFERENCES)
            prefs.getString(KEY, null) ?: newId().also {
                prefs.putString(KEY, it)
                prefs.flush()
            }
        } catch (_: Exception) {
            // Настройки недоступны (краш на старте) — отчёт важнее стабильного ID.
            "nodevice"
        }
        cached = id
        return id
    }

    /** 12 hex-символов: короче UUID, но для отличения установок хватает с запасом. */
    private fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
}
