package io.github.some_example_name.old.core

// core/.../network/ApiClient.kt
import io.ktor.client.*
import io.ktor.client.engine.*          // будет разный на платформах
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.File

object ApiClient {
    // Для Android-эмулятора → хост-машина
    // Для реального устройства / Desktop → IP компьютера или 127.0.0.1
    const val BASE_URL = "http://127.0.0.1:8080"   // поменяй под себя

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // Клиент создаём один раз. Engine передаём с платформы.
    lateinit var client: HttpClient

    fun init(engine: HttpClientEngine) {
        client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(json)
                // protobuf() // если нужно
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }

    /**
     * Загружает файл на сервер через POST /upload
     */
    suspend fun uploadFile(file: File) {
        client.submitFormWithBinaryData(
            url = "$BASE_URL/upload",
            formData = formData {
                append(
                    key = "file",                          // имя поля, которое ждёт сервер
                    value = file.readBytes(),
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    }
                )
            }
        )
    }
}
