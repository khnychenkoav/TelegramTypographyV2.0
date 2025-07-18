package org.example.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.logging.LogLevel
import org.example.utils.BOT_TOKEN

class TypographyBot(private val responseHandler: ResponseHandler) {

    private val bot: Bot

    init {
        responseHandler.sendMessage = { chatId, text, replyMarkup ->
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = text,
                replyMarkup = replyMarkup
            )
        }

        bot = bot {
            token = BOT_TOKEN
            logLevel = LogLevel.Network.Body

            dispatch {
                command("start") {
                    responseHandler.onStartCommand(this)
                }
                text {
                    if (message.text?.startsWith("/") != true) {
                        responseHandler.onTextMessage(this)
                    }
                }
                callbackQuery {
                    responseHandler.onCallbackQuery(this)
                }
            }
        }
    }

    fun start() {
        bot.startPolling()
    }
}