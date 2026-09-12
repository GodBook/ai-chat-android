package com.example.aichat.data.model

data class MessageSearchFilter(val conversationId: String? = null, val role: String? = null, val days: Int = 0)
data class MessageSearchCursor(val createdAt: Long, val id: String)
fun literalSearchPattern(keyword: String): String = "%" + keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
