package org.example.state

import java.util.concurrent.ConcurrentHashMap

class SessionManager {
    private val sessions = ConcurrentHashMap<Long, UserSession>()

    fun getSession(userId: Long): UserSession {
        return sessions.computeIfAbsent(userId) { UserSession() }
    }

    fun updateSession(userId: Long, newSession: UserSession) {
        sessions[userId] = newSession
    }

    fun resetSession(userId: Long) {
        sessions[userId] = UserSession()
    }
}