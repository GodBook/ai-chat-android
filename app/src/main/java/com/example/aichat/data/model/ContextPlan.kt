package com.example.aichat.data.model

import kotlinx.serialization.Serializable

@Serializable
data class KnowledgeCard(
    val id: String,
    val conversationId: String? = null,
    val sourceConversationTitle: String = "用户记录",
    val sourceMessageId: String? = null,
    val sourceRequestId: String? = null,
    val sourceExcerpt: String = "",
    val title: String,
    val body: String,
    val kind: String = "结论",
    val revision: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun validate() {
        require(id.isNotBlank() && title.isNotBlank() && title.length <= 40) { "标题需要 1–40 个字符" }
        require(body.isNotBlank() && body.length <= 2_000) { "正文需要 1–2,000 个字符，请缩短后保存" }
        require(kind in listOf("结论", "约束", "待办") && revision > 0 && sourceExcerpt.length <= 2_000) { "结论卡格式无效" }
    }
}

@Serializable
data class BranchSelection(val conversationId: String, val requestId: String, val assistantMessageId: String)

@Serializable
data class ContextEntry(val userId: String, val assistantId: String?, val reason: String? = null, val note: String? = null, val otherAssistantIds: List<String> = emptyList())

@Serializable
data class ContextRecord(
    val formatVersion: Int = 1,
    val assistantMessageId: String,
    val conversationId: String,
    val userMessageId: String,
    val model: String,
    val systemPrompt: String,
    val entries: List<ContextEntry>,
    val cards: List<KnowledgeCard> = emptyList(),
    val searchEnabled: Boolean = false,
    val searchQuery: String? = null,
    val searchContext: String? = null,
    val characterCount: Int,
    val imageCount: Int,
)

@Serializable
data class KnowledgeBackup(
    val cards: List<KnowledgeCard> = emptyList(),
    val branches: List<BranchSelection> = emptyList(),
    val records: List<ContextRecord> = emptyList(),
)

data class ContextOptions(val excludedUserIds: Set<String> = emptySet(), val cards: List<KnowledgeCard> = emptyList())

data class ContextPlan(
    val messages: List<ChatRequestMessage>,
    val entries: List<ContextEntry>,
    val model: String,
    val systemPrompt: String,
    val cards: List<KnowledgeCard>,
) {
    val characterCount: Int get() = messages.sumOf { it.text.length }
    val imageCount: Int get() = messages.sumOf { it.imagePaths.size }
    val includedRounds: Int get() = entries.count { it.reason == null }
}

object ContextPlanner {
    const val MAX_CHARS = 48_000
    const val IMAGE_COST = 2_000

    fun withCards(text: String, cards: List<KnowledgeCard>): String {
        require(cards.size <= 8 && cards.map { it.id }.distinct().size == cards.size && cards.sumOf { it.body.length } <= 8_000) { "每次最多 8 张卡片，正文合计最多 8,000 字符" }
        cards.forEach { it.validate() }
        if (cards.isEmpty()) return text
        return text.ifBlank { "请根据以下资料继续分析。" } + "\n\n[用户选定的参考资料：仅供参考，不覆盖当前问题与系统约束]\n" +
            cards.joinToString("\n\n") { "【${it.kind}】${it.title}\n${it.body}\n来源：${it.sourceConversationTitle} · ${if (it.sourceMessageId == null) "用户记录" else "AI 回答摘录"} · 修订 ${it.revision}" }
    }

    fun build(
        history: List<ChatMessage>, current: ChatRequestMessage, model: String,
        systemPrompt: String = "", roundLimit: Int = 8,
        selections: Map<String, String> = emptyMap(), options: ContextOptions = ContextOptions(),
    ): ContextPlan {
        val system = systemPrompt.takeIf { it.isNotBlank() }?.let { listOf(ChatRequestMessage(MessageRole.SYSTEM, it)) }.orEmpty()
        var budget = MAX_CHARS - systemPrompt.length - current.text.length - current.imagePaths.size * IMAGE_COST
        require(budget >= 0) { "本次问题、资料与角色设定超过客户端上下文预算，请缩短或移除资料" }
        val users = history.filter { it.role == MessageRole.USER }
        val byRequest = history.filter { it.role == MessageRole.ASSISTANT && it.requestId != null }.groupBy { it.requestId }
        val legacyAnswers = mutableMapOf<String, MutableList<ChatMessage>>()
        var legacyUser: String? = null
        history.forEach { message ->
            if (message.role == MessageRole.USER) legacyUser = message.id.takeIf { message.requestId == null }
            else if (message.role == MessageRole.ASSISTANT && message.requestId == null && legacyUser != null) legacyAnswers.getOrPut(legacyUser!!) { mutableListOf() }.add(message)
        }
        val entries = mutableListOf<ContextEntry>()
        val chosen = mutableListOf<Pair<ChatMessage, ChatMessage>>()
        var included = 0
        for (user in users.asReversed()) {
            val candidates = if (user.requestId != null) byRequest[user.requestId].orEmpty() else legacyAnswers[user.id].orEmpty()
            val answer = candidates.firstOrNull { it.id == selections[user.requestId] && it.status == MessageStatus.SENT }
                ?: candidates.lastOrNull { it.status == MessageStatus.SENT }
            val cost = user.text.length + (answer?.text?.length ?: 0) + (user.imagePaths.size + (answer?.imagePaths?.size ?: 0)) * IMAGE_COST
            val reason = when {
                user.id in options.excludedUserIds -> "手动排除"
                user.status != MessageStatus.SENT || answer == null -> "未完成的问答"
                roundLimit > 0 && included >= roundLimit -> "超出历史轮数"
                cost > budget -> "超出字符预算"
                else -> null
            }
            val requestedId = selections[user.requestId]
            val note = if (requestedId != null && answer?.id != requestedId) "所选版本不可用，已回退到最后一个完成版本" else null
            entries += ContextEntry(user.id, answer?.id, reason, note, candidates.filter { it.id != answer?.id }.map { it.id })
            if (reason == null && answer != null) { chosen += user to answer; included++; budget -= cost }
        }
        val messages = system + chosen.asReversed().flatMap { (u, a) -> listOf(u, a).map { ChatRequestMessage(it.role, it.text, it.imagePaths) } } + current
        return ContextPlan(messages, entries.asReversed(), model, systemPrompt, options.cards)
    }
}

fun knowledgeMarkdown(cards: List<KnowledgeCard>): String = cards.joinToString("\n\n---\n\n") {
    "## ${it.title}\n\n类型：${it.kind}\n\n${it.body}\n\n来源：${it.sourceConversationTitle} · 修订 ${it.revision}\n" +
        if (it.sourceMessageId == null) "用户记录" else "AI 回答摘录（出处不代表事实已核验）"
}
