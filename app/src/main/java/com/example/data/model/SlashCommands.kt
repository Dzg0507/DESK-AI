package com.example.data.model

/**
 * Every slash command, in ONE place: the pop-up above the chat box (typing /), the Commands list (/? or /help, or
 * the hub's Commands button) and /help's text are all built from it, so they can't drift apart (2026-10-11: the
 * hub's list and /help had drifted, and /task and /restart were missing from both).
 *
 * The app answers most of them itself (AlwaysOnAgentClient.handleSlashCommand); /task and /restart go to the agent
 * (interfaces/hud/chat.py). run = true: a tap sends it as is; false: a tap puts it in the chat box to finish.
 */
data class SlashCommand(
    val category: String,
    val command: String,
    val usage: String,
    val description: String,
    val template: String,
    val run: Boolean = false
)

object SlashCommands {
    /** In the pop-up's order. */
    val categories = listOf("Tasks", "Videos & images", "Memory", "The agent", "Help")

    /** What opens the Commands list instead of being sent. */
    val helpWords = setOf("/?", "/help", "/commands")

    val all = listOf(
        SlashCommand("Tasks", "/task", "/task <what to do>", "Hand it a job to do in the background", "/task "),
        SlashCommand("Tasks", "/retask", "/retask <pick one>", "Run a saved retask again: pick it from the list", "/retask "),
        SlashCommand("Tasks", "/status", "/status", "See what it's working on and how many tasks are waiting", "/status", run = true),
        SlashCommand("Tasks", "/cancel", "/cancel [task id]", "Stop the task that's running, or a queued one by its id", "/cancel "),
        SlashCommand("Tasks", "/retry", "/retry <task id>", "Give a failed task another go", "/retry "),

        SlashCommand("Videos & images", "/render", "/render [chest|bottle] [quote]", "Make a video in the scene you pick (the treasure chest or the message in a bottle), with your quote or one it picks", "/render "),
        SlashCommand("Videos & images", "/video", "/video [quote]", "Make a video (the chest and the bottle take turns), with your quote or one it picks", "/video "),
        SlashCommand("Videos & images", "/tiktok", "/tiktok [chest|bottle] [quote]", "Make a video and post it to TikTok", "/tiktok "),
        SlashCommand("Videos & images", "/image", "/image <description>", "Create an image from your description", "/image "),
        SlashCommand("Videos & images", "/videos", "/videos", "List your latest videos", "/videos", run = true),

        SlashCommand("Memory", "/memory", "/memory", "See your profile and the latest things it remembers", "/memory", run = true),
        SlashCommand("Memory", "/remember", "/remember <something>", "Tell it something to keep in mind for good", "/remember "),
        SlashCommand("Memory", "/forget", "/forget <#number>", "Erase a memory (numbers are shown by /memory)", "/forget #"),

        SlashCommand("The agent", "/wake", "/wake", "Wake it up so it starts on waiting tasks", "/wake", run = true),
        SlashCommand("The agent", "/standby", "/standby", "Pause it: no new tasks until you wake it", "/standby", run = true),
        SlashCommand("The agent", "/restart", "/restart", "Restart it (press send to confirm)", "/restart"),
        SlashCommand("The agent", "/engine", "/engine <auto|antigravity|cloud|ollama>", "Choose what does the background work", "/engine "),
        SlashCommand("The agent", "/hud", "/hud", "Show the server's address", "/hud", run = true),

        SlashCommand("Help", "/?", "/? or /help", "Open this list", "/?", run = true)
    )

    /** /help as text (when it's sent rather than opened, e.g. from an older card). */
    fun helpText(): String = buildString {
        append("### Commands\n")
        append("Shortcuts for the things you do most. Type `/` in the chat box and they pop up as you type; tap one to use it. ")
        append("`/?` or `/help` opens the full list.\n")
        categories.filter { it != "Help" }.forEach { cat ->
            append("\n**$cat**\n")
            all.filter { it.category == cat }.forEach { append("• `${it.usage}`: ${it.description}\n") }
        }
        append("\nYou never need a command: just say what you want in plain words, and it works it out.")
    }
}
