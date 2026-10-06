package com.example.data.model

/**
 * Something a computer task (AgentComputerUse on the Mini) is waiting on: GET /api/computer/requests.
 * kind: "approve" (Approve/Deny a step that submits, sends, pays or posts), "choice" (pick one of a pop-up's
 * buttons) or "text" (a typed answer). The screenshot shows the window, the spot in question outlined in red.
 */
data class ComputerRequest(
    val id: String,
    val kind: String,
    val question: String,
    val choices: List<String> = emptyList(),
    val url: String? = null,
    val screenshotPath: String? = null,     // e.g. /api/computer/requests/<id>/screenshot
    val created: String? = null
) {
    /** The site in words, e.g. "jobs.example.com". */
    fun site(): String? = url?.substringAfter("//")?.substringBefore("/")?.takeIf { it.isNotBlank() }
}
