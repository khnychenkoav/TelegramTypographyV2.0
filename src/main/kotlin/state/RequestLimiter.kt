package org.example.state

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.time.LocalDate

object RequestLimiter {
    private const val MAX_COMPLEX_REQUESTS_PER_DAY = 10
    private val userRequestCounts = ConcurrentHashMap<Long, ConcurrentHashMap<LocalDate, AtomicInteger>>()

    /**
     * Проверяет, может ли пользователь сделать сложный запрос.
     * Если может, увеличивает счетчик и возвращает true.
     */
    fun allowComplexRequest(userId: Long): Boolean {
        val today = LocalDate.now()
        val userDailyCounts = userRequestCounts.computeIfAbsent(userId) { ConcurrentHashMap() }

        userDailyCounts.keys.retainAll { it == today }

        val counter = userDailyCounts.computeIfAbsent(today) { AtomicInteger(0) }

        if (counter.get() < MAX_COMPLEX_REQUESTS_PER_DAY) {
            counter.incrementAndGet()
            return true
        }
        return false
    }
}