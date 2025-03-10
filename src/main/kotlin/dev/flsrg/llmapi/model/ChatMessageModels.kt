package dev.flsrg.llmapi.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatRequest(
    val model: String,
    @SerialName("chain_of_thought")
    val chainOfThought: Boolean = true,
    val messages: List<ChatMessage>,
    val stream: Boolean = true
)

@Serializable
data class ChatResponse(
    val provider: String,
    val choices: List<StreamChoice>,
)

@Serializable
data class StreamChoice(
    val delta: Delta? = null,
)

@Serializable
data class Delta(
    val content: String? = null,
    val reasoning: String? = null,
)