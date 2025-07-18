package org.example.utils

import java.util.Properties

class TextProvider(private val resourceName: String) {
    private val properties = Properties()

    init {
        val inputStream = this::class.java.classLoader.getResourceAsStream(resourceName)
        if (inputStream != null) {
            properties.load(inputStream.reader(Charsets.UTF_8))
        } else {
            throw IllegalArgumentException("Resource file not found: $resourceName")
        }
    }

    /**
     * Получает текст по ключу.
     * @param key Ключ из файла .properties
     * @param args Аргументы для подстановки (например, имя пользователя)
     * @return Отформатированный текст
     */
    fun get(key: String, vararg args: Any): String {
        val template = properties.getProperty(key) ?: return "[$key NOT FOUND]"
        return String.format(template, *args)
    }
}