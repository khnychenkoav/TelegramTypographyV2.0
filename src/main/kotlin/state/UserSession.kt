package org.example.state

enum class UserMode {
    AWAITING_NAME,
    CONVERSATION
}

data class UserSession(
    val mode: UserMode = UserMode.AWAITING_NAME,
    val name: String? = null
)