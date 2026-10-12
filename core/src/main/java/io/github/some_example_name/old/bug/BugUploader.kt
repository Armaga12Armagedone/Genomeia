package io.github.some_example_name.old.bug

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Net
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.net.HttpRequestBuilder
import io.github.some_example_name.old.core.GameVersion

/**
 * Отправка .bug на сервер — ЗАГЛУШКА.
 *
 * Запрос настоящий (POST тела файла через Gdx.net, тот работает на всех платформах), но
 * сервера пока нет: адрес в домене .invalid, который по стандарту не существует нигде,
 * так что запрос гарантированно закончится ошибкой и никуда не уйдёт. Когда сервер
 * появится — поменять [UPLOAD_URL] и разобрать ответ.
 */
object BugUploader {

    const val UPLOAD_URL = "https://bugs.genomeia.invalid/upload"

    private const val TIMEOUT_MS = 15_000

    /** [onResult] приходит в поток рендера: можно сразу трогать интерфейс. */
    fun upload(bug: FileHandle, onResult: (success: Boolean, message: String) -> Unit) {
        val request = HttpRequestBuilder()
            .newRequest()
            .method(Net.HttpMethods.POST)
            .url(UPLOAD_URL)
            .header("Content-Type", "application/octet-stream")
            .header("X-Genomeia-File", bug.name())
            .header("X-Genomeia-Version", GameVersion.NAME)
            .timeout(TIMEOUT_MS)
            .build()
        request.setContent(bug.read(), bug.length())

        Gdx.net.sendHttpRequest(request, object : Net.HttpResponseListener {
            override fun handleHttpResponse(response: Net.HttpResponse) {
                val status = response.status.statusCode
                val ok = status in 200..299
                Gdx.app.postRunnable { onResult(ok, if (ok) "OK" else "HTTP $status") }
            }

            override fun failed(t: Throwable) {
                Gdx.app.postRunnable { onResult(false, t.message ?: t::class.java.simpleName) }
            }

            override fun cancelled() {
                Gdx.app.postRunnable { onResult(false, "cancelled") }
            }
        })
    }
}
