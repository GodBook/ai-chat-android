package com.example.aichat.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortAnswerIndicatorTest {
    @Test
    fun extractsChoiceFromCommonAnswerLabels() {
        assertEquals(ShortAnswerIndicator.Choice(2), extractShortAnswerIndicator("答案是 C，因为它符合题意。"))
        assertEquals(ShortAnswerIndicator.Choice(1), extractShortAnswerIndicator("正确选项：B"))
        assertEquals(ShortAnswerIndicator.Choice(3), extractShortAnswerIndicator("D. 这是解析"))
    }

    @Test
    fun extractsJudgmentAndMapsCorrectToLeft() {
        assertEquals(ShortAnswerIndicator.Judgment(true), extractShortAnswerIndicator("答案为：正确。"))
        assertEquals(ShortAnswerIndicator.Judgment(false), extractShortAnswerIndicator("错误。理由如下"))
    }

    @Test
    fun ignoresUnclearOrUnrelatedAnswers() {
        assertNull(extractShortAnswerIndicator("这道题需要结合上下文分析。"))
        assertNull(extractShortAnswerIndicator("A 和 B 都可能，无法确定。"))
    }
}
