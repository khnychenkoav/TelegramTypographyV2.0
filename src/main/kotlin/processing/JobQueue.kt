package org.example.processing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.example.services.LlmService
import org.example.services.LlmSwitcher
import org.example.utils.TextProvider
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

data class LlmJob(
    val chatId: Long,
    val systemPrompt: String,
    val history: List<Pair<String, String>>,
    val newUserPrompt: String,
    val onResult: (rawResult: String, resultForUser: String) -> Unit
)

class JobQueue(
    private val textProvider: TextProvider,
    maxParallelJobs: Int = 1,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val jobChannel = Channel<LlmJob>(Channel.UNLIMITED)
    private val semaphore = Semaphore(permits = maxParallelJobs)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        logger.info("JobQueue инициализирован с {} воркерами.", maxParallelJobs)
        startWorkers()
    }

    /**
     * Добавляет задачу в очередь.
     * @return true, если задача успешно добавлена.
     */
    fun submit(job: LlmJob): Boolean {
        val result = jobChannel.trySend(job)
        if (result.isSuccess) {
            logger.info("Задача для чата {} успешно добавлена в очередь.", job.chatId)
        } else {
            logger.error("Не удалось добавить задачу для чата {} в очередь. Очередь заполнена? {}", job.chatId, result)
        }
        return result.isSuccess
    }

    private fun startWorkers() {
        logger.info("Запускаем воркеры...")
        scope.launch {
            for (job in jobChannel) {
                logger.debug("Получена задача из канала для чата {}.", job.chatId)
                semaphore.withPermit {
                    logger.info("Воркер получил семафор. Начинаю обработку задачи для чата {}.", job.chatId)
                    launch {
                        try {
                            val llmService = LlmSwitcher.getCurrentLlmService()
                            val rawResult = llmService.generateWithHistory(job.systemPrompt, job.history, job.newUserPrompt)
                            logger.info("Задача для чата {} успешно обработана. Результат: '{}'", job.chatId, rawResult.take(100))
                            val disclaimer = textProvider.get("llm.disclaimer")
                            val resultForUser = rawResult + disclaimer
                            job.onResult(rawResult, resultForUser)
                        } catch (e: Exception) {
                            logger.error("Ошибка при обработке задачи для чата ${job.chatId}", e)
                            job.onResult(
                                "Внутренняя ошибка: ${e.message}",
                                "К сожалению, произошла внутренняя ошибка. Попробуйте позже."
                            )
                        }
                    }
                }
            }
            logger.warn("Цикл воркера завершился. Новые задачи обрабатываться не будут.")
        }
    }
}