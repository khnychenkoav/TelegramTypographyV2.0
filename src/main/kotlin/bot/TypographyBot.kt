package org.example.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.logging.LogLevel
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.dispatcher.document
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.extensions.filters.Filter
import org.example.processing.BroadcastService
import org.example.utils.BOT_TOKEN

class TypographyBot(
    private val responseHandler: ResponseHandler,
) {

    private val bot: Bot by lazy {
        bot {
            token = BOT_TOKEN
            logLevel = LogLevel.Network.Body

            dispatch {
                command("start") {
                    responseHandler.onStartCommand(this)
                }
                command("news_message") {
                    responseHandler.onNewsMessageCommand(this)
                }
                command("cancel") {
                    responseHandler.onCancelCommand(this)
                }
                callbackQuery {
                    responseHandler.onCallbackQuery(this)
                }
                message(Filter.All) {
                    if (message.text?.startsWith("/") != true) {
                        responseHandler.handleAnyMessage(this)
                    }
                }
            }
        }
    }


    fun start() {
        bot.startPolling()
    }
}