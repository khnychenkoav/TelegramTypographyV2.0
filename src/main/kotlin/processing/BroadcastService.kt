package org.example.processing

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.network.fold
import kotlinx.coroutines.*
import org.example.state.UserRepository
import org.example.utils.OPERATOR_CHAT_ID
import org.slf4j.LoggerFactory

class BroadcastService(
    private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startBroadcast(messageToBroadcast: Message, bot: Bot) {
        val operatorChatId = messageToBroadcast.chat.id
        val userIds = userRepository.getAllUserIds().filter { it != operatorChatId }

        if (userIds.isEmpty()) {
            logger.warn("Попытка рассылки, но нет зарегистрированных пользователей.")
            bot.sendMessage(ChatId.fromId(operatorChatId), "В базе нет пользователей для рассылки.")
            return
        }

        logger.info("Начинаю рассылку сообщения {} для {} пользователей.", messageToBroadcast.messageId, userIds.size)
        bot.sendMessage(ChatId.fromId(operatorChatId), "✅ Сообщение принято. Начинаю рассылку для ${userIds.size} пользователей...")

        scope.launch {
            var successful = 0
            var failed = 0

            userIds.forEach { userId ->
                try {
                    bot.copyMessage(
                        chatId = ChatId.fromId(userId),
                        fromChatId = ChatId.fromId(messageToBroadcast.chat.id),
                        messageId = messageToBroadcast.messageId
                    ).fold(
                        { successful++ },
                        {
                            failed++
                            logger.warn("Не удалось доставить сообщение пользователю {}: {}", userId, it.errorBody)
                        }
                    )
                    delay(50)
                } catch (e: Exception) {
                    failed++
                    logger.error("Критическая ошибка при отправке сообщения пользователю {}", userId, e)
                }
            }
            val report = "🏁 Рассылка завершена!\n\n" +
                    "✅ Успешно отправлено: $successful\n" +
                    "❌ Не удалось доставить: $failed (вероятно, пользователь заблокировал бота)"

            logger.info(report)
            bot.sendMessage(ChatId.fromId(operatorChatId), report)
        }
    }
}