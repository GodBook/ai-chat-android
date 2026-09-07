package com.example.aichat.background

import android.graphics.Bitmap
import com.example.aichat.AiChatApplication
import com.example.aichat.data.model.DEFAULT_SCREENSHOT_PROMPT
import com.example.aichat.data.model.MessageStatus
import com.example.aichat.data.network.ChatClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Saves a captured bitmap, sends it in a new chat, and returns the completed answer. */
class ScreenshotQuestionProcessor(private val app: AiChatApplication) {
    suspend fun process(
        bitmap: Bitmap,
        prompt: String,
        shortAnswerModeEnabled: Boolean = false,
    ): String {
        var imagePath: String? = null
        var requestWasPersisted = false
        var sendStarted = false
        try {
            val savedImagePath = try {
                app.container.imageFileStore.saveScreenshot(bitmap)
            } finally {
                bitmap.recycle()
            }
            imagePath = savedImagePath
            val conversation = app.container.chatRepository.createConversation("截屏问答")
            val question = prompt.trim().ifEmpty { DEFAULT_SCREENSHOT_PROMPT } +
                if (shortAnswerModeEnabled) {
                    "\n若图片是选择题或判断题，请在完整回答最后单独输出机器标记：单选题用 [简答:A]（只填一个 A/B/C/D）；" +
                        "多选题用 [简答:AB]（把所有正确选项的字母连在一起，如 AB、ACD，按 A 到 D 的顺序排列）；" +
                        "判断题用 [简答:正确] 或 [简答:错误]。若不是这三类题目，不输出该标记。"
                } else {
                    ""
                }
            sendStarted = true
            val assistantId = try {
                app.container.chatRepository.sendMessage(
                    conversation.id,
                    question,
                    listOf(savedImagePath),
                )
            } catch (failure: ChatClientException) {
                requestWasPersisted = failure.requestWasPersisted
                throw failure
            }
            requestWasPersisted = true
            val message = app.container.chatRepository.observeMessages(conversation.id).first { messages ->
                messages.any {
                    it.id == assistantId && it.status in setOf(
                        MessageStatus.SENT,
                        MessageStatus.FAILED,
                        MessageStatus.INTERRUPTED,
                    )
                }
            }.firstOrNull { it.id == assistantId }
            if (message != null && message.status != MessageStatus.SENT) {
                throw IllegalStateException(message.errorMessage ?: "AI 截屏问答失败")
            }
            return message?.text?.trim().orEmpty().ifEmpty { "AI 没有返回可显示的内容" }
        } catch (cancelled: CancellationException) {
            if (!sendStarted && !requestWasPersisted) {
                imagePath?.let { runCatching { app.container.imageFileStore.delete(it) } }
            }
            throw cancelled
        } catch (failure: Throwable) {
            if (!requestWasPersisted) {
                imagePath?.let { runCatching { app.container.imageFileStore.delete(it) } }
            }
            throw failure
        }
    }
}

/** Compact answer extracted from a screenshot answer, when it is unambiguous. */
sealed interface ShortAnswerIndicator {
    /**
     * A choice answer. Indices are zero based (A = 0). Several indices describe a multiple choice
     * question and light up every selected column of the compact indicator at the same time.
     */
    data class Choice(val options: Set<Int>) : ShortAnswerIndicator {
        constructor(vararg options: Int) : this(options.toSortedSet())
    }

    data class Judgment(val correct: Boolean) : ShortAnswerIndicator
}

private val JUDGMENT_WORDS = setOf("正确", "错误", "对", "错", "是", "否", "true", "false")
private val JUDGMENT_TRUE_WORDS = setOf("正确", "对", "是", "true")

/** Machine marker appended by the model when the compact mode is on. */
private val MARKED_SHORT_ANSWER = Regex(
    "(?i)\\[简答\\s*[:：]\\s*(正确|错误|对|错|是|否|true|false|[a-h](?:\\s*[、,，/和与及]?\\s*[a-h])*)\\]",
)

