package dev.flsrg.bot.uitls

object MarkdownHelper {
    private val markdownSpecialChars = setOf(
        '_', '*', '[', ']', '(', ')', '~', '`', '>',
        '#', '+', '-', '=', '|', '{', '}', '.', '!'
    )

    fun formatMessage(rawText: String): String {
        val codeBlockRegex = """```(\w+)?\s*([\s\S]*?)```""".toRegex()
        val stringBuilder = StringBuilder()
        var lastIndex = 0

        codeBlockRegex.findAll(rawText).forEach { match ->
            // Add escaped text before code block
            stringBuilder.append(escapeMarkdown(rawText.substring(lastIndex, match.range.start)))

            // Process code block
            val (language, code) = match.destructured
            val escapedCode = code.replace("\\", "\\\\").replace("`", "\\`")
            stringBuilder.append("```${language}\n${escapedCode}\n```")

            lastIndex = match.range.endInclusive + 1
        }

        // Add remaining text after last code block
        if (lastIndex < rawText.length) {
            stringBuilder.append(escapeMarkdown(rawText.substring(lastIndex)))
        }

        return stringBuilder.toString()
    }

    private fun escapeMarkdown(text: String): String {
        return text.map { c ->
            if (markdownSpecialChars.contains(c)) "\\$c" else c
        }.joinToString("")
    }
}