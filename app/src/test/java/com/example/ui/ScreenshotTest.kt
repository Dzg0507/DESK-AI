package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.model.AgentTaskItem
import com.example.data.model.ChatMessage
import com.example.data.model.DaemonStats
import com.example.ui.components.AgentStatusBar
import com.example.ui.components.ChatInputBar
import com.example.ui.components.AtmosphereBackground
import com.example.ui.components.EmptyState
import com.example.ui.components.MessageBubble
import com.example.ui.components.SectionCard
import com.example.ui.components.SectionLabel
import com.example.ui.components.SheetHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.TagChip
import com.example.ui.components.deskSheet
import com.example.ui.components.phaseColor
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app's key screens as pictures (2026-10-27, docs/FEED_AND_DESIGN_PLAN_2026-10-27.md 4.2): the look gets reviewed
 * like code. `gradle recordRoborazziDebug` writes the PNGs in app/src/test/screenshots; `verifyRoborazziDebug` fails when
 * a change alters one, and the diff image shows where.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        // The status bar's beacon pulses forever: run the clock by hand so the test never waits for it to settle
        compose.mainClock.autoAdvance = false
        // Sheets live in a Material surface, which gives their text its color; so does this
        compose.setContent {
            MyApplicationTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurface, content = content)
            }
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun chat() = capture("chat") {
        Box(Modifier.fillMaxSize()) {
            AtmosphereBackground()
            Column(Modifier.fillMaxSize()) {
                AgentStatusBar(
                    stats = DaemonStats(daemonStatus = "running_task", tasksCompleted = 231, tasksInProgress = 1,
                        activeTask = AgentTaskItem(id = "task-232", title = "Render tonight's TikTok", phase = "in_progress")),
                    serverUrl = "https://devinmini.example.ts.net", isConnected = true, onToggleDaemon = {},
                    onOpenTasks = {}, onOpenMemory = {}, onOpenMedia = {}, onOpenSettings = {}
                )
                Column(
                    Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SAMPLE_MESSAGES.forEach { MessageBubble(message = it) }
                }
                ChatInputBar(text = "", onTextChange = {}, onSend = {}, isStreaming = false, onStopStreaming = {})
            }
        }
    }

    @Test
    fun sheetParts() = capture("sheet_parts") {
        Column(
            Modifier.fillMaxSize().background(com.example.ui.theme.SlateDarkBackground).padding(12.dp)
                .deskSheet().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeader(title = "Tasks", icon = Icons.Default.Inbox, subtitle = "231 done · 1 running", onClose = {})
            SectionLabel("Running", count = 1)
            SectionCard(accent = phaseColor("in_progress"), onClick = {}) {
                Text("Render tonight's TikTok from the cabin footage")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    StatusPill("Running", phaseColor("in_progress"))
                    TagChip("task-232")
                    TagChip("schedule #3", active = true)
                }
            }
            SectionLabel("Done", count = 2)
            SectionCard(accent = phaseColor("completed")) {
                Text("Weekly growth review: 14% more Pinterest impressions")
                Row(Modifier.padding(top = 6.dp)) { StatusPill("Done", phaseColor("completed")) }
            }
            SectionCard(accent = phaseColor("failed")) {
                Text("Check the court docket for new filings")
                Row(Modifier.padding(top = 6.dp)) { StatusPill("Failed", phaseColor("failed")) }
            }
            EmptyState(icon = Icons.Default.Memory, title = "Nothing waiting on you",
                message = "Questions from the agent show up here.", modifier = Modifier.fillMaxWidth())
        }
    }

    @Test
    fun today() = capture("today") {
        Box(Modifier.fillMaxSize()) {
            AtmosphereBackground()
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    com.example.ui.today.ChatTodaySwitch(showingToday = true, onChange = {})
                }
                com.example.ui.today.TodayScreen(
                    state = com.example.ui.today.TodayUiState(page = SAMPLE_TODAY),
                    onRefresh = {}, onReact = { _, _ -> }, onAsk = {}, onAnswer = { _, _ -> true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    private companion object {
        val SAMPLE_TODAY = com.example.data.model.TodayPage(
            edition = com.example.data.model.TodayEdition(1, "2026-10-28T07:57:08-05:00", "2026-10-28", 5,
                com.example.data.model.TodayWeather(62, "Clear", "Jonesboro", true), "2026-10-28T13:57:08-05:00"),
            items = listOf(
                com.example.data.model.TodayItem(1, "story", "Bobcat spotted near Strawberry River",
                    "A bobcat walked past a trail camera on the Strawberry River slope early Tuesday.",
                    "https://trails.example.com/bobcat", "Arkansas Outdoors", "You follow Arkansas outdoors", "new",
                    "https://devinmini.example.ts.net/api/today/items/1/image"),
                com.example.data.model.TodayItem(2, "story", "Free Gemini credits for indie developers this month",
                    "Google opened a new round of free API credits for small projects, no card needed.",
                    "https://news.example.org/credits", "news.example.org", "You build with free tiers", "saved", null),
                com.example.data.model.TodayItem(3, "settled", "Settled", "The court granted your IFP motion on Oct 5.",
                    "", "", "", "new", null),
                com.example.data.model.TodayItem(4, "done", "4 finished",
                    "Post 1 pin to Pinterest ×2\nWeekly growth review\nAuto-Post to TikTok", "", "", "", "new", null),
                com.example.data.model.TodayItem(5, "question", "A question",
                    "Which trail have you liked most this fall?", "", "", "", "new", null)
            )
        )

        val SAMPLE_MESSAGES = listOf(
            ChatMessage(id = "1", sessionId = "s", role = "assistant", inboxKind = "question",
                content = "Have you noticed any cool wildlife passing by your trail camera lately down near the " +
                    "Strawberry River slope?"),
            ChatMessage(id = "2", sessionId = "s", role = "user",
                content = "No haven't seen much wildlife besides the deer, hogs, and racoons."),
            ChatMessage(id = "3", sessionId = "s", role = "assistant", modelUsed = "gemini-3.8-flash",
                content = "Sounds like the **usual lineup**. Here's what the camera caught this week:\n\n" +
                    "- 14 deer, mostly after 9 pm\n- 6 hogs on Tuesday\n- 2 raccoons\n\n" +
                    "[▶ vibe_check_1793106074.mp4 (28.5 MB)](/api/files/m/QzpcVmlk/vibe_check_1793106074.mp4)"),
        )
    }
}
