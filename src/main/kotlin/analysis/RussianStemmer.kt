package org.example.analysis

import java.util.Locale

/**
 * Реализация стеммера Портера для русского языка.
 * Приводит слова к их основе. Например: "адреса" -> "адрес", "бегущий" -> "бег"
 */
object RussianStemmer {
    private val VOWELS = charArrayOf('а', 'е', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я')
    private val PERFECTIVE_GERUNDS_1 = arrayOf("в", "вши", "вшись")
    private val PERFECTIVE_GERUNDS_2 = arrayOf("ив", "ивши", "ившись", "ыв", "ывши", "ывшись")
    private val ADJECTIVES = arrayOf("ее", "ие", "ые", "ое", "ими", "ыми", "ей", "ий", "ый", "ой", "ем", "им", "ым", "ом", "его", "ого", "ему", "ому", "их", "ых", "ую", "юю", "ая", "яя", "ою", "ею")
    private val PARTICIPLES_1 = arrayOf("ем", "нн", "вш", "ющ", "щ")
    private val PARTICIPLES_2 = arrayOf("ивш", "ывш", "ующ")
    private val REFLEXIVES = arrayOf("ся", "сь")
    private val VERBS_1 = arrayOf("ла", "на", "ете", "йте", "ли", "й", "л", "ем", "н", "ло", "но", "ет", "ют", "ны", "ть", "ешь", "нно")
    private val VERBS_2 = arrayOf("ила", "ыла", "ена", "ейте", "уйте", "ите", "или", "ыли", "ей", "уй", "ил", "ыл", "им", "ым", "ен", "ило", "ыло", "ено", "ят", "ует", "уют", "ит", "ыт", "ены", "ить", "ыть", "ишь", "ую", "ю")
    private val NOUNS = arrayOf("а", "ев", "ов", "ие", "ье", "е", "иями", "ями", "ами", "еи", "ии", "и", "ией", "ей", "ой", "ий", "й", "иям", "ям", "ием", "ем", "ам", "ом", "о", "у", "ах", "иях", "ях", "ы", "ь", "ию", "ью", "ю", "ия", "ья", "я")
    private val SUPERLATIVES = arrayOf("ейш", "ейше")
    private val DERIVATIONALS = arrayOf("ост", "ость")

    fun stem(word: String): String {
        val lowerWord = word.lowercase(Locale.getDefault())
        if (isNotRussian(lowerWord)) return lowerWord

        val (rv, r2) = findRegions(lowerWord)
        var temp = removeFromEnd(lowerWord, rv, *PERFECTIVE_GERUNDS_1, *PERFECTIVE_GERUNDS_2)
        if (temp == lowerWord) {
            temp = removeFromEnd(temp, rv, *REFLEXIVES)
            temp = removeFromEnd(temp, rv, *ADJECTIVES, *PARTICIPLES_1, *PARTICIPLES_2)
            if (temp == lowerWord) {
                temp = removeFromEnd(temp, rv, *VERBS_1, *VERBS_2)
                if (temp == lowerWord) {
                    temp = removeFromEnd(temp, rv, *NOUNS)
                }
            }
        }

        temp = removeFromEnd(temp, rv, "и")

        temp = removeFromEnd(temp, r2, *DERIVATIONALS)

        var result = temp
        val endsWithNN = result.endsWith("нн")
        if(endsWithNN) result = result.substring(0, result.length - 1)

        result = removeFromEnd(result, rv, *SUPERLATIVES)
        result = removeFromEnd(result, result.indices, "ь")

        if (endsWithNN && result.endsWith("н")) {
            result += "н"
        }

        return result
    }

    private fun isNotRussian(word: String): Boolean = word.any { it !in 'а'..'я' && it != 'ё' }

    private fun findRegions(word: String): Pair<IntRange, IntRange> {
        var rvStart = 0
        for (i in 1 until word.length) {
            if (VOWELS.contains(word[i - 1])) {
                rvStart = i + 1
                break
            }
        }
        if (rvStart == 0 && word.isNotEmpty()) rvStart = word.length

        var r2Start = 0
        for (i in rvStart until word.length) {
            if (VOWELS.contains(word[i - 1])) {
                r2Start = i + 1
                break
            }
        }
        if (r2Start == 0 && rvStart <= word.length) r2Start = word.length

        return Pair(rvStart until word.length, r2Start until word.length)
    }

    private fun removeFromEnd(word: String, region: IntRange, vararg endings: String): String {
        for (ending in endings) {
            if (word.endsWith(ending) && (word.length - ending.length) in region.first..region.last) {
                return word.substring(0, word.length - ending.length)
            }
        }
        return word
    }
}