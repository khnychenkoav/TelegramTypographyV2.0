package org.example.bot

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import org.example.calculation.PriceListProvider
import org.example.utils.TextProvider

class KeyboardFactory(
    private val textProvider: TextProvider,
    private val prices: PriceListProvider
) {

    companion object {
        const val START_CHAT_CALLBACK = "start_chat"
        const val CALCULATE_ORDER_CALLBACK = "calculate_order"
        const val CONTACT_OPERATOR_CALLBACK = "contact_operator"

        const val CALC_PT_BADGE_CALLBACK = "calc_pt_badge"
        const val CALC_PT_DIGITAL_PRINTING_CALLBACK = "calc_pt_digital_printing"
        const val CALC_PT_CUTTING_CALLBACK = "calc_pt_cutting"
        const val CALC_PT_CUTTING_AND_PRINTING_CALLBACK = "calc_pt_cutting_and_printing"

        const val CALC_BADGE_TYPE_PREFIX = "calc_badge_type_"
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

    fun buildCalcProductTypeMenu(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.calc.product_badge"),
                    callbackData = CALC_PT_BADGE_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.calc.product_digital_printing"),
                    callbackData = CALC_PT_DIGITAL_PRINTING_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.calc.product_cutting"),
                    callbackData = CALC_PT_CUTTING_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.calc.product_cutting_and_printing"),
                    callbackData = CALC_PT_CUTTING_AND_PRINTING_CALLBACK
                )
            )
        )
    }

    fun buildCalcBadgeTypeMenu(): InlineKeyboardMarkup? {
        val badgeTypes = prices.products.badges.types?.keys ?: return null

        val buttons = badgeTypes.map { badgeTypeKey ->
            InlineKeyboardButton.CallbackData(
                text = badgeTypeKey,
                callbackData = "$CALC_BADGE_TYPE_PREFIX$badgeTypeKey"
            )
        }

        return InlineKeyboardMarkup.create(buttons.chunked(2))
    }
}