package org.example.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.TextHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import org.example.calculation.CalculatorService
import org.example.calculation.PriceListProvider
import org.example.processing.JobQueue
import org.example.processing.LlmJob
import org.example.state.CalculationData
import org.example.state.SessionManager
import org.example.state.UserMode
import org.example.utils.TextProvider
import org.slf4j.LoggerFactory

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

            UserMode.CALC_AWAITING_PRODUCT_TYPE -> {
                sendMessage(chatId, textProvider.get("calc.prompt.choose_product"), keyboardFactory.buildCalcProductTypeMenu())
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

        val session = sessionManager.getSession(chatId)

        when {
            callbackData == KeyboardFactory.START_CHAT_CALLBACK -> startLlmChat(env.bot, chatId)
            callbackData == KeyboardFactory.CONTACT_OPERATOR_CALLBACK -> startOperatorQuery(env.bot, chatId)
            callbackData == KeyboardFactory.CALCULATE_ORDER_CALLBACK -> startCalculation(env.bot, chatId)

            callbackData == KeyboardFactory.CALC_PT_BADGE_CALLBACK -> handleProductTypeSelected(env.bot, chatId, "badge")
            callbackData == KeyboardFactory.CALC_PT_DIGITAL_PRINTING_CALLBACK -> TODO("Будет реализовано позже")
            callbackData == KeyboardFactory.CALC_PT_CUTTING_CALLBACK -> TODO("Будет реализовано позже")
            callbackData == KeyboardFactory.CALC_PT_CUTTING_AND_PRINTING_CALLBACK -> TODO("Будет реализовано позже")

            callbackData.startsWith(KeyboardFactory.CALC_BADGE_TYPE_PREFIX) -> {
                val badgeType = callbackData.removePrefix(KeyboardFactory.CALC_BADGE_TYPE_PREFIX)
                TODO("Обработать выбор типа значка: $badgeType")
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
            else -> TODO("Обработка для продукта $productType еще не реализована")
        }
    }
}