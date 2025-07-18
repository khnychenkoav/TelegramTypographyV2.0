package org.example.bot

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import org.example.utils.TextProvider

class KeyboardFactory(private val textProvider: TextProvider) {

    companion object {
        const val START_CHAT_CALLBACK = "start_chat"
        const val CALCULATE_ORDER_CALLBACK = "calculate_order"
        const val CONTACT_OPERATOR_CALLBACK = "contact_operator"
    }

    fun buildMainMenu(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.start_chat"),
                    callbackData = START_CHAT_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.calculate_order"),
                    callbackData = CALCULATE_ORDER_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.contact_operator"),
                    callbackData = CONTACT_OPERATOR_CALLBACK
                )
            )
        )
    }
}