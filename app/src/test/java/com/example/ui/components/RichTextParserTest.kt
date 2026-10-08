package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The RichText parser is plain Kotlin, so these run on the JVM without Robolectric. */
class RichTextParserTest {

    private val sample = """
        [agentwork] Preparing a fresh copy of 'sandbox-site' for job task-077...
        [agentwork] Working in C:\Projects\AgentWork\workspaces\task-077 on branch agent/task-077 with gemini-3.8-flash-high.
        I have completed the task.
        ### Files Changed
        * [readme.md](file:///C:/Projects/AgentWork/workspaces/task-077/readme.md#L154-L157)
        ### Summary of Changes
        * Read and verified the guide ([`docs/X.md`](file:///C:/Projects/AgentWork/workspaces/task-077/docs/X.md))
        * Appended the line `Edited live` to the end of [readme.md](file:///C:/p/readme.md#L156).
        [Changes on branch agent/task-077, not pushed]
        readme.md | 2 ++
         1 file changed, 2 insertions(+)
    """.trimIndent()

    @Test
    fun parsesAgentWorkResult() {
        val blocks = parseRichText(sample)
        assertEquals(RichBlock.TagLine("agentwork", "Preparing a fresh copy of 'sandbox-site' for job task-077..."), blocks[0])
        assertTrue(blocks[1] is RichBlock.TagLine)
        assertEquals(RichBlock.Paragraph("I have completed the task."), blocks[2])
        assertEquals(RichBlock.Heading(3, "Files Changed"), blocks[3])
        assertTrue(blocks[4] is RichBlock.Bullet)
        assertEquals(RichBlock.Heading(3, "Summary of Changes"), blocks[5])
        assertTrue(blocks[6] is RichBlock.Bullet)
        assertTrue(blocks[7] is RichBlock.Bullet)
        assertEquals(RichBlock.BranchBadge("Changes on branch agent/task-077, not pushed", false), blocks[8])
        assertEquals(RichBlock.DiffStatFile("readme.md", "2", 2, 0), blocks[9])
        assertEquals(RichBlock.DiffStatSummary(1, 2, 0), blocks[10])
        assertEquals(11, blocks.size)
    }

    @Test
    fun fileLinksBecomeShortChips() {
        val inl = parseInlines("[readme.md](file:///C:/Projects/AgentWork/workspaces/task-077/readme.md#L154-L157)")
        assertEquals(listOf(RichInline.FileRef("readme.md", "L154-157")), inl)
        assertEquals(RichInline.FileRef("readme.md", "L156"), fileRef("file:///C:/p/readme.md#L156"))
        assertEquals(RichInline.FileRef("X.md", null), fileRef("file:///C:/p/docs/X.md"))
        val labelled = parseInlines("([`docs/X.md`](file:///C:/w/docs/X.md))")
        assertEquals(
            listOf(RichInline.Plain("("), RichInline.FileRef("X.md", null), RichInline.Plain(")")),
            labelled
        )
    }

    @Test
    fun boldCodeAndWebLinks() {
        val inl = parseInlines("**Done**: see `a.kt` at https://example.com/x. Or [docs](https://d.dev)")
        assertEquals(RichInline.Bold(listOf(RichInline.Plain("Done"))), inl[0])
        assertEquals(RichInline.Plain(": see "), inl[1])
        assertEquals(RichInline.Code("a.kt"), inl[2])
        assertEquals(RichInline.WebLink("https://example.com/x", "https://example.com/x"), inl[4])
        assertEquals(RichInline.Plain(". Or "), inl[5])
        assertEquals(RichInline.WebLink("docs", "https://d.dev"), inl[6])
    }

    @Test
    fun plainTextStaysPlain() {
        val blocks = parseRichText("Just a sentence.\n\nAnother one | with a pipe")
        assertEquals(
            listOf(RichBlock.Paragraph("Just a sentence."), RichBlock.Paragraph("Another one | with a pipe")),
            blocks
        )
        assertEquals(listOf(RichInline.Plain("no markup here")), parseInlines("no markup here"))
    }

    @Test
    fun diffstatVariantsAndPushedBadge() {
        val blocks = parseRichText(
            "src/a.kt | 10 +++++-----\n3 files changed, 5 insertions(+), 7 deletions(-)\n[Changes on branch feat/x, pushed]"
        )
        assertEquals(RichBlock.DiffStatFile("src/a.kt", "10", 5, 5), blocks[0])
        assertEquals(RichBlock.DiffStatSummary(3, 5, 7), blocks[1])
        assertEquals(RichBlock.BranchBadge("Changes on branch feat/x, pushed", true), blocks[2])
    }

    @Test
    fun codeFenceAndNumbered() {
        val blocks = parseRichText("1. first\n```kotlin\nval x = 1\n```")
        assertEquals(RichBlock.Numbered("1", "first", 0), blocks[0])
        assertEquals(RichBlock.Code("kotlin", "val x = 1"), blocks[1])
    }

    @Test
    fun logLines() {
        val a = parseLogLine("2026-10-07T19:27:01.123", "WARNING", "[Push] [media] token refresh slow")
        assertEquals("19:27:01", a.time)
        assertEquals(LogLevel.WARNING, a.level)
        assertEquals(listOf("Push", "media"), a.tags)
        assertEquals("token refresh slow", a.message)

        val b = parseLogLine("", "INFO", "Traceback (most recent call last):")
        assertEquals(LogLevel.ERROR, b.level)

        val c = parseLogLine("19:00:00", "debug", "[INFO] tick")
        assertEquals(LogLevel.DEBUG, c.level)
        assertEquals(emptyList<String>(), c.tags)
        assertEquals("tick", c.message)
    }
}