private val JUDGMENT_LABELED = Regex(
    "(?i)(?:正确答案|答案|结论|判断)\\s*(?:是|为|选)?\\s*[:：]?\\s*(正确|错误|对|错|true|false)",
)
private val JUDGMENT_YES_NO = Regex("(?i)(?:正确答案|答案|结论|判断)\\s*[:：]\\s*(是|否)")
private val JUDGMENT_BARE = Regex(
    "(?i)(?:^|[\\n。.!！])\\s*(正确|错误|对|错|是|否|true|false)\\s*(?:[。.!！]|$)",
)

/** One or more option letters introduced by an answer label, e.g. "答案：AB" or "选 A、C 和 D". */
private val CHOICE_LABELED = Regex(
    "(?i)(?:正确选项|正确答案|答案|选择|选项|选)\\s*(?:是|为|选)?\\s*[:：]?\\s*(?:选项\\s*)?" +
        "([a-h](?:\\s*[、,，/和与及]?\\s*[a-h])*)",
)

/** A bare leading option such as "D. 这是解析", used only when nothing else matched. */
private val CHOICE_BARE = Regex(
    "(?i)(?:^|[\\n])\\s*[（(]?([abcd])(?:[)）.]|[：:、。](?=\\s|$)|$)",
)

/** Phrases that mean the options listed after the answer are the wrong ones. */
private val DISTRACTOR_HINT = Regex("不(?:对|正确|成立|符合|是|选)|错误|排除|而非")
private const val DISTRACTOR_LOOKAHEAD = 10

fun extractShortAnswerIndicator(answer: String): ShortAnswerIndicator? {
    val text = answer
        .replace('\uFF21', 'A').replace('\uFF22', 'B').replace('\uFF23', 'C').replace('\uFF24', 'D')
        .replace('\uFF25', 'E').replace('\uFF26', 'F')
        .replace("**", "")
        .trim()
    if (text.isEmpty()) return null
    val marked = MARKED_SHORT_ANSWER.find(text)
    if (marked != null) {
        val value = marked.groupValues[1].lowercase()
        if (value in JUDGMENT_WORDS) {
            return ShortAnswerIndicator.Judgment(value in JUDGMENT_TRUE_WORDS)
        }
        val markedOptions = parseOptionLetters(value)
        if (markedOptions.isNotEmpty()) return ShortAnswerIndicator.Choice(markedOptions)
    }
    val judgment = JUDGMENT_LABELED.find(text)
        ?: JUDGMENT_YES_NO.find(text)
        ?: JUDGMENT_BARE.find(text)
    if (judgment != null) {
        val value = judgment.groupValues.last().lowercase()
        return ShortAnswerIndicator.Judgment(value in JUDGMENT_TRUE_WORDS)
    }
    val labeled = CHOICE_LABELED.find(text)
    if (labeled != null) {
        val options = parseOptionLetters(labeled.groupValues[1])
        if (options.isNotEmpty()) {
            // "答案：A、B 都不对" lists distractors after the real answer, so keep only the first
            // letter instead of showing every option that was mentioned next to the answer.
            val kept = if (options.size > 1 && mentionsDistractorAfter(text, labeled.range.last + 1)) {
                setOf(options.first())
            } else {
                options
            }
            return ShortAnswerIndicator.Choice(kept)
        }
    }
    val bare = CHOICE_BARE.find(text)
    return bare?.groupValues?.last()?.lowercase()?.firstOrNull()?.let {
        ShortAnswerIndicator.Choice(it - 'a')
    }
}

/** Reads every option letter in [value] as a zero based index, sorted from A onwards. */
private fun parseOptionLetters(value: String): Set<Int> = value.mapNotNull { char ->
    val lower = char.lowercaseChar()
    if (lower in 'a'..'h') lower - 'a' else null
}.toSortedSet()

private fun mentionsDistractorAfter(text: String, startIndex: Int): Boolean {
    if (startIndex >= text.length) return false
    val window = text.substring(startIndex, minOf(startIndex + DISTRACTOR_LOOKAHEAD, text.length))
    val boundary = window.indexOfAny(charArrayOf('\n', '.', '。', '!', '！', ';', '；'))
    val scope = if (boundary >= 0) window.substring(0, boundary) else window
    return DISTRACTOR_HINT.containsMatchIn(scope)
}
