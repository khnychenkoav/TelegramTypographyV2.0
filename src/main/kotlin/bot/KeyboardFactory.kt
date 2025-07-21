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
        const val CALC_PAPER_TYPE_PREFIX = "calc_paper_type_"
        const val CALC_PRINT_SIDES_PREFIX = "calc_print_sides_"
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

    fun buildCalcPaperTypeMenu(): InlineKeyboardMarkup? {
        val paperTypes = prices.products.digitalPrinting.paperType?.keys ?: return null
        val buttons = paperTypes.map { paperTypeKey ->
            InlineKeyboardButton.CallbackData(
                text = paperTypeKey,
                callbackData = "$CALC_PAPER_TYPE_PREFIX$paperTypeKey"
            )
        }
        return InlineKeyboardMarkup.create(buttons.chunked(2))
    }

    fun buildCalcPrintSidesMenu(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "Односторонняя (4+0)",
                    callbackData = "${CALC_PRINT_SIDES_PREFIX}1"
                ),
                InlineKeyboardButton.CallbackData(
                    text = "Двухсторонняя (4+4)",
                    callbackData = "${CALC_PRINT_SIDES_PREFIX}2"
                )
            )
        )
    }
}