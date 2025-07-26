package org.example.services

import chat.giga.client.auth.AuthClient
import chat.giga.client.auth.AuthClientBuilder
import chat.giga.langchain4j.GigaChatChatModel
import chat.giga.langchain4j.GigaChatChatRequestParameters
import chat.giga.model.Scope
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import org.example.utils.GIGACHAT_API_KEY
import org.slf4j.LoggerFactory
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private const val CERT_URL = "https://gu-st.ru/content/Other/doc/russian_trusted_root_ca.cer"

class GigaChatServiceImpl : LlmService {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val model: GigaChatChatModel

    init {
        logger.info("Инициализация GigaChatServiceImpl...")

        // 1. Настраиваем глобальный SSLContext. Это единственный путь из-за ограничений библиотеки.
        try {
            val sslContext = createSslContext()
            SSLContext.setDefault(sslContext)
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            logger.info("Глобальный SSLContext успешно настроен с российским сертификатом.")
        } catch (e: Exception) {
            logger.error("КРИТИЧЕСКАЯ ОШИБКА: Не удалось настроить SSL контекст для GigaChat.", e)
            throw IllegalStateException("Не удалось инициализировать SSL для GigaChat", e)
        }

        // 2. Строим AuthClient. Он подхватит глобальный SSLContext.
        val authClient = AuthClient.builder()
            .withOAuth(
                AuthClientBuilder.OAuthBuilder.builder()
                    .scope(Scope.GIGACHAT_API_PERS)
                    .authKey(GIGACHAT_API_KEY)
                    .build()
            )
            .build()

        // 3. Строим саму модель.
        // verifySslCerts(true) говорит клиенту использовать настроенный TrustManager (наш глобальный).
        model = GigaChatChatModel.builder()
            .authClient(authClient)
            .verifySslCerts(true)
            .logRequests(true)
            .logResponses(true)
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

    override fun generateWithHistory(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        newUserPrompt: String
    ): String {
        try {
            val messages = mutableListOf<ChatMessage>()
            messages.add(SystemMessage.from(systemPrompt))
            history.forEach { (userMsg, assistantMsg) ->
                messages.add(UserMessage.from(userMsg))
                messages.add(AiMessage.from(assistantMsg))
            }
            messages.add(UserMessage.from(newUserPrompt))

            // Создаем параметры запроса, чтобы избежать NullPointerException
            val gigaParams = GigaChatChatRequestParameters.builder()
                .modelName(model.defaultRequestParameters().modelName())
                .maxOutputTokens(1024)
                .build()

            val chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(gigaParams)
                .build()

            val response = model.doChat(chatRequest)
            return response.aiMessage().text()

        } catch (e: Exception) {
            logger.error("Ошибка при работе с GigaChat API", e)
            // Возвращаем осмысленное сообщение, можно добавить детали из `e`
            return "Произошла ошибка при обращении к AI-модели: ${e.message}"
        }
    }
}