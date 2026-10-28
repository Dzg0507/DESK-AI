package com.example.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Migrated screens use the design tokens only (2026-10-27, docs/FEED_AND_DESIGN_PLAN_2026-10-27.md): no raw
 * `fontSize = 13.sp` (use DeskTextSize or DeskType) and no `RoundedCornerShape(10.dp)` (use DeskShapes). The app had
 * 371 hand-set sizes in 17 values and 12 radii, which is why it looked unfinished. Add a file here when it's migrated;
 * anything new under ui/today is checked automatically.
 */
class DesignTokensTest {
    private val ui = File("src/main/java/com/example/ui")

    private val migrated = listOf(
        "components/DeskUi.kt", "components/AgentStatusBar.kt", "components/MessageBubble.kt",
        "components/ChatInputBar.kt", "components/MarkdownText.kt", "components/MarkdownMedia.kt",
        "components/RichText.kt", "components/LiveTaskCard.kt", "components/TaskFolding.kt",
        "components/SessionDrawer.kt", "screens/ChatScreen.kt", "dialogs/TasksSheet.kt",
        "dialogs/MediaGallerySheet.kt", "dialogs/MemorySheet.kt", "dialogs/NeedsYouSheet.kt",
        "dialogs/TaskDetailsSheet.kt",
    )
    private val raw = Regex("""fontSize\s*=\s*[0-9.]+\.sp|RoundedCornerShape\(\s*[0-9.]+\.dp\s*\)""")

    @Test
    fun migratedScreensUseTokensOnly() {
        val files = migrated.map { File(ui, it) } +
            (File(ui, "today").takeIf { it.isDirectory }?.walk()?.filter { it.extension == "kt" }?.toList() ?: emptyList())
        val found = files.flatMap { f ->
            assertTrue("missing ${f.path}", f.isFile)
            f.readLines().mapIndexedNotNull { i, line -> raw.find(line)?.let { "${f.name}:${i + 1}  ${it.value}" } }
        }
        assertTrue("Raw sizes or radii in migrated files (use DeskTextSize/DeskType, DeskShapes):\n" +
            found.joinToString("\n"), found.isEmpty())
    }
}
