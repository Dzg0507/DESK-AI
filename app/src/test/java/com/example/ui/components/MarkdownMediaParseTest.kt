package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Files from the Mini in a reply (the agent's mini_files tool, 2026-10-27): images inline, the rest as media cards. */
class MarkdownMediaParseTest {
    @Test
    fun videoAudioAndPdfLinesBecomeMediaCards() {
        val parsed = parseMarkdown(
            "Here it is:\n" +
                "[▶ cabin bear.mp4 (12.3 MB)](/api/files/m/QzpcVXNlcnM/cabin%20bear.mp4)\n" +
                "[🔊 intro.wav (1.1 MB)](/api/files/m/QzpcTXVzaWM/intro.wav)\n" +
                "[📄 notes.pdf (40 KB)](/api/files/m/QzpcZG9j/notes.pdf)\n" +
                "![sinkhole.jpg](/api/files/m/QzpcUGlj/sinkhole.jpg)"
        )
        val media = parsed.filterIsInstance<MarkdownElement.Media>()
        assertEquals(3, media.size)
        assertEquals("▶ cabin bear.mp4 (12.3 MB)", media[0].label)
        assertEquals("/api/files/m/QzpcVXNlcnM/cabin%20bear.mp4", media[0].url)
        assertTrue(parsed.any { it is MarkdownElement.Image })
    }

    @Test
    fun aVideoWrittenAsAPictureStillPlays() {
        // 2026-10-27: "![vibe_check_1793106074.mp4](/videos/vibe_check_1793106074.mp4)" drew a blank image card
        val parsed = parseMarkdown("![vibe_check_1793106074.mp4](/videos/vibe_check_1793106074.mp4)")
        val media = parsed.filterIsInstance<MarkdownElement.Media>().single()
        assertEquals("▶ vibe_check_1793106074.mp4", media.label)
        assertTrue(parsed.none { it is MarkdownElement.Image })
    }

    @Test
    fun anOrdinaryLinkStaysText() {
        val parsed = parseMarkdown("[the docs](https://example.com)")
        assertTrue(parsed.none { it is MarkdownElement.Media })
    }
}
