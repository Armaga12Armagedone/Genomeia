package io.github.some_example_name.old.features.settings

import com.badlogic.gdx.Gdx
import java.util.Locale

class SettingsViewModel {


    /**
     * Надёжное получение доступных языков.
     * Не использует list() — он не работает с internal-файлами.
     */
    fun getAvailableLanguages(): List<Locale> {
        // Список всех языков, которые ты поддерживаешь (добавляй сюда новые)
        val candidates = listOf(
            Locale.ENGLISH,
            Locale.forLanguageTag("ru"),
            Locale.forLanguageTag("uk"),
            Locale.forLanguageTag("de"),
            Locale.forLanguageTag("fr"),
            Locale.forLanguageTag("es"),
            Locale.forLanguageTag("pl"),
            Locale.forLanguageTag("pt"),
            Locale.forLanguageTag("tr"),
            Locale.forLanguageTag("id"),
        )

        val available = mutableListOf<Locale>()

        for (locale in candidates) {
            val fileName = buildPropertiesFileName(locale)
            if (Gdx.files.internal(fileName).exists()) {
                available.add(locale)
            }
        }

        // Если ничего не нашли — возвращаем системный
        return available.ifEmpty { listOf(Locale.getDefault()) }
    }

    /** Формирует имя файла properties по Locale */
    private fun buildPropertiesFileName(locale: Locale): String {
        val base = "ui/i18n/MyBundle"

        // Корневой файл (без суффикса) обычно соответствует английскому
        if (locale.language.isEmpty() || locale == Locale.ENGLISH || locale.language == "en") {
            // Сначала пробуем корневой, потом _en
            if (Gdx.files.internal("$base.properties").exists()) {
                return "$base.properties"
            }
            return "${base}_en.properties"
        }

        // Обычный случай: MyBundle_ru.properties, MyBundle_uk.properties и т.д.
        val tag = locale.toLanguageTag().replace('-', '_')   // ru → ru, ru-RU → ru_RU
        return "${base}_$tag.properties"
    }
}

// === Глобальные настройки ===
object GlobalSettings {
    var MUSIC_VOLUME = 0
    var SOUND_VOLUME = 50
    var currentLanguageTag: String = Locale.getDefault().toLanguageTag()
}
