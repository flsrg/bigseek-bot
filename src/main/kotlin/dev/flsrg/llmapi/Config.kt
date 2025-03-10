package dev.flsrg.llmapi

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object Config {
    const val API_CONNECTION_TIMEOUT_MS = 10 * 60 * 1000L

    val format = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val sreamingClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(format)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = API_CONNECTION_TIMEOUT_MS
        }
        install(SSE)
        engine {
            maxConnectionsCount = 1000
            endpoint {
                maxConnectionsPerRoute = 100
                keepAliveTime = 5000
                pipelineMaxSize = 20
            }
        }
    }

    enum class Model(val id: String) {
        DEEPSEEK_R1("deepseek/deepseek-r1:free")
    }
}