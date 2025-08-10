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
        const val CALC_MATERIAL_CATEGORY_PREFIX = "calc_mat_cat_"
        const val CALC_MATERIAL_PREFIX = "calc_material_"
        const val CALC_PRINT_LAYERS_PREFIX = "calc_print_layers_"
        const val CALC_PT_AI_ESTIMATION_CALLBACK = "calc_pt_ai_estimation"

        const val BACK_TO_MAIN_MENU_CALLBACK = "back_to_main_menu"
        const val BACK_CALLBACK_PREFIX = "back_to_"
        const val DEST_CALC_START = "calc_start"
        const val DEST_CHOOSE_PAPER = "choose_paper"
        const val DEST_CHOOSE_SIDES = "choose_sides"
        const val DEST_CHOOSE_MATERIAL_CAT = "choose_material_cat"
        const val DEST_CHOOSE_MATERIAL = "choose_material"

        const val INFO_CALLBACK = "info"
        const val INFO_ADDRESSES_CALLBACK = "info_addresses"
        const val INFO_FILE_REQ_CALLBACK = "info_file_req"

        const val SUBMIT_ORDER_CALLBACK = "submit_order"

        const val ESCALATE_TO_LLM_CALLBACK = "escalate_to_llm"

        const val GENERATE_IMAGE_CALLBACK = "generate_image"

        const val IMAGE_GEN_RETRY_CALLBACK = "image_gen_retry"
        const val IMAGE_GEN_CREATE_ORDER_CALLBACK = "image_gen_create_order"
        const val IMAGE_GEN_SUBMIT_TO_OPERATOR_CALLBACK = "image_gen_submit"
        const val IMAGE_GEN_EDIT_ORDER_CALLBACK = "image_gen_edit"

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
                    text = textProvider.get("button.generate_idea"),
                    callbackData = GENERATE_IMAGE_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.contact_operator"),
                    callbackData = CONTACT_OPERATOR_CALLBACK
                ),
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.info"),
                    callbackData = INFO_CALLBACK
                )
            ),
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
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.calc.ai_estimation"),
                    callbackData = CALC_PT_AI_ESTIMATION_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.back_to_main_menu"),
                    callbackData = BACK_TO_MAIN_MENU_CALLBACK
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

        return InlineKeyboardMarkup.create(buttons.chunked(2) + listOf(listOf(backButton(DEST_CALC_START))))
    }

    fun buildCalcPaperTypeMenu(): InlineKeyboardMarkup? {
        val paperTypes = prices.products.digitalPrinting.paperType?.keys ?: return null
        val buttons = paperTypes.map { paperTypeKey ->
            InlineKeyboardButton.CallbackData(
                text = paperTypeKey,
                callbackData = "$CALC_PAPER_TYPE_PREFIX$paperTypeKey"
            )
        }
        return InlineKeyboardMarkup.create(buttons.chunked(2) + listOf(listOf(backButton(DEST_CALC_START))))
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
            ),
            listOf(backButton(DEST_CHOOSE_PAPER))
        )
    }

    fun buildCalcMaterialCategoryMenu(): InlineKeyboardMarkup {
        val categoryNames = mapOf(
            "wood" to "Дерево, МДФ",
            "plastic" to "Пластик, Акрил",
            "composite" to "Композит",
            "film" to "Пленка",
            "magnetic" to "Магнитный винил",
            "foam" to "Вспененные материалы",
            "adhesive" to "Клеевые материалы"
        )

        val categories = listOf("wood", "plastic", "composite", "film", "magnetic", "foam", "adhesive")

        val buttons = categories.map { categoryKey ->
            InlineKeyboardButton.CallbackData(
                text = categoryNames[categoryKey] ?: categoryKey,
                callbackData = "$CALC_MATERIAL_CATEGORY_PREFIX$categoryKey"
            )
        }
        return InlineKeyboardMarkup.create(buttons.chunked(2) + listOf(listOf(backButton(DEST_CALC_START))))
    }

    fun buildCalcMaterialMenu(category: String): InlineKeyboardMarkup? {
        val materialsMap = when (category) {
            "wood" -> prices.materials.wood
            "plastic" -> prices.materials.plastic
            "composite" -> prices.materials.composite
            "film" -> prices.materials.film
            "magnetic" -> prices.materials.magnetic
            "foam" -> prices.materials.foam
            "adhesive" -> prices.materials.adhesive
            else -> return null
        }

        if (materialsMap.isEmpty()) {
            return null
        }

        val buttons = materialsMap.keys.map { materialKey ->
            InlineKeyboardButton.CallbackData(
                text = textProvider.get("material.$materialKey"),
                callbackData = "$CALC_MATERIAL_PREFIX$materialKey"
            )
        }
        return InlineKeyboardMarkup.create(buttons.chunked(2) + listOf(listOf(backButton(DEST_CHOOSE_MATERIAL_CAT))))
    }

    fun buildCalcPrintLayersMenu(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.calc.layers_1"),
                    callbackData = "${CALC_PRINT_LAYERS_PREFIX}1"
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.calc.layers_2"),
                    callbackData = "${CALC_PRINT_LAYERS_PREFIX}2"
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.calc.layers_3"),
                    callbackData = "${CALC_PRINT_LAYERS_PREFIX}3"
                )
            ),
            listOf(backButton(DEST_CHOOSE_MATERIAL_CAT))
        )
    }

    fun buildBackToMainMenuKeyboard(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.back_to_main_menu"),
                    callbackData = BACK_TO_MAIN_MENU_CALLBACK
                )
            )
        )
    }

    fun buildInfoMenu(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.info.addresses"),
                    callbackData = INFO_ADDRESSES_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.info.file_requirements"),
                    callbackData = INFO_FILE_REQ_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.back_to_main_menu"),
                    callbackData = BACK_TO_MAIN_MENU_CALLBACK
                )
            )
        )
    }

    fun buildPostCalculationMenu(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "✅ Оформить заявку",
                    callbackData = SUBMIT_ORDER_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.back_to_main_menu"),
                    callbackData = BACK_TO_MAIN_MENU_CALLBACK
                )
            )
        )
    }

    private fun backButton(destination: String): InlineKeyboardButton.CallbackData {
        return InlineKeyboardButton.CallbackData(
            text = textProvider.get("button.back"),
            callbackData = "$BACK_CALLBACK_PREFIX$destination"
        )
    }

    fun buildAfterCannedResponseMenu(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "❓ Уточнить у AI-ассистента",
                    callbackData = ESCALATE_TO_LLM_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.back_to_main_menu"),
                    callbackData = BACK_TO_MAIN_MENU_CALLBACK
                )
            )
        )
    }

    fun buildAfterImageGenMenu(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "✅ Отлично, оформляем!",
                    callbackData = IMAGE_GEN_CREATE_ORDER_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "🔄 Попробовать еще раз",
                    callbackData = IMAGE_GEN_RETRY_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.back_to_main_menu"),
                    callbackData = BACK_TO_MAIN_MENU_CALLBACK
                )
            )
        )
    }

    fun buildCreativeOrderConfirmationMenu(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "✅ Да, отправить заявку оператору",
                    callbackData = IMAGE_GEN_SUBMIT_TO_OPERATOR_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "✏️ Изменить параметры",
                    callbackData = IMAGE_GEN_EDIT_ORDER_CALLBACK
                )
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = textProvider.get("button.back_to_main_menu"),
                    callbackData = BACK_TO_MAIN_MENU_CALLBACK
                )
            )
        )
    }

}