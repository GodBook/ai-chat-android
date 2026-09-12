package com.example.aichat.ui.export

internal object BlockExportFormatter {
    private val extensions = mapOf(
        "python" to "py", "py" to "py", "kotlin" to "kt", "kt" to "kt", "java" to "java",
        "javascript" to "js", "js" to "js", "typescript" to "ts", "ts" to "ts", "tsx" to "tsx", "jsx" to "jsx",
        "json" to "json", "sql" to "sql", "html" to "html", "css" to "css", "xml" to "xml",
        "yaml" to "yaml", "yml" to "yml", "bash" to "sh", "shell" to "sh", "sh" to "sh",
        "powershell" to "ps1", "go" to "go", "rust" to "rs", "rs" to "rs", "c" to "c", "cpp" to "cpp",
        "c++" to "cpp", "csharp" to "cs", "c#" to "cs", "swift" to "swift", "dart" to "dart",
        "markdown" to "md", "md" to "md", "text" to "txt", "plaintext" to "txt", "csv" to "csv",
    )
    fun codeFilename(language: String?): String {
        val token = language?.trim()?.substringBefore(' ')?.lowercase()
        return "code.${extensions[token] ?: "txt"}"
    }

    /** Avoid spreadsheet formula evaluation when pasting/exporting model-generated cells. */
    private fun literalCell(value: String): String {
        val first = value.trimStart().firstOrNull()
        val numeric = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?").matches(value.trim())
        return if ((!numeric && first in listOf('=', '+', '-', '@')) || value.startsWith('\t') || value.startsWith('\r')) "'$value" else value
    }
    fun csv(rows: List<List<String>>): String = "\uFEFF" + delimited(rows, ',')
    fun tsv(rows: List<List<String>>): String = delimited(rows, '\t')
    private fun delimited(rows: List<List<String>>, separator: Char): String {
        val columns = rows.maxOfOrNull { it.size } ?: return ""
        return rows.joinToString("\r\n", postfix = if (rows.isEmpty()) "" else "\r\n") { row ->
            (0 until columns).joinToString(separator.toString()) { index ->
                val cell = literalCell(row.getOrElse(index) { "" })
                if (cell.any { it == separator || it == '"' || it == '\r' || it == '\n' }) "\"${cell.replace("\"", "\"\"")}\"" else cell
            }
        }
    }
}
