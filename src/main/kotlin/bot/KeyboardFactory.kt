package org.example.bot

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton

object KeyboardFactory {

    const val START_CHAT_CALLBACK = "start_chat"
    const val CALCULATE_ORDER_CALLBACK = "calculate_order"
    const val CONTACT_OPERATOR_CALLBACK = "contact_operator"

    fun buildMainMenu(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "💬 Начать диалог",
                    callbackData = START_CHAT_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "💰 Рассчитать заказ",
                    callbackData = CALCULATE_ORDER_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "👨‍💼 Связаться с оператором",
                    callbackData = CONTACT_OPERATOR_CALLBACK
                )
            )
        )
    }
}