package org.example.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.example.services.dto.TranslationResponse

class TranslationService {
    private val apiUrl = "http://localhost:5000/translate"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    /**
     * Переводит текст с одного языка на другой.
     * @param text Текст для перевода.
     * @param source Исходный язык (например, "ru").
     * @param target Целевой язык (например, "en").
     * @return Переведенный текст или null в случае ошибки.
     */
    suspend fun translate(text: String, source: String, target: String): String? {
        return try {
            val response: TranslationResponse = client.submitForm(
                url = apiUrl,
                formParameters = parameters {
                    append("q", text)
                    append("source", source)
                    append("target", target)
                    append("format", "text")
                }
            ).body()
            response.translatedText
        } catch (e: Exception) {
            println("Ошибка перевода: ${e.message}")
            null
        }
    }
}