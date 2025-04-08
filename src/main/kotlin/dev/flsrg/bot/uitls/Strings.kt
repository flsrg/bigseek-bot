package dev.flsrg.bot.uitls

import dev.flsrg.bot.roleplay.LanguageDetector

sealed class Strings(
    val ru: String,
    val en: String,
) {
    fun get(lang: LanguageDetector.Language, vararg args: Any): String = when (lang) {
        LanguageDetector.Language.EN -> en.format(*args)
        LanguageDetector.Language.RU -> ru.format(*args)
    }

    object StartMessage : Strings("Го", "Let's go")
    object ThinkingMessage : Strings("Думаю...", "Thinking...")
    object ThinkingCompletedMessage : Strings("Подумал, получается:", "Thought and it's:")
    object ResponseMessage : Strings("Так, ну смотри", "So, well, look")
    object RateLimitMessage : Strings("Превышен лимит запросов. Подожди пока", "Rate limit exceeded. Wait")
    object KeyboardStopText : Strings("🚫 Остановись", "🚫 Stop")
    object KeyboardClearHistoryText : Strings("🧹 Забудь все", "🧹 Clear history")

    object CallbackStopSuccessAnswer : Strings("Остановился!", "Stopped!")
    object CallbackStopNothingRunningAnswer : Strings("Нечего останавливать", "Nothing to stop")
    object CallbackClearHistorySuccessAnswer : Strings("Чисто!", "History cleared!")
    object CallbackClearHistorySuccessMessage : Strings("Бот забыл историю. Давай по новой", "Bot forgot the history. Let's start over")

    object StopErrorUser : Strings("Стою", "I'm stopped")
    object StopErrorNewMessage : Strings("Новое сообщение в чате, так, ща...", "There is a new message in the chat, so...")
}