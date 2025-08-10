package org.example.state

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.time.LocalDate

enum class LimitType {
    COMPLEX_LLM_REQUEST,
    IMAGE_GENERATION
}

object RequestLimiter {
    private val limits = mapOf(
        LimitType.COMPLEX_LLM_REQUEST to 10,
        LimitType.IMAGE_GENERATION to 2
    )

    private val userRequestCounts = ConcurrentHashMap<Long, ConcurrentHashMap<LocalDate, ConcurrentHashMap<LimitType, AtomicInteger>>>()

    /**
     * Проверяет, может ли пользователь выполнить действие определенного типа.
     * Если может, увеличивает счетчик и возвращает true.
     * @param userId ID пользователя.
     * @param type Тип действия из enum LimitType.
     * @return true, если действие разрешено, иначе false.
     */
    fun isAllowed(userId: Long, type: LimitType): Boolean {
        val maxRequests = limits[type] ?: return true

        val today = LocalDate.now()
        val userDailyMaps = userRequestCounts.computeIfAbsent(userId) { ConcurrentHashMap() }

        userDailyMaps.keys.retainAll { it == today }

        val todayCounts = userDailyMaps.computeIfAbsent(today) { ConcurrentHashMap() }
        val counter = todayCounts.computeIfAbsent(type) { AtomicInteger(0) }

        if (counter.get() <= maxRequests) {
            counter.incrementAndGet()
            return true
        }
        return false
    }

    /**
     * Возвращает максимальное количество разрешенных запросов для данного типа.
     */
    fun getLimitFor(type: LimitType): Int {
        return limits[type] ?: Int.MAX_VALUE
    }

    /**
     * Возвращает количество оставшихся попыток для пользователя на сегодня.
     */
    fun getRemainingAttempts(userId: Long, type: LimitType): Int {
        val maxRequests = limits[type] ?: return Int.MAX_VALUE
        val today = LocalDate.now()
        val currentCount = userRequestCounts[userId]?.get(today)?.get(type)?.get() ?: 0
        return (maxRequests - currentCount).coerceAtLeast(0)
    }
}