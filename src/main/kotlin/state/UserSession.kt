package org.example.state

enum class UserMode {
    AWAITING_NAME,
    MAIN_MENU,
    AWAITING_ORDER_DETAILS,
    AWAITING_OPERATOR_QUERY
}

data class UserSession(
    val mode: UserMode = UserMode.AWAITING_NAME,
    val name: String? = null
)