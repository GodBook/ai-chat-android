package com.example.aichat.ui

import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.MessageRole
import com.example.aichat.data.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatExportFormatterTest {
    @Test
    fun `exports text images and failed state`() {
        val output = ChatExportFormatter.format(
            title = "  数学题  ",
            messages = listOf(
                ChatMessage(
                    id = "user",
                    role = MessageRole.USER,
                    text = "请解题",
                    imagePaths = listOf("a.jpg"),
                ),
                ChatMessage(
                    id = "assistant",
                    role = MessageRole.ASSISTANT,
                    text = "无法回答",
                    status = MessageStatus.FAILED,
                    errorMessage = "服务不可用",
                ),
            ),
        )

        assertEquals(
            "数学题\n\n用户\n[图片 1 张]\n请解题\n\nAI\n无法回答\n状态：失败\n说明：服务不可用",
            output,
        )
    }
}
