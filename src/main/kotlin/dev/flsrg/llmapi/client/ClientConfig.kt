package dev.flsrg.llmapi.client

import dev.flsrg.llmapi.Config

sealed class ClientConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: Config.Model,
    val maxHistoryLength: Int = 20,
    val chainOfThoughts: Boolean = true,
)

class OpenRouterDeepseekConfig(
    baseUrl: String = "https://openrouter.ai/api/v1/chat/completions",
    apiKey: String,
    model: Config.Model = Config.Model.DEEPSEEK_R1,
    maxHistoryLength: Int = 20,
    chainOfThought: Boolean = true,
) : ClientConfig(baseUrl, apiKey, model, maxHistoryLength, chainOfThought)