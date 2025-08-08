package org.example.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.TextHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.network.fold
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.TelegramFile
import org.example.analysis.CannedResponses
import org.example.analysis.Complexity
import org.example.analysis.ComplexityAnalyzer
import org.example.calculation.CalculatorService
import org.example.calculation.PriceListProvider
import org.example.calculation.models.CalculationResult
import org.example.processing.BroadcastService
import org.example.processing.JobQueue
import org.example.processing.LlmJob
import org.example.services.LlmSwitcher
import org.example.services.LlmType
import org.example.state.CalculationData
import org.example.state.RequestLimiter
import org.example.state.SessionManager
import org.example.state.UserMode
import org.example.state.UserRepository
import org.example.utils.OPERATOR_CHAT_ID
import org.example.utils.TextProvider
import org.example.utils.sanitizeMarkdownV1
import org.slf4j.LoggerFactory
import java.io.File
import java.text.DecimalFormat


class ResponseHandler(
    private val sessionManager: SessionManager,
    private val textProvider: TextProvider,
    private val jobQueue: JobQueue,
    private val calculatorService: CalculatorService,
    private val priceListProvider: PriceListProvider,
    private val complexityAnalyzer: ComplexityAnalyzer,
    private val userRepository: UserRepository,
    private val broadcastService: BroadcastService,
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
                parseMode = ParseMode.MARKDOWN
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
                        parseMode = ParseMode.MARKDOWN
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
                parseMode = ParseMode.MARKDOWN
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
        userRepository.addUser(chatId)
        sessionManager.resetSession(chatId)
        val session = sessionManager.getSession(chatId)
        sessionManager.updateSession(chatId, session.copy(mode = UserMode.AWAITING_NAME))
        env.bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = textProvider.get("start.welcome")
        )
    }

    fun onNewsMessageCommand(env: CommandHandlerEnvironment) {
        val chatId = env.message.chat.id
        if (chatId != OPERATOR_CHAT_ID) {
            logger.warn("Попытка вызова /news_message от не-оператора: {}", chatId)
            env.bot.sendMessage(ChatId.fromId(chatId), "Эта команда доступна только администратору.")
            return
        }

        val session = sessionManager.getSession(chatId)
        sessionManager.updateSession(chatId, session.copy(mode = UserMode.AWAITING_NEWS_MESSAGE))
        val updatedSession = sessionManager.getSession(chatId)
        logger.info("Оператор {} переведен в режим {}. Проверка: {}", chatId, UserMode.AWAITING_NEWS_MESSAGE, updatedSession.mode)
        env.bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = "Готов принять сообщение для рассылки. Отправьте его следующим сообщением (текст, фото с подписью и т.д.).\n\nДля отмены введите /cancel"
        )
    }

    fun onCancelCommand(env: CommandHandlerEnvironment) {
        val chatId = env.message.chat.id
        val session = sessionManager.getSession(chatId)
        if (session.mode == UserMode.AWAITING_NEWS_MESSAGE) {
            sessionManager.updateSession(chatId, session.copy(mode = UserMode.MAIN_MENU))
            env.bot.sendMessage(ChatId.fromId(chatId), "Рассылка отменена.")
        }
    }

    fun handleAnyMessage(env: MessageHandlerEnvironment) {
        val chatId = env.message.chat.id
        userRepository.addUser(chatId)

        val session = sessionManager.getSession(chatId)

        if (chatId == OPERATOR_CHAT_ID) {
            logger.info("Сообщение от оператора. Текущий режим: {}", session.mode)
        }

        if (session.mode == UserMode.AWAITING_NEWS_MESSAGE && chatId == OPERATOR_CHAT_ID) {
            logger.info("Поймано сообщение для рассылки от оператора {}.", chatId)
            broadcastService.startBroadcast(env.message, env.bot)
            sessionManager.updateSession(chatId, session.copy(mode = UserMode.MAIN_MENU))
            logger.info("Оператор {} возвращен в режим {}", chatId, UserMode.MAIN_MENU)
            return
        }

        if (env.message.text != null) {
            onTextMessage(env.toTextHandlerEnvironment())
        } else {
            onFileReceived(env)
        }
    }

    fun onTextMessage(env: TextHandlerEnvironment) {
        val chatId = env.message.chat.id
        val text = env.message.text.orEmpty()
        val session = sessionManager.getSession(chatId)
        val cannedResponse = CannedResponses.findResponse(text)
        if (cannedResponse != null) {
            sendOrEditMessage(env.bot, chatId, cannedResponse, keyboardFactory.buildBackToMainMenuKeyboard(), editPrevious = false)
            return
        }
        when (session.mode) {
            UserMode.AWAITING_NAME -> handleNameInput(env, text)
            UserMode.MAIN_MENU -> showMainMenu(env.bot, chatId)
            UserMode.LLM_CHAT -> handleLlmChat(env, text)
            UserMode.AWAITING_OPERATOR_QUERY -> handleOperatorQuery(env, text)
            UserMode.CALC_AWAITING_QUANTITY -> handleQuantitySelected(env.bot, chatId, text)
            UserMode.CALC_AWAITING_DIMENSIONS -> handleDimensionsSelected(env.bot, chatId, text)
            UserMode.CALC_AWAITING_AI_ESTIMATION -> {
                sendOrEditMessage(env.bot, chatId, textProvider.get("llm.in_queue"), null, editPrevious = true)
                LlmSwitcher.switchTo(LlmType.GIGA_CHAT)
                val job = LlmJob(
                    chatId = chatId,
                    systemPrompt = textProvider.get("llm.system_prompt.estimator"),
                    history = emptyList(),
                    newUserPrompt = text,
                    onResult = { result ->
                        val sanitizedResult = sanitizeMarkdownV1(result)
                        sendOrEditMessage(env.bot, chatId, sanitizedResult, null, editPrevious = false)
                        showMainMenu(env.bot, chatId, editPrevious = false)
                    }
                )
                jobQueue.submit(job)
            }
            UserMode.AWAITING_FILE_CAPTION -> {
                val user = env.message.from ?: return
                val userName = user.firstName + (user.lastName?.let { " $it" } ?: "")
                val userMention = "[${userName}](tg://user?id=${user.id})"

                val messageForOperator = """
            *Комментарий к файлу от* $userMention:
            
            ${escapeMarkdownV1(text)}
        """.trimIndent()

                if (OPERATOR_CHAT_ID != 0L) {
                    env.bot.sendMessage(
                        chatId = ChatId.fromId(OPERATOR_CHAT_ID),
                        text = messageForOperator,
                        parseMode = ParseMode.MARKDOWN
                    )
                }
                sendOrEditMessage(env.bot, chatId, textProvider.get("file.caption.received"), null, editPrevious = false)
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.MAIN_MENU))
                showMainMenu(env.bot, chatId, editPrevious = false)
            }
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

            callbackData == KeyboardFactory.INFO_CALLBACK -> {
                sendOrEditMessage(
                    bot = env.bot,
                    chatId = chatId,
                    text = textProvider.get("info.prompt"),
                    replyMarkup = keyboardFactory.buildInfoMenu()
                )
            }
            callbackData == KeyboardFactory.INFO_ADDRESSES_CALLBACK -> {
                sendOrEditMessage(
                    bot = env.bot,
                    chatId = chatId,
                    text = textProvider.get("info.text.addresses"),
                    replyMarkup = keyboardFactory.buildInfoMenu()
                )
                env.bot.sendLocation(
                    chatId = ChatId.fromId(chatId),
                    latitude = 55.041256f,
                    longitude = 82.930810f
                )
            }
            callbackData == KeyboardFactory.INFO_FILE_REQ_CALLBACK -> {
                sendOrEditMessage(
                    bot = env.bot,
                    chatId = chatId,
                    text = textProvider.get("info.text.file_requirements"),
                    replyMarkup = keyboardFactory.buildInfoMenu()
                )
            }

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

            callbackData == KeyboardFactory.CALC_PT_AI_ESTIMATION_CALLBACK -> {
                val session = sessionManager.getSession(chatId)
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_AI_ESTIMATION))
                sendOrEditMessage(
                    bot = env.bot,
                    chatId = chatId,
                    text = textProvider.get("calc.prompt.ai_estimation"),
                    replyMarkup = keyboardFactory.buildBackToMainMenuKeyboard()
                )
            }

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

    fun onFileReceived(env: MessageHandlerEnvironment) {
        val chatId = env.message.chat.id
        val session = sessionManager.getSession(chatId)

        if (session.mode != UserMode.MAIN_MENU && session.mode != UserMode.LLM_CHAT) {
            logger.warn("Файл получен в неподходящем режиме (${session.mode}). Игнорируем.")
            return
        }

        if (OPERATOR_CHAT_ID != 0L) {
            env.bot.forwardMessage(
                chatId = ChatId.fromId(OPERATOR_CHAT_ID),
                fromChatId = ChatId.fromId(chatId),
                messageId = env.message.messageId
            )
        }

        sessionManager.updateSession(chatId, session.copy(mode = UserMode.AWAITING_FILE_CAPTION))

        sendOrEditMessage(env.bot, chatId, textProvider.get("file.received"), null, editPrevious = false)
    }

    private fun MessageHandlerEnvironment.toTextHandlerEnvironment(): TextHandlerEnvironment {
        return TextHandlerEnvironment(bot, update, message, message.text!!)
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

        if (OPERATOR_CHAT_ID != 0L) {
            env.bot.sendMessage(
                chatId = ChatId.fromId(OPERATOR_CHAT_ID),
                text = messageForOperator,
                parseMode = ParseMode.MARKDOWN
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

        sendOrEditPhotoMessage(
            bot = bot,
            chatId = chatId,
            photoResourcePath = "img/TypographyBotLogo.png",
            text = textProvider.get("calc.prompt.choose_product"),
            replyMarkup = keyboardFactory.buildCalcProductTypeMenu(),
            editPrevious = true
        )
    }

    private fun handleLlmChat(env: TextHandlerEnvironment, text: String) {
        val chatId = env.message.chat.id
        val session = sessionManager.getSession(chatId)

        val complexity = complexityAnalyzer.analyze(text)

        var useGigaChat: Boolean
        var reason: String

        if (complexity == Complexity.COMPLEX) {
            if (RequestLimiter.allowComplexRequest(chatId)) {
                useGigaChat = true
                reason = " (сложный запрос)"
            } else {
                useGigaChat = false
                reason = " (лимит сложных запросов исчерпан, используется локальная модель)"
            }
        } else {
            useGigaChat = false
            reason = " (простой запрос)"
        }

        logger.info("Решено использовать ${if (useGigaChat) "GigaChat" else "Local LLM"} для чата $chatId. Причина: $reason")

        if (useGigaChat) {
            LlmSwitcher.switchTo(LlmType.GIGA_CHAT)
        } else {
            LlmSwitcher.switchTo(LlmType.LOCAL)
        }

        val job = LlmJob(
            chatId = chatId,
            systemPrompt = textProvider.get("llm.system_prompt.chat"),
            history = session.conversationHistory.toList(),
            newUserPrompt = text,
            onResult = { result ->
                val updatedHistory = session.conversationHistory
                updatedHistory.add(text to result)
                while (updatedHistory.size > 4) {
                    updatedHistory.removeFirst()
                }
                val sanitizedResult = sanitizeMarkdownV1(result)
                sessionManager.updateSession(chatId, session.copy(conversationHistory = updatedHistory))
                sendOrEditMessage(env.bot, chatId, sanitizedResult, replyMarkup = keyboardFactory.buildBackToMainMenuKeyboard(), editPrevious = false)
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

        val photoPath: String?
        val text: String
        val keyboard: InlineKeyboardMarkup?

        when (productType) {
            "badge" -> {
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_BADGE_TYPE))
                photoPath = "img/calc_badges.png"
                text = textProvider.get("calc.prompt.choose_badge_type")
                keyboard = keyboardFactory.buildCalcBadgeTypeMenu()
            }
            "digital_printing" -> {
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_PAPER_TYPE))
                photoPath = "img/calc_digital_print.png"
                text = textProvider.get("calc.prompt.choose_paper_type")
                keyboard = keyboardFactory.buildCalcPaperTypeMenu()
            }
            "cutting" -> {
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_MATERIAL_CATEGORY))
                photoPath = "img/calc_cutting.png"
                text = textProvider.get("calc.prompt.choose_material_category")
                keyboard = keyboardFactory.buildCalcMaterialCategoryMenu()
            }
            "cutting_and_printing" -> {
                sessionManager.updateSession(chatId, session.copy(mode = UserMode.CALC_AWAITING_MATERIAL_CATEGORY))
                photoPath = "img/calc_cutting_print.png"
                text = textProvider.get("calc.prompt.choose_material_category")
                keyboard = keyboardFactory.buildCalcMaterialCategoryMenu()
            }
            else -> {
                logger.error("Неизвестный тип продукта: $productType")
                sendOrEditMessage(bot, chatId, "Произошла ошибка, неизвестный тип продукта.", null, editPrevious = false)
                return
            }
        }
        sendOrEditPhotoMessage(
            bot = bot,
            chatId = chatId,
            photoResourcePath = photoPath,
            text = text,
            replyMarkup = keyboard
        )
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

        val hasErrors = result.comments.any {
            it.contains("не найден", ignoreCase = true) ||
                    it.contains("ошибка", ignoreCase = true) ||
                    it.contains("необходимо указать", ignoreCase = true)
        }

        if (hasErrors || result.finalTotalPrice == 0.0) {
            val resultText = formatCalculationResult(result)
            sendOrEditMessage(bot, chatId, textProvider.get("calc.result.success", resultText), null, editPrevious = false)
            sessionManager.updateSession(chatId, session.copy(mode = UserMode.MAIN_MENU, currentCalculation = null))
            showMainMenu(bot, chatId, editPrevious = false)
        } else {
            val resultTextForLlm = formatCalculationResult(result)
            val initialMessage = textProvider.get("calc.result.success", resultTextForLlm)

            sendOrEditMessage(bot, chatId, textProvider.get("llm.in_queue"), null, editPrevious = true)
            LlmSwitcher.switchTo(LlmType.GIGA_CHAT)
            val job = LlmJob(
                chatId = chatId,
                systemPrompt = textProvider.get("llm.system_prompt.commentator"),
                history = emptyList(),
                newUserPrompt = initialMessage,
                onResult = { llmResponse ->
                    val sanitizedResult = sanitizeMarkdownV1(llmResponse)
                    sendOrEditMessage(bot, chatId, sanitizedResult, null, editPrevious = false)
                    sessionManager.updateSession(chatId, session.copy(mode = UserMode.MAIN_MENU, currentCalculation = null))
                    showMainMenu(bot, chatId, editPrevious = false)
                }
            )
            jobQueue.submit(job)
        }
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

    private fun sendOrEditPhotoMessage(
        bot: Bot,
        chatId: Long,
        photoResourcePath: String?,
        text: String,
        replyMarkup: InlineKeyboardMarkup?,
        editPrevious: Boolean = true
    ) {
        val session = sessionManager.getSession(chatId)
        val messageIdToDelete = if (editPrevious) session.lastBotMessageId else null

        if (messageIdToDelete != null) {
            bot.deleteMessage(chatId = ChatId.fromId(chatId), messageId = messageIdToDelete)
        }

        var finalMessageId: Long? = null

        if (photoResourcePath != null) {
            val photoStream = javaClass.classLoader.getResourceAsStream(photoResourcePath)

            if (photoStream == null) {
                logger.error("Не удалось найти ресурс изображения: $photoResourcePath. Отправляю только текст.")
                sendOrEditMessage(bot, chatId, text, replyMarkup, editPrevious = false)
                return
            }

            val photoBytes = photoStream.readBytes()
            photoStream.close()

            val telegramFile = TelegramFile.ByByteArray(
                fileBytes = photoBytes,
                filename = "photo.png"
            )

            bot.sendPhoto(
                chatId = ChatId.fromId(chatId),
                photo = telegramFile,
                caption = text,
                replyMarkup = replyMarkup,
                parseMode = ParseMode.MARKDOWN
            ).fold(
                { response -> finalMessageId = response?.result?.messageId },
                { error ->
                    logger.error("Не удалось отправить сообщение с фото: $error")
                }
            )
        } else {
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = text,
                replyMarkup = replyMarkup,
                parseMode = ParseMode.MARKDOWN
            ).fold(
                { response -> finalMessageId = response.messageId },
                { error ->
                    logger.error("Не удалось отправить текстовое сообщение: $error")
                }
            )
        }

        finalMessageId?.let {
            session.lastBotMessageId = it
            sessionManager.updateSession(chatId, session)
        }
    }
}