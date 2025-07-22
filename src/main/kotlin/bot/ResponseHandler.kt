package org.example.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.TextHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
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

    lateinit var sendMessage: (chatId: Long, text: String, replyMarkup: InlineKeyboardMarkup?) -> Unit

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
            UserMode.CALC_AWAITING_PRODUCT_TYPE -> {
                sendMessage(chatId, textProvider.get("calc.prompt.choose_product"), keyboardFactory.buildCalcProductTypeMenu())
            }
            UserMode.CALC_AWAITING_BADGE_TYPE -> {
                sendMessage(chatId, textProvider.get("calc.prompt.choose_badge_type"), keyboardFactory.buildCalcBadgeTypeMenu())
            }
            UserMode.CALC_AWAITING_PAPER_TYPE -> {
                sendMessage(chatId, textProvider.get("calc.prompt.choose_paper_type"), keyboardFactory.buildCalcPaperTypeMenu())
            }
            UserMode.CALC_AWAITING_PRINT_SIDES -> {
                sendMessage(chatId, textProvider.get("calc.prompt.choose_print_sides"), keyboardFactory.buildCalcPrintSidesMenu())
            }
            UserMode.CALC_AWAITING_DIMENSIONS -> handleDimensionsSelected(env.bot, chatId, text)
            UserMode.CALC_AWAITING_MATERIAL_CATEGORY -> {
                sendMessage(chatId, textProvider.get("calc.prompt.choose_material_category"), keyboardFactory.buildCalcMaterialCategoryMenu())
            }
            UserMode.CALC_AWAITING_MATERIAL_AND_THICKNESS -> {
                sendMessage(chatId, "Пожалуйста, выберите материал из списка.", null)
            }

            else -> {
                logger.warn("Получено текстовое сообщение в необрабатываемом режиме: {}", session.mode)
                sendMessage(chatId, "Пожалуйста, используйте кнопки для выбора.", null)
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
            callbackData == KeyboardFactory.CALC_PT_BADGE_CALLBACK -> handleProductTypeSelected(env.bot, chatId, "badge")
            callbackData == KeyboardFactory.CALC_PT_DIGITAL_PRINTING_CALLBACK -> handleProductTypeSelected(env.bot, chatId, "digital_printing")
            callbackData == KeyboardFactory.CALC_PT_CUTTING_CALLBACK -> handleProductTypeSelected(env.bot, chatId, "cutting")
            callbackData == KeyboardFactory.CALC_PT_CUTTING_AND_PRINTING_CALLBACK -> TODO("Будет реализовано позже")

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
        }
    }


    private fun handleNameInput(env: TextHandlerEnvironment, name: String) {
        val chatId = env.message.chat.id
        val session = sessionManager.getSession(chatId)
        sessionManager.updateSession(chatId, session.copy(name = name, mode = UserMode.MAIN_MENU))

        env.bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = textProvider.get("greeting.personal", name),
            replyMarkup = keyboardFactory.buildMainMenu()
        )
    }

    private fun showMainMenu(bot: Bot, chatId: Long) {
        bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = textProvider.get("menu.prompt"),
            replyMarkup = keyboardFactory.buildMainMenu()
        )
    }

    private fun startLlmChat(bot: Bot, chatId: Long) {
        val session = sessionManager.getSession(chatId)
        sessionManager.updateSession(chatId, session.copy(mode = UserMode.LLM_CHAT))
        bot.sendMessage(ChatId.fromId(chatId), textProvider.get("callback.chat.prompt"))
    }

    private fun startOperatorQuery(bot: Bot, chatId: Long) {
        val session = sessionManager.getSession(chatId)
        sessionManager.updateSession(chatId, session.copy(mode = UserMode.AWAITING_OPERATOR_QUERY))
        bot.sendMessage(ChatId.fromId(chatId), textProvider.get("callback.operator.prompt"))
    }

    private fun handleOperatorQuery(env: TextHandlerEnvironment, text: String) {
        val chatId = env.message.chat.id
        // TODO: Реализовать логику пересылки сообщения оператору
        sendMessage(chatId, textProvider.get("operator.query.received", text), null)

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

        bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = textProvider.get("calc.prompt.choose_product"),
            replyMarkup = keyboardFactory.buildCalcProductTypeMenu()
        )
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
                sendMessage(chatId, result, null)
            }
        )
        if (jobQueue.submit(job)) {
            sendMessage(chatId, textProvider.get("llm.in_queue"), null)
        } else {
            sendMessage(chatId, textProvider.get("llm.error"), null)
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
                val keyboard = keyboardFactory.buildCalcBadgeTypeMenu()
                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = textProvider.get("calc.prompt.choose_badge_type"),
                    replyMarkup = keyboard
                )
            }
            "digital_printing" -> {
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_PAPER_TYPE))
                val keyboard = keyboardFactory.buildCalcPaperTypeMenu()
                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = textProvider.get("calc.prompt.choose_paper_type"),
                    replyMarkup = keyboard
                )
            }
            "cutting" -> {
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_MATERIAL_CATEGORY))
                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = textProvider.get("calc.prompt.choose_material_category"),
                    replyMarkup = keyboardFactory.buildCalcMaterialCategoryMenu()
                )
            }
            else -> TODO("Обработка для продукта $productType еще не реализована")
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
            // TODO: Сообщить об ошибке и вернуться в меню
            return
        }

        sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_QUANTITY))
        bot.sendMessage(ChatId.fromId(chatId), textProvider.get("calc.prompt.enter_quantity"))
    }

    private fun handlePaperTypeSelected(bot: Bot, chatId: Long, paperType: String) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return

        calcData.material = paperType

        sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_PRINT_SIDES))
        bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = textProvider.get("calc.prompt.choose_print_sides"),
            replyMarkup = keyboardFactory.buildCalcPrintSidesMenu()
        )
    }

    private fun handlePrintSidesSelected(bot: Bot, chatId: Long, sides: Int) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return

        calcData.printingSides = sides

        sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_QUANTITY))
        bot.sendMessage(ChatId.fromId(chatId), textProvider.get("calc.prompt.enter_quantity"))
    }

    private fun handleQuantitySelected(bot: Bot, chatId: Long, text: String) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return

        val quantity = text.toIntOrNull()
        if (quantity == null || quantity <= 0) {
            sendMessage(chatId, textProvider.get("calc.error.invalid_number"), null)
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
            logger.error("Не удалось создать клавиатуру для категории материалов '$category'")
            // TODO: Сообщить об ошибке
            return
        }

        bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = textProvider.get("calc.prompt.choose_material"),
            replyMarkup = keyboard
        )
    }

    private fun handleMaterialSelected(bot: Bot, chatId: Long, materialKey: String) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return

        val parts = materialKey.split('_')
        val thicknessString = parts.lastOrNull()?.removeSuffix("мм")

        if (parts.size < 2 || thicknessString == null) {
            logger.error("Некорректный формат materialKey: $materialKey")
            sendMessage(chatId, "Произошла внутренняя ошибка. Попробуйте начать заново.", null)
            startCalculation(bot, chatId)
            return
        }

        calcData.material = parts.dropLast(1).joinToString("_")
        calcData.thicknessMm = thicknessString.toIntOrNull()

        sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_DIMENSIONS))
        bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = textProvider.get("calc.prompt.enter_dimensions", materialKey)
        )
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
                sendMessage(chatId, textProvider.get("calc.error.invalid_dimensions"), null)
                return
            }
        }

        calcData.quantity = 1

        finishCalculation(bot, chatId)
    }


    private fun finishCalculation(bot: Bot, chatId: Long) {
        val session = sessionManager.getSession(chatId)
        val calcData = session.currentCalculation ?: return

        val params = calcData.toOrderParameters()
        if (params == null) {
            logger.error("Не удалось создать OrderParameters из CalculationData для чата $chatId")
            // TODO: Сообщить об ошибке
            return
        }

        val result = calculatorService.calculate(params)
        val resultText = formatCalculationResult(result)

        sendMessage(chatId, textProvider.get("calc.result.success", resultText), null)

        sessionManager.updateSession(chatId, session.copy(
            mode = UserMode.MAIN_MENU,
            currentCalculation = null
        ))
        showMainMenu(bot, chatId)
    }


    private fun formatCalculationResult(result: CalculationResult): String {
        val formatter = DecimalFormat("#,##0.00")
        val builder = StringBuilder()

        if (result.items.isNotEmpty()) {
            builder.append("Детализация:\n")
            result.items.forEach { item ->
                val price = if (item.workPrice > 0) item.workPrice else item.materialPrice
                builder.append("• ${item.description}: ${formatter.format(price)} ₽\n")
            }
            builder.append("\n")
        }

        if (result.comments.any { it.isNotEmpty() }) {
            builder.append("Комментарии:\n")
            result.comments.forEach { comment ->
                builder.append("- $comment\n")
            }
            builder.append("\n")
        }

        builder.append("------------------------------\n")
        builder.append("Итоговая стоимость: *${formatter.format(result.finalTotalPrice)} ₽*")

        return builder.toString()
    }
}