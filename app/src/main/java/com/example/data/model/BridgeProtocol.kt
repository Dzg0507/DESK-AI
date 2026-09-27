package com.example.data.model

enum class BridgeProtocol(val displayName: String, val defaultPort: Int, val description: String) {
    ALWAYSON_AGENT(
        displayName = "AlwaysOnAgent Web HUD (:8080)",
        defaultPort = 8080,
        description = "Direct connection to your AlwaysOnAgent Python worker daemon on port 8080"
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI Compatible / LM Studio (:1234)",
        defaultPort = 1234,
        description = "Standard OpenAI format endpoint used by LM Studio, LocalAI, vLLM & Ollama"
    ),
    OLLAMA_NATIVE(
        displayName = "Ollama Native API (:11434)",
        defaultPort = 11434,
        description = "Direct Ollama HTTP API (/api/chat) on your computer"
    )
}
