package dev.flsrg.bot.roleplay

import dev.flsrg.bot.roleplay.LanguageDetector.Language

class RoleDetector(private val roles: List<RoleConfig>) {
    fun detectRole(query: String, language: Language? = null): RoleConfig {
        val detectedLanguage = language ?: LanguageDetector.detectLanguage(query)
        val normalizedQuery = query.lowercase()

        val scoredRoles = roles.map { role ->
            val score = calculateScore(normalizedQuery, role, detectedLanguage)
            role to score
        }

        return scoredRoles.maxByOrNull { (_, score) -> score }
            ?.takeIf { (_, score) -> score > 0 }
            ?.first
            ?: defaultRole(detectedLanguage)
    }

    private fun calculateScore(query: String, role: RoleConfig, language: Language): Float {
        val keywords = if (language == Language.RU) role.russianKeywords else role.keywords
        if (keywords.isEmpty()) return 0f

        val keywordMatches = keywords.count { keyword ->
            keyword.lowercase() in query
        }

        val proximityBonus = keywords
            .filter { it.contains(" ") }
            .count { phrase -> phrase in query }
            .toFloat() * 1.5f

        return (keywordMatches + proximityBonus) * role.priority
    }

    private fun defaultRole(language: Language): RoleConfig =
        if (language == Language.RU) RoleConfig.russianFallbackRoles.first()
        else RoleConfig.englishFallbackRoles.first()
}
