package dev.flsrg.bot.roleplay

import dev.flsrg.bot.roleplay.LanguageDetector.detectLanguage
import dev.flsrg.bot.roleplay.LanguageDetector.Language.RU
import dev.flsrg.bot.roleplay.LanguageDetector.Language.EN
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageDetectorTest {
    // Pure language cases
    @Test fun `detect pure russian`() = assertEquals(RU, detectLanguage("Привет, как дела?"))
    @Test fun `detect pure english`() = assertEquals(EN, detectLanguage("Hello world"))
    @Test fun `detect russian with numbers`() = assertEquals(RU, detectLanguage("Стоимость 100 рублей"))
    @Test fun `detect english with numbers`() = assertEquals(EN, detectLanguage("Price 100 dollars"))

    // Mixed language cases
    @Test fun `detect mixed with russian dominant`() = assertEquals(RU, detectLanguage("Привет hello"))
    @Test fun `detect mixed with english dominant`() = assertEquals(EN, detectLanguage("Hello привет"))
    @Test fun `balanced mixed text defaults to english`() = assertEquals(EN, detectLanguage("Hello привет 123"))

    // Edge cases
    @Test fun `empty string defaults to english`() = assertEquals(EN, detectLanguage(""))
    @Test fun `single cyrillic char`() = assertEquals(RU, detectLanguage("я"))
    @Test fun `single latin char`() = assertEquals(EN, detectLanguage("a"))
    @Test fun `special characters only`() = assertEquals(EN, detectLanguage("!@#$%^&*()"))
    @Test fun `numbers only`() = assertEquals(EN, detectLanguage("123456"))

    // Code-like text
    @Test fun `code with russian comments`() = assertEquals(RU, detectLanguage("// Это комментарий\nval x = 1"))
    @Test fun `code with english comments`() = assertEquals(EN, detectLanguage("// This is comment\nval x = 1"))
    @Test fun `code without comments`() = assertEquals(EN, detectLanguage("fun main() { println() }"))

    // Explicit markers
    @Test fun `explicit russian marker`() = assertEquals(RU, detectLanguage("lang=ruТекст"))
    @Test fun `explicit english marker`() = assertEquals(EN, detectLanguage("lang=enText"))
    @Test fun `marker overrides content`() = assertEquals(RU, detectLanguage("lang=ruHello"))
    @Test fun `marker overrides content 2`() = assertEquals(EN, detectLanguage("lang=enПривет"))

    // Real-world cases
    @Test fun `russian with english terms`() = assertEquals(RU, detectLanguage("Настройте Kubernetes кластер"))
    @Test fun `english with russian terms`() = assertEquals(EN, detectLanguage("Configure кластер Kubernetes"))
    @Test fun `tech documentation style`() = assertEquals(RU, detectLanguage("API возвращает JSON-объект"))

    // Threshold testing
    @Test fun `just below russian threshold`() = assertEquals(EN, detectLanguage("Прив hello world"))
    @Test fun `just above russian threshold`() = assertEquals(RU, detectLanguage("Привет hello"))

    @Test fun `performance with long text`() {
        val longText = "Привет " + "hello ".repeat(10_000)
        measureTimeMillis {
            detectLanguage(longText)
        }.also { println("Detection took $it ms") }
    }
}   