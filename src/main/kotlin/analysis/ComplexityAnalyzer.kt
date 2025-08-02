package org.example.analysis

import java.io.InputStream
import java.util.*

private fun loadKeywords(resourcePath: String): List<String> {
    val stream: InputStream = object {}.javaClass.classLoader.getResourceAsStream(resourcePath)
        ?: return emptyList()
    return stream.bufferedReader().readLines()
}

enum class Complexity { SIMPLE, COMPLEX }

class ComplexityAnalyzer {
    private val simpleKeywords = loadKeywords("keywords_simple.txt")
    private val complexKeywords = loadKeywords("keywords_complex.txt")

    fun analyze(text: String): Complexity {
        val lowerCaseText = text.lowercase(Locale.getDefault())

        val hasComplex = complexKeywords.any { lowerCaseText.contains(it) }
        if (hasComplex) {
            return Complexity.COMPLEX
        }

        return Complexity.SIMPLE
    }
}