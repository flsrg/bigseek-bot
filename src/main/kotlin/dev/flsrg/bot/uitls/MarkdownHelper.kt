package dev.flsrg.bot.uitls

object MarkdownHelper {
    private val telegramMarkdownEscapeTable = BooleanArray(128).apply {
        set('_'.code, true)
        set('*'.code, true)
        set('['.code, true)
        set(']'.code, true)
        set('('.code, true)
        set(')'.code, true)
        set('~'.code, true)
        set('`'.code, true)
        set('>'.code, true)
        set('#'.code, true)
        set('+'.code, true)
        set('-'.code, true)
        set('='.code, true)
        set('|'.code, true)
        set('{'.code, true)
        set('}'.code, true)
        set('.'.code, true)
        set('!'.code, true)
    }

    fun formatMessageMarkdownV2(input: String): String {
        if (input.isEmpty()) return input

        return buildString(capacity = input.length * 2) {
            for (char in input) {
                // Check if char is in the ASCII range and needs escaping.
                if (char.code < telegramMarkdownEscapeTable.size && telegramMarkdownEscapeTable[char.code]) {
                    append('\\')
                }
                append(char)
            }
        }
    }
}