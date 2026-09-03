package com.example.aichat.data.network

/** Collects SSE data lines into complete events, preserving event boundaries. */
internal class SseParser {
    private val dataLines = mutableListOf<String>()

    fun accept(line: String): List<String> {
        if (line.isEmpty()) return flush()

        val normalized = line.trimStart('\uFEFF', ' ', '\t')
        if (normalized.startsWith(":") || !normalized.startsWith("data:")) return emptyList()

        val value = normalized.removePrefix("data:").let { payload ->
            if (payload.startsWith(" ")) payload.drop(1) else payload
        }
        dataLines += value
        return emptyList()
    }

    fun finish(): List<String> = flush()

    private fun flush(): List<String> {
        if (dataLines.isEmpty()) return emptyList()
        val event = dataLines.joinToString("\n")
        dataLines.clear()
        return listOf(event)
    }
}
