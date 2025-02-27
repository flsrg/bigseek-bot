package dev.flsrg.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val refusal: String? = null
)

@Serializable
data class ChatRequest(
    val model: String,
    @SerialName("chain_of_thought")
    val chainOfThought: Boolean = true,
    val stream: Boolean = false,
    val messages: List<ChatMessage>
)

@Serializable
data class ChatChoice(
    val index: Int,
    val message: ChatMessage
)

@Serializable
data class ChatResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val choices: List<ChatChoice>
)