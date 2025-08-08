package org.example.utils

/**
 * Очищает и исправляет MarkdownV1 разметку, чтобы избежать ошибок Telegram API.
 *
 * @param text Текст от LLM или другого источника.
 * @return Безопасный для отправки текст.
 */
fun sanitizeMarkdownV1(text: String): String {
    var sanitizedText = text

    val unpairedChars = listOf('*', '_', '`')
    for (char in unpairedChars) {
        if (sanitizedText.count { it == char } % 2 != 0) {
            val lastIndex = sanitizedText.lastIndexOf(char)
            if (lastIndex != -1 && sanitizedText.substring(lastIndex + 1).isBlank()) {
                sanitizedText = sanitizedText.substring(0, lastIndex)
            }
        }
    }

    sanitizedText = sanitizedText.replace("*`", "`").replace("`*", "`")
    sanitizedText = sanitizedText.replace("_`", "`").replace("`_", "`")


    for (char in unpairedChars) {
        if (sanitizedText.count { it == char } % 2 != 0) {
            sanitizedText = sanitizedText.replace(char.toString(), "\\" + char)
        }
    }

    return sanitizedText
}