package org.example.services

import chat.giga.client.GigaChatClient
import chat.giga.client.auth.AuthClient
import chat.giga.client.auth.AuthClientBuilder
import chat.giga.langchain4j.GigaChatChatModel
import chat.giga.langchain4j.GigaChatChatRequestParameters
import chat.giga.langchain4j.GigaChatImageModel
import chat.giga.model.ModelName
import chat.giga.model.Scope
import chat.giga.model.completion.ChatFunctionCallEnum
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.example.utils.GIGACHAT_API_KEY
import org.slf4j.LoggerFactory
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private const val CERT_URL = "https://gu-st.ru/content/Other/doc/russian_trusted_root_ca.cer"

class GigaChatServiceImpl : LlmService {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val chatModel: GigaChatChatModel
    private val gigaChatClient: GigaChatClient

    init {
        logger.info("Инициализация GigaChatServiceImpl...")

        try {
            val sslContext = createSslContext()
            SSLContext.setDefault(sslContext)
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            logger.info("Глобальный SSLContext успешно настроен с российским сертификатом.")
        } catch (e: Exception) {
            logger.error("КРИТИЧЕСКАЯ ОШИБКА: Не удалось настроить SSL контекст для GigaChat.", e)
            throw IllegalStateException("Не удалось инициализировать SSL для GigaChat", e)
        }

        val authClient = AuthClient.builder()
            .withOAuth(
                AuthClientBuilder.OAuthBuilder.builder()
                    .scope(Scope.GIGACHAT_API_PERS)
                    .authKey(GIGACHAT_API_KEY)
                    .build()
            )
            .build()

        chatModel = GigaChatChatModel.builder()
            .authClient(authClient)
            .readTimeout(120)
            .defaultChatRequestParameters(
                GigaChatChatRequestParameters.builder()
                    .modelName(ModelName.GIGA_CHAT)
                    .maxOutputTokens(1024)
                    .build()
            )
            .verifySslCerts(true)
            .logRequests(true)
            .logResponses(true)
            .build()

        gigaChatClient = GigaChatClient.builder()
            .authClient(authClient)
            .readTimeout(720)
            .logRequests(true)
            .logResponses(true)
            .verifySslCerts(true)
            .build()

        logger.info("GigaChatServiceImpl успешно инициализирован.")
    }

    /** Загружает .cer по URL, собирает KeyStore → TrustManager → SSLContext */
    @Throws(Exception::class)
    private fun createSslContext(): SSLContext {
        val certStream = URL(CERT_URL).openStream()
        val cf = CertificateFactory.getInstance("X.509")
        val caCert = certStream.use { cf.generateCertificate(it) as X509Certificate }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("russianRootCa", caCert)
        }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        val trustManager = tmf.trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), null)
        }
    }

    override suspend fun generateWithHistory(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        newUserPrompt: String
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val messages = mutableListOf<ChatMessage>()
                messages.add(SystemMessage.from(systemPrompt))
                history.forEach { (userMsg, assistantMsg) ->
                    messages.add(UserMessage.from(userMsg))
                    messages.add(AiMessage.from(assistantMsg))
                }
                messages.add(UserMessage.from(newUserPrompt))

                val gigaParams = GigaChatChatRequestParameters.builder()
                    .modelName(chatModel.defaultRequestParameters().modelName())
                    .maxOutputTokens(1024)
                    .build()

                val chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .parameters(gigaParams)
                    .build()

                val response = chatModel.doChat(chatRequest)
                response.aiMessage().text()

            } catch (e: Exception) {
                logger.error("Ошибка при работе с GigaChat API", e)
                "Произошла ошибка при обращении к AI-модели GigaChat. Пожалуйста, попробуйте позже."
            }
        }
    }

    override suspend fun generateImage(prompt: String): ByteArray? {
        return try {
            withContext(Dispatchers.IO) {
                val userMessage = UserMessage.from(
                    "Нарисуй: $prompt. Обязательно верни результат в теге <img src=\\\"file_id\\\">"
                )

                val imageGenParams = GigaChatChatRequestParameters.builder()
                    .modelName(chatModel.defaultRequestParameters().modelName())
                    .functionCall(ChatFunctionCallEnum.AUTO)
                    .maxOutputTokens(chatModel.defaultRequestParameters().maxOutputTokens())
                    .build()

                val chatRequest = ChatRequest.builder()
                    .messages(listOf(userMessage))
                    .parameters(imageGenParams)
                    .build()

                val response = chatModel.doChat(chatRequest)
                val content = response.aiMessage().text()

                if (content.contains("<img src=\"")) {
                    val fileId = content.substringAfter("<img src=\"").substringBefore("\"")

                    if (fileId.isBlank()) {
                        throw IllegalStateException("Не удалось извлечь fileId из ответа: $content")
                    }

                    val imageBytes = gigaChatClient.downloadFile(fileId, null)

                    logger.info("Изображение успешно сгенерировано. File ID: {}", fileId)

                    imageBytes
                } else {
                    logger.warn("GigaChat не вернул тег <img> в ответ на промпт: {}. Ответ модели: {}", prompt, content)
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Ошибка при генерации/скачивании изображения через GigaChat API", e)
            null
        }
    }
}