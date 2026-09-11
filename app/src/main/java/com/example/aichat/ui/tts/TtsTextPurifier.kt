package com.example.aichat.ui.tts

object TtsTextPurifier {
    /**
     * 将 AI 生成的 Markdown / LaTeX / 带有思考链的富文本清洗为适合 TTS 朗读的纯净自然文本
     */
    fun purify(rawText: String, stripThinking: Boolean = true): String {
        var text = rawText

        // 1. 彻底剔除思考链标签 <think>...</think>
        if (stripThinking) {
            text = text.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
        }

        // 2. 替换代码块 ```lang ... ``` 为友好提示
        text = text.replace(Regex("```([a-zA-Z0-9_-]*)[\\s\\S]*?```")) { match ->
            val lang = match.groupValues.getOrNull(1)?.trim()
            if (!lang.isNullOrEmpty()) {
                "此处包含一段 $lang 代码，已为您略过。"
            } else {
                "此处包含代码块，已为您略过。"
            }
        }

        // 3. 替换块级 LaTeX 公式
        text = text.replace(Regex("\\$\\$[\\s\\S]*?\\$\\$"), "此处包含数学公式。")
        text = text.replace(Regex("\\\\\\[[\\s\\S]*?\\\\\\]"), "此处包含数学公式。")

        // 4. 行内公式 $...$ 或 \(...\) 简单剥离符号
        text = text.replace(Regex("\\$([^$]+)\\$"), "$1")
        text = text.replace(Regex("\\\\\\((.*?)\\\\\\)"), "$1")

        // 5. 清除来源引用 [来源 1]、[来源 1, 2]、[1] 等
        text = text.replace(Regex("\\[(?:来源\\s*)?\\d+(?:\\s*[,，、]\\s*\\d+)*\\]"), "")

        // 6. 清理 Markdown 链接 [title](url) -> title 与图片 ![alt](url) -> ""
        text = text.replace(Regex("!\\[.*?\\]\\(.*?\\)"), "")
        text = text.replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")

        // 7. 清除行内反引号 `code` -> code
        text = text.replace(Regex("`([^`]+)`"), "$1")

        // 8. 清理 Markdown 标题符号 #, ##, ### 等
        text = text.replace(Regex("(?m)^#{1,6}\\s*"), "")

        // 9. 清理 Markdown 引用符 >
        text = text.replace(Regex("(?m)^>\\s*"), "")

        // 10. 清理 Markdown 分割线 --- 或 ***
        text = text.replace(Regex("(?m)^[-*_]{3,}\\s*$"), "")

        // 11. 清理粗体斜体与删除线 **bold**, *italic*, ~~del~~
        text = text.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        text = text.replace(Regex("\\*(.*?)\\*"), "$1")
        text = text.replace(Regex("__(.*?)__"), "$1")
        text = text.replace(Regex("_(.*?)_"), "$1")
        text = text.replace(Regex("~~(.*?)~~"), "$1")

        // 12. 清理列表符号 - * + 1. 等
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        text = text.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")

        // 13. 合并多余空白行
        text = text.replace(Regex("\\n{2,}"), "\n")

        return text.trim()
    }

    private const val MAX_SENTENCE_LENGTH = 150

    /**
     * 将长文本切分为自然句子，便于分段朗读与高亮跟随
     */
    fun splitIntoSentences(purifiedText: String): List<String> {
        if (purifiedText.isBlank()) return emptyList()

        val sentenceDelimiters = Regex("(?<=[。！？!?；;\n])")
        val rawSentences = purifiedText
            .split(sentenceDelimiters)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<String>()
        for (sentence in rawSentences) {
            if (sentence.length <= MAX_SENTENCE_LENGTH) {
                result.add(sentence)
            } else {
                var remaining = sentence
                while (remaining.length > MAX_SENTENCE_LENGTH) {
                    result.add(remaining.take(MAX_SENTENCE_LENGTH))
                    remaining = remaining.drop(MAX_SENTENCE_LENGTH)
                }
                if (remaining.isNotEmpty()) {
                    result.add(remaining)
                }
            }
        }
        return result
    }
}
