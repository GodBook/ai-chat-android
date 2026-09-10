package com.example.aichat.ui

import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.DEFAULT_GROUP_NAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAndGroupingTest {

    @Test
    fun `theme fromKey returns correct theme or default fallback`() {
        assertEquals(AppThemeColor.CLASSIC_BLUE, AppThemeColor.fromKey("classic_blue"))
        assertEquals(AppThemeColor.EMERALD_GREEN, AppThemeColor.fromKey("emerald_green"))
        assertEquals(AppThemeColor.CORAL_ORANGE, AppThemeColor.fromKey("coral_orange"))
        assertEquals(AppThemeColor.ELEGANT_PURPLE, AppThemeColor.fromKey("elegant_purple"))
        assertEquals(AppThemeColor.ROSE_PINK, AppThemeColor.fromKey("rose_pink"))
        assertEquals(AppThemeColor.AMBER_GOLD, AppThemeColor.fromKey("amber_gold"))
        assertEquals(AppThemeColor.TEAL_CYAN, AppThemeColor.fromKey("teal_cyan"))
        assertEquals(AppThemeColor.SLATE_CHARCOAL, AppThemeColor.fromKey("slate_charcoal"))

        // Unknown or empty string fallback
        assertEquals(AppThemeColor.CLASSIC_BLUE, AppThemeColor.fromKey("unknown_theme"))
        assertEquals(AppThemeColor.CLASSIC_BLUE, AppThemeColor.fromKey(""))
        assertEquals(AppThemeColor.CLASSIC_BLUE, AppThemeColor.fromKey(null))
    }

    @Test
    fun `theme colors provide distinct display names and colors`() {
        val themes = AppThemeColor.entries
        assertEquals(8, themes.size)
        val labels = themes.map { it.label }.toSet()
        assertEquals(8, labels.size)
        assertTrue(labels.contains("经典蓝"))
        assertTrue(labels.contains("翡翠绿"))
        assertTrue(labels.contains("活力橙"))
        assertTrue(labels.contains("优雅紫"))
        assertTrue(labels.contains("樱花粉"))
        assertTrue(labels.contains("琥珀金"))
        assertTrue(labels.contains("极客青"))
        assertTrue(labels.contains("玄武黑"))
    }

    @Test
    fun `toggle group collapse adds and removes group correctly`() {
        val initial = setOf("工作", "学习")
        val toggled1 = if ("工作" in initial) initial - "工作" else initial + "工作"
        assertFalse("工作" in toggled1)
        assertTrue("学习" in toggled1)

        val toggled2 = if ("生活" in toggled1) toggled1 - "生活" else toggled1 + "生活"
        assertTrue("生活" in toggled2)
        assertTrue("学习" in toggled2)
    }

    @Test
    fun `grouping partitions conversations by group name with default group at the end`() {
        val list = listOf(
            ChatConversation(id = "1", title = "工作任务 1", createdAt = 10L, updatedAt = 20L, groupName = "工作"),
            ChatConversation(id = "2", title = "无分组 1", createdAt = 11L, updatedAt = 21L, groupName = null),
            ChatConversation(id = "3", title = "学习资料", createdAt = 12L, updatedAt = 22L, groupName = "学习"),
            ChatConversation(id = "4", title = "工作任务 2", createdAt = 13L, updatedAt = 23L, groupName = "工作"),
            ChatConversation(id = "5", title = "无分组 2", createdAt = 14L, updatedAt = 24L, groupName = "   "),
        )

        val namedGroups = list
            .mapNotNull { it.groupName?.trim()?.takeIf { g -> g.isNotEmpty() } }
            .distinct()
        val result = mutableListOf<Pair<String, List<ChatConversation>>>()
        namedGroups.forEach { name ->
            result.add(name to list.filter { it.groupName?.trim() == name })
        }
        val ungrouped = list.filter { it.groupName.isNullOrBlank() }
        if (ungrouped.isNotEmpty()) {
            result.add(DEFAULT_GROUP_NAME to ungrouped)
        }

        assertEquals(3, result.size)
        assertEquals("工作", result[0].first)
        assertEquals(listOf("1", "4"), result[0].second.map { it.id })

        assertEquals("学习", result[1].first)
        assertEquals(listOf("3"), result[1].second.map { it.id })

        assertEquals(DEFAULT_GROUP_NAME, result[2].first)
        assertEquals(listOf("2", "5"), result[2].second.map { it.id })
    }
}
