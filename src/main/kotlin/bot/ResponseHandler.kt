package org.example.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.TextHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.network.fold
import org.example.calculation.CalculatorService
import org.example.calculation.PriceListProvider
import org.example.calculation.models.CalculationResult
import org.example.processing.JobQueue
import org.example.processing.LlmJob
import org.example.state.CalculationData
import org.example.state.SessionManager
import org.example.state.UserMode
import org.example.utils.TextProvider
import org.slf4j.LoggerFactory
import java.text.DecimalFormat

class ResponseHandler(
    private val sessionManager: SessionManager,
    private val textProvider: TextProvider,
    private val jobQueue: JobQueue,
    private val calculatorService: CalculatorService,
    private val priceListProvider: PriceListProvider
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val keyboardFactory = KeyboardFactory(textProvider, priceListProvider)

    private fun sendOrEditMessage(
        bot: Bot,
        chatId: Long,
        text: String,
        replyMarkup: InlineKeyboardMarkup?,
        editPrevious: Boolean = true
    ) {
        val session = sessionManager.getSession(chatId)
        val messageIdToEdit = if (editPrevious) session.lastBotMessageId else null

        var finalMessageId: Long? = null

        if (messageIdToEdit != null) {
            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = messageIdToEdit,
                text = text,
                replyMarkup = replyMarkup,
                parseMode = com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN
            ).fold(
                {
                    finalMessageId = messageIdToEdit
                },
                { error ->
                    logger.warn("Не удалось отредактировать сообщение $messageIdToEdit: $error. Отправляю новое.")
                    bot.sendMessage(
                        chatId = ChatId.fromId(chatId),
                        text = text,
                        replyMarkup = replyMarkup,
                        parseMode = com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN
                    ).fold(
                        { newMessage -> finalMessageId = newMessage.messageId },
                        { sendError -> logger.error("Не удалось отправить запасное сообщение: $sendError") }
                    )
                }
            )
        } else {
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = text,
                replyMarkup = replyMarkup,
                parseMode = com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN
            ).fold(
                { newMessage -> finalMessageId = newMessage.messageId },
                { error -> logger.error("Не удалось отправить сообщение: $error") }
            )
        }

        finalMessageId?.let {
            session.lastBotMessageId = it
            sessionManager.updateSession(chatId, session)
        }
    }

    private fun escapeMarkdownV1(text: String): String {
        return text
            .replace("_", "\\_")
            .replace("*", "\\*")
            .replace("`", "\\`")
            .replace("[", "\\[")
    }

    fun onStartCommand(env: CommandHandlerEnvironment) {
        val chatId = env.message.chat.id
        sessionManager.resetSession(chatId)
        val session = sessionManager.getSession(chatId)
        sessionManager.updateSession(chatId, session.copy(mode = UserMode.AWAITING_NAME))
        env.bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = textProvider.get("start.welcome")
        )
    }

    fun onTextMessage(env: TextHandlerEnvironment) {
        val chatId = env.message.chat.id
        val text = env.message.text.orEmpty()
        val session = sessionManager.getSession(chatId)

        when (session.mode) {
            UserMode.AWAITING_NAME -> handleNameInput(env, text)
            UserMode.MAIN_MENU -> showMainMenu(env.bot, chatId)
            UserMode.LLM_CHAT -> handleLlmChat(env, text)
            UserMode.AWAITING_OPERATOR_QUERY -> handleOperatorQuery(env, text)
            UserMode.CALC_AWAITING_QUANTITY -> handleQuantitySelected(env.bot, chatId, text)
            UserMode.CALC_AWAITING_DIMENSIONS -> handleDimensionsSelected(env.bot, chatId, text)
            else -> {
                logger.warn("Получено текстовое сообщение в необрабатываемом режиме: {}", session.mode)
                sendOrEditMessage(env.bot, chatId, "Пожалуйста, используйте кнопки для выбора.", null, editPrevious = false)
            }
        }
    }

    fun onCallbackQuery(env: CallbackQueryHandlerEnvironment) {
        val chatId = env.callbackQuery.message?.chat?.id ?: return
        val callbackData = env.callbackQuery.data
        env.bot.answerCallbackQuery(env.callbackQuery.id)

        when {
            callbackData == KeyboardFactory.START_CHAT_CALLBACK -> startLlmChat(env.bot, chatId)
            callbackData == KeyboardFactory.CONTACT_OPERATOR_CALLBACK -> startOperatorQuery(env.bot, chatId)
            callbackData == KeyboardFactory.CALCULATE_ORDER_CALLBACK -> startCalculation(env.bot, chatId)

            callbackData == KeyboardFactory.BACK_TO_MAIN_MENU_CALLBACK -> {
                val session = sessionManager.getSession(chatId)
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.MAIN_MENU))
                showMainMenu(env.bot, chatId)
            }

            callbackData.startsWith(KeyboardFactory.BACK_CALLBACK_PREFIX) -> {
                val destination = callbackData.removePrefix(KeyboardFactory.BACK_CALLBACK_PREFIX)
                handleBackNavigation(env.bot, chatId, destination)
            }

            callbackData == KeyboardFactory.CALC_PT_BADGE_CALLBACK -> handleProductTypeSelected(env.bot, chatId, "badge")
            callbackData == KeyboardFactory.CALC_PT_DIGITAL_PRINTING_CALLBACK -> handleProductTypeSelected(env.bot, chatId, "digital_printing")
            callbackData == KeyboardFactory.CALC_PT_CUTTING_CALLBACK -> handleProductTypeSelected(env.bot, chatId, "cutting")
            callbackData == KeyboardFactory.CALC_PT_CUTTING_AND_PRINTING_CALLBACK -> handleProductTypeSelected(env.bot, chatId, "cutting_and_printing")

            callbackData.startsWith(KeyboardFactory.CALC_BADGE_TYPE_PREFIX) -> {
                val badgeType = callbackData.removePrefix(KeyboardFactory.CALC_BADGE_TYPE_PREFIX)
                handleBadgeTypeSelected(env.bot, chatId, badgeType)
            }
            callbackData.startsWith(KeyboardFactory.CALC_PAPER_TYPE_PREFIX) -> {
                val paperType = callbackData.removePrefix(KeyboardFactory.CALC_PAPER_TYPE_PREFIX)
                handlePaperTypeSelected(env.bot, chatId, paperType)
            }
            callbackData.startsWith(KeyboardFactory.CALC_PRINT_SIDES_PREFIX) -> {
                val sides = callbackData.removePrefix(KeyboardFactory.CALC_PRINT_SIDES_PREFIX).toIntOrNull()
                if (sides != null) {
                    handlePrintSidesSelected(env.bot, chatId, sides)
                }
            }
            callbackData.startsWith(KeyboardFactory.CALC_MATERIAL_CATEGORY_PREFIX) -> {
                val category = callbackData.removePrefix(KeyboardFactory.CALC_MATERIAL_CATEGORY_PREFIX)
                handleMaterialCategorySelected(env.bot, chatId, category)
            }
            callbackData.startsWith(KeyboardFactory.CALC_MATERIAL_PREFIX) -> {
                val materialKey = callbackData.removePrefix(KeyboardFactory.CALC_MATERIAL_PREFIX)
                handleMaterialSelected(env.bot, chatId, materialKey)
            }
            callbackData.startsWith(KeyboardFactory.CALC_PRINT_LAYERS_PREFIX) -> {
                val layers = callbackData.removePrefix(KeyboardFactory.CALC_PRINT_LAYERS_PREFIX).toIntOrNull()
                if (layers != null) {
                    handlePrintLayersSelected(env.bot, chatId, layers)
                }
            }
        }
    }


    private fun handleNameInput(env: TextHandlerEnvironment, name: String) {
        val chatId = env.message.chat.id
        val session = sessionManager.getSession(chatId)
        sessionManager.updateSession(chatId, session.copy(name = name, mode = UserMode.MAIN_MENU))

        sendOrEditMessage(env.bot, chatId, textProvider.get("greeting.personal", name), keyboardFactory.buildMainMenu(), editPrevious = false)
    }

    private fun showMainMenu(bot: Bot, chatId: Long, editPrevious: Boolean = true) {
        sendOrEditMessage(bot, chatId, textProvider.get("menu.prompt"), keyboardFactory.buildMainMenu(), editPrevious)
    }

    private fun startLlmChat(bot: Bot, chatId: Long) {
        val session = sessionManager.getSession(chatId)
        sessionManager.updateSession(chatId, session.copy(mode = UserMode.LLM_CHAT))
        sendOrEditMessage(
            bot = bot,
            chatId = chatId,
            text = textProvider.get("callback.chat.prompt"),
            replyMarkup = keyboardFactory.buildBackToMainMenuKeyboard()
        )
    }

    private fun startOperatorQuery(bot: Bot, chatId: Long) {
        val session = sessionManager.getSession(chatId)
        sessionManager.updateSession(chatId, session.copy(mode = UserMode.AWAITING_OPERATOR_QUERY))
        sendOrEditMessage(
            bot = bot,
            chatId = chatId,
            text = textProvider.get("callback.operator.prompt"),
            replyMarkup = keyboardFactory.buildBackToMainMenuKeyboard()
        )
    }

    private fun handleOperatorQuery(env: TextHandlerEnvironment, text: String) {
        val chatId = env.message.chat.id
        val user = env.message.from ?: return
        val userName = user.firstName + (user.lastName?.let { " $it" } ?: "")
        val userMention = "[${userName}](tg://user?id=${user.id})"

        val messageForOperator = """
        ❗️*Новый вопрос оператору*❗️
        
        *От:* $userMention
        *ID чата:* `${user.id}`
        
        *Сообщение:*
        ${escapeMarkdownV1(text)}
    """.trimIndent()

        if (org.example.utils.OPERATOR_CHAT_ID != 0L) {
            env.bot.sendMessage(
                chatId = ChatId.fromId(org.example.utils.OPERATOR_CHAT_ID),
                text = messageForOperator,
                parseMode = com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN
            )
            sendOrEditMessage(env.bot, chatId, textProvider.get("operator.query.received"), null, editPrevious = false)
        } else {
            logger.warn("OPERATOR_CHAT_ID не настроен. Сообщение не отправлено.")
            sendOrEditMessage(env.bot, chatId, "К сожалению, связь с оператором временно недоступна. Пожалуйста, попробуйте позже.", null, editPrevious = false)
        }

        val session = sessionManager.getSession(chatId)
        sessionManager.updateSession(chatId, session.copy(mode = UserMode.MAIN_MENU))
        showMainMenu(env.bot, chatId)
    }

    private fun startCalculation(bot: Bot, chatId: Long) {
        val session = sessionManager.getSession(chatId)

        sessionManager.updateSession(chatId, session.copy(
            mode = UserMode.CALC_AWAITING_PRODUCT_TYPE,
            currentCalculation = CalculationData()
        ))

        sendOrEditMessage(bot, chatId, textProvider.get("calc.prompt.choose_product"), keyboardFactory.buildCalcProductTypeMenu(), editPrevious = false)
    }

    private fun handleLlmChat(env: TextHandlerEnvironment, text: String) {
        val chatId = env.message.chat.id
        val session = sessionManager.getSession(chatId)
        val job = LlmJob(
            chatId = chatId,
            systemPrompt = textProvider.get("llm.system_prompt.chat"),
            history = session.conversationHistory.toList(),
            newUserPrompt = text,
            onResult = { result ->
                val updatedHistory = session.conversationHistory
                updatedHistory.add(text to result)
                while (updatedHistory.size > 5) {
                    updatedHistory.removeFirst()
                }
                sessionManager.updateSession(chatId, session.copy(conversationHistory = updatedHistory))
                sendOrEditMessage(env.bot, chatId, result, null, editPrevious = false)
            }
        )
        if (jobQueue.submit(job)) {
            sendOrEditMessage(env.bot, chatId, textProvider.get("llm.in_queue"), null, editPrevious = false)
        } else {
            sendOrEditMessage(env.bot, chatId, textProvider.get("llm.error"), null, editPrevious = false)
        }
    }

    private fun handleProductTypeSelected(bot: Bot, chatId: Long, productType: String) {
        val session = sessionManager.getSession(chatId)
        val calculationData = session.currentCalculation ?: run {
            logger.error("currentCalculation is null for chat $chatId, aborting.")
            return
        }

        calculationData.productType = productType

        when (productType) {
            "badge" -> {
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_BADGE_TYPE))
                sendOrEditMessage(bot, chatId, textProvider.get("calc.prompt.choose_badge_type"), keyboardFactory.buildCalcBadgeTypeMenu())
            }
            "digital_printing" -> {
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_PAPER_TYPE))
                sendOrEditMessage(bot, chatId, textProvider.get("calc.prompt.choose_paper_type"), keyboardFactory.buildCalcPaperTypeMenu())
            }
            "cutting", "cutting_and_printing" -> {
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_MATERIAL_CATEGORY))
                sendOrEditMessage(bot, chatId, textProvider.get("calc.prompt.choose_material_category"), keyboardFactory.buildCalcMaterialCategoryMenu())
            }
            else -> {
                logger.error("Неизвестный тип продукта: $productType")
                sendOrEditMessage(bot, chatId, "Произошла ошибка, неизвестный тип продукта.", null, editPrevious = false)
            }
        }
    }

    private fun handleBadgeTypeSelected(bot: Bot, chatId: Long, badgeType: String) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return

        val parts = badgeType.split('_')
        if (parts.size == 2) {
            calcData.shape = parts[0]
            calcData.size = parts[1]
        } else {
            logger.error("Некорректный формат badgeType: $badgeType")
            sendOrEditMessage(bot, chatId, "Ошибка: неверный формат типа значка. Начните заново.", null, editPrevious = false)
            startCalculation(bot, chatId)
            return
        }

        sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_QUANTITY))
        sendOrEditMessage(bot, chatId, textProvider.get("calc.prompt.enter_quantity"), null)
    }

    private fun handlePaperTypeSelected(bot: Bot, chatId: Long, paperType: String) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return

        calcData.material = paperType

        sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_PRINT_SIDES))
        sendOrEditMessage(bot, chatId, textProvider.get("calc.prompt.choose_print_sides"), keyboardFactory.buildCalcPrintSidesMenu())
    }

    private fun handlePrintSidesSelected(bot: Bot, chatId: Long, sides: Int) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return

        calcData.printingSides = sides

        sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_QUANTITY))
        sendOrEditMessage(bot, chatId, textProvider.get("calc.prompt.enter_quantity"), null)
    }

    private fun handleQuantitySelected(bot: Bot, chatId: Long, text: String) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return

        val quantity = text.toIntOrNull()
        if (quantity == null || quantity <= 0) {
            sendOrEditMessage(bot, chatId, textProvider.get("calc.error.invalid_number"), null, editPrevious = false)
            return
        }

        calcData.quantity = quantity

        finishCalculation(bot, chatId)
    }

    private fun handleMaterialCategorySelected(bot: Bot, chatId: Long, category: String) {
        val session = sessionManager.getSession(chatId)
        sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_MATERIAL_AND_THICKNESS))

        val keyboard = keyboardFactory.buildCalcMaterialMenu(category)
        if (keyboard == null) {
            logger.error("Не удалось создать клавиатуру для категории материалов '$category' или в ней нет материалов.")
            sendOrEditMessage(bot, chatId, "В категории '$category' не найдено материалов для расчета. Пожалуйста, выберите другую категорию.", keyboardFactory.buildCalcMaterialCategoryMenu())
            return
        }
        sendOrEditMessage(bot, chatId, textProvider.get("calc.prompt.choose_material"), keyboard)

    }

    private fun handleMaterialSelected(bot: Bot, chatId: Long, materialKey: String) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return
        calcData.originalMaterialKey = materialKey

        val regex = """^([a-z_]+)_(\d+(\.\d+)?)mm$""".toRegex()
        val matchResult = regex.find(materialKey)

        if (matchResult != null) {
            val (materialName, thicknessString) = matchResult.destructured
            calcData.material = materialName
            calcData.thicknessMm = thicknessString.toDoubleOrNull()
        } else {
            calcData.material = materialKey
            calcData.thicknessMm = null
        }

        sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_DIMENSIONS))
        val materialDisplayName = textProvider.get("material.$materialKey")
        sendOrEditMessage(bot, chatId, textProvider.get("calc.prompt.enter_dimensions", materialDisplayName), null)
    }

    private fun handleDimensionsSelected(bot: Bot, chatId: Long, text: String) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return

        val parts = text.trim().split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }

        when (parts.size) {
            2 -> {
                calcData.widthCm = parts[0]
                calcData.heightCm = parts[1]
            }
            1 -> {
                calcData.diameterCm = parts[0]
            }
            else -> {
                sendOrEditMessage(bot, chatId, textProvider.get("calc.error.invalid_dimensions"), null, editPrevious = false)
                return
            }
        }

        if (calcData.productType == "cutting_and_printing") {
            sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_PRINT_LAYERS))
            sendOrEditMessage(bot, chatId, textProvider.get("calc.prompt.choose_print_layers"), keyboardFactory.buildCalcPrintLayersMenu(), editPrevious = false)
        } else {
            sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_QUANTITY))
            sendOrEditMessage(bot, chatId, textProvider.get("calc.prompt.enter_quantity"), null, editPrevious = false)
        }
    }

    private fun handlePrintLayersSelected(bot: Bot, chatId: Long, layers: Int) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return
        calcData.printingLayers = layers

        sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_QUANTITY))
        sendOrEditMessage(bot, chatId, textProvider.get("calc.prompt.enter_quantity"), null)
    }


    private fun finishCalculation(bot: Bot, chatId: Long) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return

        val params = calcData.toOrderParameters()
        if (params == null) {
            logger.error("Не удалось создать OrderParameters из CalculationData для чата $chatId")
            sendOrEditMessage(bot, chatId, "Произошла внутренняя ошибка при подготовке данных для расчета.", null, editPrevious = false)
            return
        }

        val result = calculatorService.calculate(params)
        val resultText = formatCalculationResult(result)

        sendOrEditMessage(bot, chatId, textProvider.get("calc.result.success", resultText), null, editPrevious = false)

        sessionManager.updateSession(chatId, session.copy(
            mode = UserMode.MAIN_MENU,
            currentCalculation = null
        ))
        showMainMenu(bot, chatId, editPrevious = false)
    }


    private fun formatCalculationResult(result: CalculationResult): String {
        val formatter = DecimalFormat("#,##0.00")
        val builder = StringBuilder()

        if (result.items.isNotEmpty()) {
            builder.append("Детализация:\n")
            result.items.forEach { item ->
                val price = item.workPrice + item.materialPrice
                if (price > 0) {
                    val escapedDescription = escapeMarkdownV1(item.description)
                    builder.append("• ${escapedDescription}: ${formatter.format(price)} ₽\n")
                }
            }
            builder.append("\n")
        }

        if (result.comments.any { it.isNotEmpty() }) {
            builder.append("Комментарии:\n")
            result.comments.forEach { comment ->
                val escapedComment = escapeMarkdownV1(comment)
                builder.append("- $escapedComment\n")
            }
            builder.append("\n")
        }

        builder.append("------------------------------\n")
        builder.append("Итоговая стоимость: *${formatter.format(result.finalTotalPrice)} ₽*")

        return builder.toString()
    }

    private fun handleBackNavigation(bot: Bot, chatId: Long, destination: String) {
        val session = sessionManager.getSession(chatId)
        when (destination) {
            KeyboardFactory.DEST_CALC_START -> {
                startCalculation(bot, chatId)
            }
            KeyboardFactory.DEST_CHOOSE_PAPER -> {
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_PAPER_TYPE))
                sendOrEditMessage(
                    bot = bot,
                    chatId = chatId,
                    text = textProvider.get("calc.prompt.choose_paper_type"),
                    replyMarkup = keyboardFactory.buildCalcPaperTypeMenu()
                )
            }
            KeyboardFactory.DEST_CHOOSE_MATERIAL_CAT -> {
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_MATERIAL_CATEGORY))
                sendOrEditMessage(
                    bot = bot,
                    chatId = chatId,
                    text = textProvider.get("calc.prompt.choose_material_category"),
                    replyMarkup = keyboardFactory.buildCalcMaterialCategoryMenu()
                )
            }
            else -> {
                logger.warn("Неизвестное направление для возврата: $destination")
                showMainMenu(bot, chatId)
            }
        }
    }
}