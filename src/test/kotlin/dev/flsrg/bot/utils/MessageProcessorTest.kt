import dev.flsrg.bot.Bot
import dev.flsrg.bot.uitls.MessageProcessor
import dev.flsrg.llmpollingclient.client.OpenRouterClient
import dev.flsrg.llmpollingclient.client.OpenRouterClient.ChatResponse
import dev.flsrg.llmpollingclient.client.OpenRouterClient.StreamChoice
import dev.flsrg.llmpollingclient.client.OpenRouterClient.Delta
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MessageProcessorTest {

    @MockK
    private lateinit var bot: Bot

    private val chatId = "testChatId"

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        coEvery<Int> { bot.updateOrSendMessage(any(), any(), any(), any()) } returns 123
        coEvery { bot.execute(any()) } just Runs
        coEvery { bot.deleteAllReasoningMessages(any()) } just Runs
        BotConfig.MESSAGE_MAX_LENGTH = 10
    }

    @Test
    fun `processMessage with reasoning appends to reasoning buffer`() = runTest {
        val processor = MessageProcessor(bot, chatId)
        val message = ChatResponse(
            choices = listOf(
                StreamChoice(delta = Delta(reasoning = "test reasoning"))
            ),
            provider = "",
        )

        processor.processMessage(message)

        assertEquals("test reasoning", processor.reasoningBuffer.toString())
        assertTrue(processor.contentBuffer.isEmpty())
    }

    @Test
    fun `processMessage with content appends to content and fullContent buffers`() = runTest {
        val processor = MessageProcessor(bot, chatId)
        val message = ChatResponse(
            choices = listOf(
                ChatChoice(delta = ChatDelta(content = "test content"))
            ),
            provider = "",
        )

        processor.processMessage(message)

        assertEquals("test content", processor.contentBuffer.toString())
        assertEquals("test content", processor.fullContent.toString())
    }

    @Test
    fun `processMessage with both reasoningand content appends only reasoning`() = runTest {
        val processor = MessageProcessor(bot, chatId)
        val message = ChatResponse(
            choices = listOf(
                ChatChoice(delta = ChatDelta(reasoning = "reasoning", content = "content"))
            ),
            provider = "",
        )

        processor.processMessage(message)

        assertEquals("reasoning", processor.reasoningBuffer.toString())
        assertTrue(processor.contentBuffer.isEmpty())
        assertTrue(processor.fullContent.isEmpty())
    }

    @Test
    fun `processMessage exceeding reasoning buffer sends message and clears buffer`() = runTest {
        val processor = MessageProcessor(bot, chatId)
        val message = ChatResponse(
            choices = listOf(
                ChatChoice(delta = ChatDelta(reasoning = "12345678901")) // 11 characters
            ),
            provider = "",
        )

        processor.processMessage(message)

        coVerify {
            bot.updateOrSendMessage(
                message = "12345678901",
                existingMessageId = null,
                parseMode = null,
                keyboardButtons = null
            )
        }
        assertTrue(processor.reasoningBuffer.isEmpty())
        assertTrue(processor.reasoningMessageIds.contains(null))
    }

    @Test
    fun `processMessage exceeding content buffer sends message and clears buffer`() = runTest {
        val processor = MessageProcessor(bot, chatId)
        val message = ChatResponse(
            choices = listOf(
                ChatChoice(delta = ChatDelta(content = "12345678901")) // 11 characters
            ),
            provider = "",
        )

        processor.processMessage(message)

        coVerify {
            bot.updateOrSendMessage(
                message = MarkdownHelper.formatMessage("12345678901"),
                existingMessageId = null,
                parseMode = ParseMode.MARKDOWN,
                keyboardButtons = null
            )
        }
        assertTrue(processor.contentBuffer.isEmpty())
        assertNull(processor.contentMessageId)
    }

    @Test
    fun `updateOrSend with content sends message and deletes reasoning messages`() = runTest {
        val processor = MessageProcessor(bot, chatId).apply {
            contentBuffer.append("content")
            reasoningMessageIds.addAll(setOf(111, 222))
        }

        processor.updateOrSend(BotUtils.ControlKeyboardButton.BUTTON1)

        coVerify {
            bot.deleteAllReasoningMessages(setOf(111, 222))
            bot.execute(any<SendMessage> { it.text == "Подумал, получается:" })
            bot.updateOrSendMessage(
                message = MarkdownHelper.formatMessage("content"),
                existingMessageId = null,
                parseMode = ParseMode.MARKDOWN,
                keyboardButtons = arrayOf(BotUtils.ControlKeyboardButton.BUTTON1)
            )
        }
        assertEquals(123, processor.contentMessageId)
        assertTrue(processor.reasoningMessageIds.isEmpty())
    }

    @Test
    fun `updateOrSend with reasoning sends message and updates reasoningMessageIds`() = runTest {
        val processor = MessageProcessor(bot, chatId).apply {
            reasoningBuffer.append("reasoning")
        }

        processor.updateOrSend(BotUtils.ControlKeyboardButton.BUTTON2)

        coVerify {
            bot.updateOrSendMessage(
                message = "reasoning",
                existingMessageId = null,
                parseMode = null,
                keyboardButtons = arrayOf(BotUtils.ControlKeyboardButton.BUTTON2)
            )
        }
        assertTrue(processor.reasoningMessageIds.contains(123))
    }

    @Test
    fun `fullContent accumulates all content parts`() = runTest {
        val processor = MessageProcessor(bot, chatId)
        val messages = listOf(
            ChatResponse(choices = listOf(ChatChoice(delta = ChatDelta(content = "123456789")))),
            ChatResponse(choices = listOf(ChatChoice(delta = ChatDelta(content = "01")))),
        )

        messages.forEach {processor.processMessage(it) }

        assertEquals("12345678901", processor.fullContent.toString())
        coVerify(exactly = 1) { bot.updateOrSendMessage(any(), any(), any(), any()) }
    }
}