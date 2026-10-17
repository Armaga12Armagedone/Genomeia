package io.github.some_example_name.old.ui

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * libGDX читает переводы (`I18NBundle`) строго в UTF-8. Файл в другой кодировке (был
 * MyBundle_es.properties в ISO-8859-1) не падает, а тихо показывает «�» вместо букв —
 * поэтому проверяем байты каждого файла: UTF-8 без ошибок декодирования.
 */
class I18nBundleEncodingTest {

    /** Тесты :core запускаются из каталога core. */
    private val dir = File("../assets/ui/i18n")

    @Test
    fun `все файлы переводов записаны в UTF-8`() {
        val files = dir.listFiles { file -> file.name.startsWith("MyBundle") && file.extension == "properties" }.orEmpty()
        assertTrue(files.isNotEmpty(), "нет файлов переводов в ${dir.absolutePath}")

        for (file in files) {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val ok = runCatching { decoder.decode(ByteBuffer.wrap(file.readBytes())) }.isSuccess
            assertTrue(ok, "${file.name} не декодируется как UTF-8 — пересохраните файл в UTF-8")
        }
    }
}
