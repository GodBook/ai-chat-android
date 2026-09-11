package com.example.aichat.data.model

/**
 * 提示词模板与快捷指令模型
 */
data class PromptTemplate(
    val id: String,
    val command: String,
    val title: String,
    val category: String,
    val description: String,
    val template: String,
)

val PROMPT_CATEGORIES: List<String> = listOf(
    "全部",
    "写作表达",
    "编程技术",
    "翻译语言",
    "日常办公",
    "学习思维",
)

val PROMPT_TEMPLATES: List<PromptTemplate> = listOf(
    // 写作表达
    PromptTemplate(
        id = "polish",
        command = "/polish",
        title = "文本润色",
        category = "写作表达",
        description = "优化词句表达与行文流畅度，保持原意不变",
        template = "请帮我润色以下内容，修正错别字与语病，提升文笔表现力与逻辑流畅度，但保留原本的核心观点：\n\n",
    ),
    PromptTemplate(
        id = "summary",
        command = "/summary",
        title = "要点总结",
        category = "写作表达",
        description = "提炼核心要点与关键结论，结构化呈现",
        template = "请对以下内容进行深度提炼与总结，梳理出关键论点、核心结论以及后续行动项，使用分点清晰呈现：\n\n",
    ),
    PromptTemplate(
        id = "expand",
        command = "/expand",
        title = "内容扩写",
        category = "写作表达",
        description = "根据简要大纲或观点丰富细节、论据与案例",
        template = "请基于以下要点或框架进行扩充编写，补充生动的细节、逻辑论据与实际案例，字数适度充实：\n\n",
    ),

    // 编程技术
    PromptTemplate(
        id = "code",
        command = "/code",
        title = "代码编写",
        category = "编程技术",
        description = "根据需求编写高效规范的高质量代码",
        template = "请根据以下功能需求编写代码，要求结构清晰、注释完备、包含必要的错误处理，并给出简单的使用示例：\n\n",
    ),
    PromptTemplate(
        id = "explain",
        command = "/explain",
        title = "代码解读",
        category = "编程技术",
        description = "逐行解析代码执行逻辑、数据流与设计思想",
        template = "请详细分析以下代码片段，解释其核心设计逻辑、算法复杂度以及潜在的边界条件：\n\n",
    ),
    PromptTemplate(
        id = "debug",
        command = "/debug",
        title = "代码排错",
        category = "编程技术",
        description = "定位异常原因，提供修复代码与排查建议",
        template = "以下代码或报错信息遇到了问题，请帮我分析产生 Bug 的根本原因，并给出修改后的可运行代码：\n\n",
    ),
    PromptTemplate(
        id = "refactor",
        command = "/refactor",
        title = "代码重构",
        category = "编程技术",
        description = "改善代码架构设计，提升可读性与执行效率",
        template = "请对以下代码进行重构，遵循 Clean Code 原则与现代最佳实践，优化可读性与性能，并简要列出重构的要点：\n\n",
    ),

    // 翻译语言
    PromptTemplate(
        id = "translate",
        command = "/translate",
        title = "地道翻译",
        category = "翻译语言",
        description = "中英双向翻译，精准传达语境与专业术语",
        template = "请将以下内容翻译为地道自然的中文（若原文为中文则翻译为英文），符合母语使用习惯并准确保留专业术语：\n\n",
    ),
    PromptTemplate(
        id = "grammar",
        command = "/grammar",
        title = "语法纠错",
        category = "翻译语言",
        description = "找出拼写语法问题，并给出更高级的改写参考",
        template = "请检查以下英文文本中的语法、拼写和标点错误，给出修正说明以及 1-2 种更地道自然的表达方式：\n\n",
    ),

    // 日常办公
    PromptTemplate(
        id = "report",
        command = "/report",
        title = "周报生成",
        category = "日常办公",
        description = "将零散的工作记录整理成规范的工作周报",
        template = "请根据以下工作事项，整理一份条理清晰的工作周报，包含【本周主要成果】、【遇到的问题与解决方案】和【下周工作规划】：\n\n",
    ),
    PromptTemplate(
        id = "meeting",
        command = "/meeting",
        title = "会议纪要",
        category = "日常办公",
        description = "整理会议背景、核心讨论、决策及待办责任人",
        template = "请根据以下会议记录或讨论摘要，整理一份专业的会议纪要，包含：1.会议主题与背景 2.核心议题与讨论 3.达成共识与决策 4.后续待办事项（含负责人与截止时间）：\n\n",
    ),
    PromptTemplate(
        id = "email",
        command = "/email",
        title = "商务邮件",
        category = "日常办公",
        description = "撰写得体礼貌、重点突出的专业商务电子邮件",
        template = "请根据以下信息起草一封专业商务邮件，注意语气客气得体、重点清晰、行动诉求明确：\n\n",
    ),

    // 学习思维
    PromptTemplate(
        id = "expert",
        command = "/expert",
        title = "专家咨询",
        category = "学习思维",
        description = "以领域权威专家的视角进行深度咨询解答",
        template = "请作为该领域的资深专家，用严谨系统且深入浅出的方式，全面解答以下问题，并给出可落地的建议：\n\n",
    ),
    PromptTemplate(
        id = "eli5",
        command = "/eli5",
        title = "通俗解释",
        category = "学习思维",
        description = "用大白话或生动比喻解释复杂晦涩的概念",
        template = "请像向一个完全没有背景知识的外行解释一样，用生动形象的比喻和大白话，解释以下概念或现象：\n\n",
    ),
)
