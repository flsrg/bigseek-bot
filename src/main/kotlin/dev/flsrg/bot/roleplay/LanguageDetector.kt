package dev.flsrg.bot.roleplay

import dev.flsrg.bot.uitls.MessageHelper
import java.util.Locale

object LanguageDetector {
    enum class Language(val locale: Locale) {
        EN(Locale.ENGLISH),
        RU(Locale.of("ru"))
    }

    private val enWords = mutableSetOf("the", "and", "for", "you", "are", "hello").also {
        it.addAll(MessageHelper.EN_THINKING_PREFIX)
    }
    private val ruWords = mutableSetOf("и", "в", "не", "на", "что", "привет").also {
        it.addAll(MessageHelper.RU_THINKING_PREFIX)
    }

    fun detectLanguage(text: String): Language {
        if (text.isBlank()) return Language.EN // Default

        // Weighted scoring
        val cyrillicRange = '\u0400'..'\u04FF'
        val hasCyrillic = text.any { it in cyrillicRange }
        val hasLatin = text.any { it.isLatin() }

        return when {
            // Rule 1: Explicit language markers
            text.contains("[lang=ru]") -> Language.RU
            text.contains("[lang=en]") -> Language.EN
            // Rule 2: Combined scoring
            hasCyrillic && !hasLatin -> Language.RU
            hasLatin && !hasCyrillic -> Language.EN

            // Rule 3: Mixed content analysis
            else -> {
                val cyrillicCount = text.count { it in cyrillicRange }
                val latinCount = text.count { it.isLatin() }

                when {
                    cyrillicCount > latinCount * 1.5 -> Language.RU
                    latinCount > cyrillicCount * 1.5 -> Language.EN
                    else -> detectWithCommonWords(text) // Fallback
                }
            }
        }
    }

    private fun Char.isLatin() = this in 'a'..'z' || this in 'A'..'Z'

    private fun detectWithCommonWords(text: String): Language {
        val ruScore = ruWords.count { text.lowercase().containsWord(it) }
        val enScore = enWords.count { text.lowercase().containsWord(it) }

        return if (ruScore > enScore) Language.RU else Language.EN
    }

    fun String.containsWord(word: String): Boolean {
        return this.contains(Regex("\\b${Regex.escape(word)}\\b"))
    }
}