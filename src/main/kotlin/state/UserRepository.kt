package org.example.state

import org.slf4j.LoggerFactory
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class UserRepository(private val storagePath: String = "data/users.txt") {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val userIds: MutableSet<Long> = Collections.newSetFromMap(ConcurrentHashMap())

    init {
        loadUsers()
    }

    private fun loadUsers() {
        val file = File(storagePath)
        if (file.exists()) {
            try {
                file.forEachLine { line ->
                    line.toLongOrNull()?.let { userIds.add(it) }
                }
                logger.info("Загружено {} уникальных ID пользователей из {}", userIds.size, storagePath)
            } catch (e: Exception) {
                logger.error("Ошибка при загрузке ID пользователей из файла $storagePath", e)
            }
        } else {
            logger.info("Файл с пользователями {} не найден. Будет создан новый.", storagePath)
            file.parentFile.mkdirs()
        }
    }

    /**
     * Добавляет пользователя, если его еще нет, и сохраняет в файл.
     */
    fun addUser(chatId: Long) {
        if (userIds.add(chatId)) {
            try {
                File(storagePath).appendText("$chatId\n")
                logger.info("Добавлен новый пользователь с ID: {}. Всего пользователей: {}", chatId, userIds.size)
            } catch (e: Exception) {
                logger.error("Не удалось сохранить нового пользователя в файл $storagePath", e)
            }
        }
    }

    /**
     * Возвращает список всех ID пользователей.
     */
    fun getAllUserIds(): Set<Long> {
        return userIds.toSet()
    }
}