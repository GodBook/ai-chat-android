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

    @Test
    fun extractsMultipleChoiceFromMachineMarker() {
        assertEquals(ShortAnswerIndicator.Choice(0, 1), extractShortAnswerIndicator("答案选 AB。\n[简答:AB]"))
        assertEquals(ShortAnswerIndicator.Choice(0, 2, 3), extractShortAnswerIndicator("解析……\n[简答:ACD]"))
        assertEquals(ShortAnswerIndicator.Choice(1, 2), extractShortAnswerIndicator("[简答:B、C]"))
        assertEquals(ShortAnswerIndicator.Choice(0), extractShortAnswerIndicator("[简答:A]"))
    }

    @Test
    fun extractsMultipleChoiceFromNaturalAnswer() {
        assertEquals(ShortAnswerIndicator.Choice(0, 1, 3), extractShortAnswerIndicator("答案：ABD"))
        assertEquals(ShortAnswerIndicator.Choice(0, 2, 3), extractShortAnswerIndicator("正确选项：A、C、D"))
        assertEquals(ShortAnswerIndicator.Choice(1, 3), extractShortAnswerIndicator("选 B 和 D。"))
        assertEquals(ShortAnswerIndicator.Choice(0, 1), extractShortAnswerIndicator("答案是 A、B，因为两项都成立。"))
    }

    @Test
    fun keepsSingleChoiceWhenFollowingOptionsAreDistractors() {
        assertEquals(
            ShortAnswerIndicator.Choice(0),
            extractShortAnswerIndicator("答案：A、B 都不对，只有第一项成立。"),
        )
    }
}
