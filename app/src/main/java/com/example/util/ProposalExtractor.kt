package com.example.util

object ProposalExtractor {

    /**
     * Extracts all actionable task instructions from assistant replies,
     * including formats used by AlwaysOnAgent Web HUD and Telegram Bridge:
     * - "Suggested tasks:\n• /task <instruction>"
     * - "• `/task <instruction>`"
     * - "🧩 *Suggested task*\n\n<instruction>"
     * - "/task <instruction>"
     */
    fun extractAllInstructions(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        val list = mutableListOf<String>()

        // 1. Matches all lines with `• /task ...`, `• `/task ...``, `- /task ...`, or `/task ...`
        val taskRegex = Regex("""(?:^|\n)\s*(?:[•\-*]\s*)?`?/task\s+([^`\r\n]+)`?""", RegexOption.IGNORE_CASE)
        taskRegex.findAll(content).forEach { match ->
            val instr = match.groupValues[1].trim()
            if (instr.isNotBlank() && instr !in list) {
                list.add(instr)
            }
        }

        // 2. If no /task lines matched, try single extractor
        if (list.isEmpty()) {
            val single = extractInstruction(content)
            if (!single.isNullOrBlank()) {
                list.add(single)
            }
        }

        return list
    }

    /**
     * Extracts single primary task instruction.
     */
    fun extractInstruction(content: String): String? {
        if (content.isBlank()) return null

        // 1. Matches: `• /task ...`, `• `/task ...``, `- /task ...`, or `/task ...`
        val taskRegex = Regex("""(?:^|\n)\s*(?:[•\-*]\s*)?`?/task\s+([^`\r\n]+)`?""", RegexOption.IGNORE_CASE)
        val taskMatch = taskRegex.find(content)
        if (taskMatch != null) {
            val candidate = taskMatch.groupValues[1].trim()
            if (candidate.isNotBlank()) return candidate
        }

        // 2. Format from Telegram bridge / MemoryManager:
        // 🧩 *Suggested task*
        // ...
        // <instruction>
        if (content.contains("Suggested task", ignoreCase = true) || content.contains("Suggested mission", ignoreCase = true)) {
            val lines = content.lines()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("/task ", ignoreCase = true) ||
                    trimmed.startsWith("• /task ", ignoreCase = true) ||
                    trimmed.startsWith("- /task ", ignoreCase = true)) {
                    return trimmed.removePrefix("•").removePrefix("-").trim().removePrefix("/task").trim().removeSurrounding("`")
                }
            }
            if (content.contains("🧩")) {
                val paragraphs = content.split(Regex("""\n\s*\n"""))
                val candidate = paragraphs.lastOrNull {
                    !it.contains("Suggested task", ignoreCase = true) &&
                    !it.contains("AgentWork", ignoreCase = true) &&
                    !it.startsWith("_")
                }?.trim()
                if (!candidate.isNullOrBlank() && candidate.length in 5..400) {
                    return candidate
                }
            }
            // Format: Suggested task: <instruction>
            val prefixRegex = Regex("""(?:Suggested|Proposed)\s+tasks?:\s*(?:[•\-*]\s*)?`?([^`\r\n]+)`?""", RegexOption.IGNORE_CASE)
            val prefixMatch = prefixRegex.find(content)
            if (prefixMatch != null) {
                val candidate = prefixMatch.groupValues[1].trim()
                if (candidate.isNotBlank() && !candidate.startsWith("/task", ignoreCase = true)) {
                    return candidate
                }
            }
        }

        return null
    }

    /**
     * Extracts optional AgentWork project name: `AgentWork project: `my-repo``
     */
    fun extractProject(content: String): String? {
        val projectRegex = Regex("""AgentWork project:\s*`([^`]+)`""", RegexOption.IGNORE_CASE)
        return projectRegex.find(content)?.groupValues?.get(1)?.trim()
    }
}
