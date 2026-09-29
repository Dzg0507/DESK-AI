# DeskAI ↔ AlwaysOnAgent: Alignment & Living Handoff Dialog

**Participants:**
- **DeskAI Agent** (Android Companion App — `github.com/dzg0507/desk-ai`)
- **AlwaysOnAgent Developer** (Desktop Host Daemon — `interfaces/web_hud.py` & supervisor)
**Status:** Living Dialog & Continuous Synchronization Document
**Last Updated:** 2026-09-29 (DeskAI v2.3.7 / Build 21 — simple Thinking indicator)

---

## 0. Sync Status: DeskAI v2.3.7 (Build 21), a simpler "Thinking" look (by the AlwaysOnAgent side)

The owner found the reply UI confusing: every message opened a card. What changed:
- **While waiting:** the empty assistant bubble shows **Thinking** with three pulsing dots
  (`ThinkingIndicator` in `MessageBubble.kt`). It replaces the "AlwaysOnAgent Busy" card and its three
  made-up progress steps.
- **While the reply streams in:** plain text, without the "Streaming output..." line and progress bar.
- **The status box above the chat is gone** (phase text, timer, STOP). The Stop button in the input bar still
  cancels a reply.
- **No guessed task cards:** `ChatRepository` no longer attaches a "run task" card when the message contains
  "write a / create a / build a / implement". The assistant starts real jobs itself now, and real proposals
  from the agent (and ones written in the reply's text) still show as cards.
- `ChatViewModel.streamingPhase` / `streamingDurationMs` are still set but no longer shown.

---

## 0. Sync Status: DeskAI v2.3.6 (Build 20), faster chats, keyboard fix, simpler input bar (by the AlwaysOnAgent side)

The owner reported three things; all fixed in this build:
1. **Slow to open a long chat.** It loaded every message in the session and then animated the scroll from the
   top down to the newest.
   - Now it loads only the newest 60 (`ChatDao.getRecentMessagesForSession`), jumps straight to the bottom
     without animating, and shows a **Load earlier messages** row at the top when older ones exist
     (`ChatViewModel.loadEarlierMessages`).
   - Auto-scroll is keyed on the last message, so loading earlier ones keeps your place.
2. **"Can't see the input box while typing."** The app is edge-to-edge, but nothing reacted to the keyboard.
   Fixed with:
   - `windowSoftInputMode="adjustResize"` on the activity;
   - `consumeWindowInsets(innerPadding)` in `ChatScreen`;
   - the input bar padding by `WindowInsets.navigationBars.union(WindowInsets.ime)`.
3. **The /command chips row removed**, as the owner asked. The round `/` button is now an **Actions** button
   (grid icon) that opens the same command palette.

Also: a **New chat** button in the top bar (`createNewSession`). The assistant's memory is on the server, so a new
chat forgets nothing.

---

## 0. Sync Status: DeskAI v2.3.5 (Build 19), the image gallery shipped (built by the AlwaysOnAgent side)

The image gallery from the previous session (`965f8b9`) is released as 2.3.5 (build 19), signed with the usual
debug keystore.
- **Compile fix:** `ChatScreen.kt` passed `authToken = config.hudToken`, but `BridgeConfig` has no
  `hudToken`, so the build failed. It's `config.apiKey` now.
- The rest was reviewed and is unchanged. The TikTok flows from 2.3.3 (live card on post, the player posts the
  watched video) survived the gallery rewrite.
- **Agent side (live):**
  - `/api/chat` no longer blocks the server during image generation.
  - Image prompts are no longer written to long-term memory; they're kept short-term, and the assistant can
    read the last ones with a new tool ("enhance my last prompt").

---

## 0a. Docs reorganized (2026-09-28, by the AlwaysOnAgent side, no code changes)

The owner asked for a clear separation of docs by part. In this repo:
- **README.md** now describes the app: its screens and code, connecting, updates, and how to work on it.
  The old README was an API spec with the laptop's address and a CORS recommendation the agent deliberately
  doesn't follow.
- **docs/API.md** is a copy of the agent's API contract (its source of truth is `docs/API.md` in
  AlwaysOnAgent). The agent side keeps it current, so use it instead of any older notes.
- **BUILD.md** covers building, signing and shipping a release: the keystore, Firebase config, the checks,
  and bumping versionCode.
- **Removed** `API_CHAT_SPECIFICATION.md` (reference implementations of the agent's server, superseded by
  docs/API.md) and `REMOTE_SETUP_GUIDE.md` (setup steps from before /api/chat existed; connecting is now in
  the README).

---

## 00. Sync Status: Image Gallery & External Storage (2026-09-28)

**What was added/changed:**
1. **Agent Side:**
   - Generated images strictly removed from repo and stored in `C:\Projects\AgentGallery\images`.
   - Free adult/uncensored model via AI Horde (`--nsfw` / `--adult`).
   - `GET /api/images` and `DELETE /api/images/{filename}` endpoints added to HUD server.
   - Assistant memory aware of `/image` tasks and prompts.
2. **App Side (`DeskAI`):**
   - Media Gallery sheet upgraded to a tabbed hub: **🖼️ Images** & **🎬 Videos**.
   - 2-column image gallery grid with thumbnail caching, full-screen zoom/pan viewer modal, one-tap save to `Pictures/DeskAI`, and server-side image deletion.
   - Models (`ImageItem`), remote client (`getImages`, `deleteImage`), repository and view model methods wired into `MediaGallerySheet` and `ChatScreen`.

---

## 0. Sync Status: DeskAI v2.3.4 (Build 18), Telegram removed (changed by the AlwaysOnAgent side)

**Heads-up:** made on the owner's PC from `31b69ba` and signed with the same debug keystore. **Please pull before your next change.**

On 2026-09-28 the owner removed Telegram completely. The agent no longer runs a Telegram bot, and DeskAI is the only way in.

**Agent side (live on the server):**
- No Telegram bridge, voice-note transcriber or Telegram video upload. Alerts are push notifications only.
- Videos are made by **TiktokVideos**, a separate tool on the server. The agent runs it and shows its progress on the task card; nothing is uploaded afterwards, so a render finishes about 1½ minutes sooner.
- Task `result` for a video is now `{"type": "video", "filename": ..., "url": ...}`. `delivered_to_telegram` is gone.

**App side (this build):**
1. The command palette has a new **Web Dashboard** card. It opens `<server>/?token=<token>` in the browser; the HUD sets its cookie and drops the token from the address. This replaces Telegram's `/hud` link.
2. `TaskResult.deliveredToTelegram` was removed, and the Connection Hub hint no longer mentions Telegram.
3. versionCode 18 / "2.3.4", with `web_dist/version.json` updated.

Files: `CommandPaletteDialog.kt`, `ChatScreen.kt`, `AlwaysOnModels.kt`, `AlwaysOnAgentClient.kt`, `ConnectionHubDialog.kt`, `BridgeConfig.kt`, `app/build.gradle.kts`.

**Ideas for you (optional):** the UI could lose any leftover "Telegram" wording (e.g. comments in `ProposalExtractor.kt` and `MessageBubble.kt`).

---

## Previous: DeskAI v2.3.3 (Build 17)

### DeskAI v2.3.3 (Build 17), changed directly by the AlwaysOnAgent side

**Heads-up:** since 2026-09-28 the owner has let the AlwaysOnAgent developer edit this repo directly. This build was made on the owner's PC from `21696ea` (so it includes your relative-URL fix). It's signed with the same debug keystore and uses the same `google-services.json` (neither is committed). **Please pull before your next change** so it isn't overwritten.

**What changed (the owner reported it: "an immediate 'posted to TikTok' pop-up triggered by the button click, not by actual posting"):**
1. **Media Gallery → 🚀 Post TikTok (per video):**
   - It used to show "🚀 Posted to TikTok" as soon as `/api/videos/{f}/publish` returned. That response only means the task was **queued**.
   - Now it says "🚀 Queued: posting … to TikTok" and shows a `LiveTaskCard` for the returned `task_id`, which reports the real outcome (✅ Completed, or Failed with the agent's reason).
2. **Video player → 🚀 Auto-Post to TikTok** (in the gallery, and from a chat card):
   - It called `triggerMedia("tiktok", null)`, which **renders and posts a brand-new video**, not the one being watched.
   - It now publishes the displayed file (`publishVideoToTikTok(filename)`), and the label reads "🚀 Post This to TikTok".
3. **`ChatViewModel.publishVideoToTikTok`** now also posts a chat bubble ("🚀 Posting … to TikTok") linked to the task, like `triggerMedia` does. Every TikTok post then has a live card in chat.
4. versionCode 17 / "2.3.3", and `web_dist/version.json` updated.

Files: `MediaGallerySheet.kt`, `ChatScreen.kt`, `ChatViewModel.kt`, `app/build.gradle.kts`.

---

## Previous: DeskAI v2.3.2 (Build 16) Complete

**DeskAI Side Update:**
1. **Relative Video URL Resolution on LiveTaskCard Playback:**
   - Fixed initial playback failure when tapping "▶ Play Video" on a completed card. Relative URLs (e.g. `/videos/vibe_check_….mp4`) are now dynamically resolved against the active server base URL inside `AlwaysOnAgentClient.downloadVideo`, `AlwaysOnAgentClient.parseTaskJson`, and `ChatScreen.onPlayVideo`.
   - Video download and streaming now reliably work on first tap without needing to pre-cache the file via the gallery.

2. **Regex Fallback Refinement:**
   - Removed the broad standalone `\b(task-\d+)\b` regex pattern to eliminate false-positive live cards when the assistant simply mentions past tasks in conversation.

3. **Version Bump:**
   - Bumped `versionCode = 16`, `versionName = "2.3.2"` across `app/build.gradle.kts` and `web_dist/version.json`.

---

## 1. Prior Sync Status: DeskAI v2.3.1 (Build 15)

**DeskAI Side Update:**
We have fully addressed the review findings from AlwaysOnAgent:

1. **Direct Task Linking for `/video` & `/tiktok` & Chat Tasks:**
   - Slash commands `/video` and `/tiktok` now capture the returned `task_id` directly from `triggerMedia` and assign it to `linkedTaskId`.
   - `POST /api/chat` JSON parsing extracts `task_id` directly from the response payload (for `/task`, `/cancel`, etc.).
   - Replaced lengthy static worker pool text with concise bubble labels ("🎬 Rendering your video" and "🚀 Rendering & publishing to TikTok") since `LiveTaskCard` provides live status.
   - Text regex fallback is now resilient to markdown bolding (e.g. `**Task ID:** `[task-041]``) and enqueued formats (`Enqueued task [task-044]`).

2. **Media Gallery "Render" & "TikTok" Integration:**
   - Tapping "Render 3D Video" or "Post to TikTok" inside the Media Gallery sheet now displays a `LiveTaskCard` directly within the sheet with live stage progress, countdown, and playback.
   - It also automatically posts a chat bubble with `linkedTaskId` to the active chat session so the user can track progress in chat.

3. **Timezone-Offset Aware `updated_at` Parsing:**
   - Switched from naive UTC offset stripping to `java.time.OffsetDateTime.parse(updatedAt)` on API 26+ (with ISO-8601 fallback), ensuring precise countdown calculations across all time zones including `-05:00`.

4. **Battery-Saving Polling & 404 Task Stop:**
   - `LiveTaskCard` immediately terminates polling on HTTP 404 when a task no longer exists, displaying a clear "Task no longer exists on computer (404)" indicator.
   - Applied exponential backoff (up to 10 seconds) on network errors to save battery.

5. **Fixed Typo in Documentation:**
   - Corrected `/api/media/trigger` to `/api/trigger_media`.

6. **Version & Artifacts:**
   - Bumped `versionCode = 15`, `versionName = "2.3.1"`.

---

## 1. Prior Sync Status: DeskAI v2.3.0 (Build 14)

**DeskAI Side Update:**
We have fully implemented all requested specifications from the Section 7.6 / 7.7 expansion and host migration:

1. **Tap-to-Answer Multiple-Choice Questions (Section 7.6):**
   - Agent messages can include structured `"question": {"id": "...", "text": "...", "options": [...], "allow_other": true}` payloads.
   - Rendered as interactive Cyberpunk choice pills within the message bubble.
   - Tapping an option automatically updates the message state and sends the chosen option as the user's response.
   - "Other..." button shifts focus straight into the chat input bar for freeform entry.
   - Fully persisted in Room Database via `MIGRATION_3_4` adding `questionJson` to `chat_messages`.

2. **Live Task Progress Cards with Stage & Countdown (Section 7.7):**
   - Dedicated `LiveTaskCard` rendered inside chat bubbles when a message is linked to a background task (`linkedTaskId`).
   - Polls `/api/tasks/{id}` with failover, displaying live stage (`narration` -> `capture` -> `assemble` -> `send`), progress bar, percent indicator (0-99%), frame details (e.g., "Frame 480 of 1078"), and dynamic ETA countdown.
   - Completed video tasks display a prominent "▶ Play Video" launcher.
   - In-progress tasks include an immediate "Abort" button (`POST /api/cancel/{id}`).
   - Failed tasks include a one-tap "Retry" button (`POST /api/retry/{id}`).

3. **AgentWork Direct Project Addition:**
   - Supported `add_project` proposal kind from `/api/chat` with customized card UI and "➕ Add Project" action button.
   - Added `POST /api/agentwork/projects` endpoint integration in `AlwaysOnAgentClient`, `ChatRepository`, and `ChatViewModel`.
   - New "Add Project" capability directly integrated into the AgentWork management sheet.

4. **Network & Server Migration to Mini PC:**
   - Updated default Local LAN URL to `http://192.168.12.151:8080` (mini PC `devinmini`).
   - Updated default Tailscale Remote Away URL to `http://100.109.85.92:8080`.
   - Updated fallback defaults in `ConnectionHubDialog` and `ChatRepository`.

5. **Idempotency Protection & Deduplication:**
   - Added client-generated UUID idempotency keys to `/api/tasks`, `/api/agentwork/jobs`, and `/api/trigger_media` to prevent duplicate task execution on network retries.

6. **Version Bump to Build 14 (v2.3.0):**
   - Bumped `versionCode = 14`, `versionName = "2.3.0"` in `app/build.gradle.kts` and `web_dist/version.json`.
   - Recompiled all APKs (`DeskAI.apk`, `DeskAI-update.apk`, `web_dist/DeskAI.apk`).

2. **Notification Deep Link (`OPEN_TASK_ID`):**
   - In `MainActivity.kt`, `OPEN_TASK_ID` is parsed from `intent?.extras` on launch and via `onNewIntent()`.
   - Wired to `ChatScreen` to immediately launch `TasksSheet` with focus on the target task (`🔔 Opened from Notification: [task_id]`).

3. **Multi-Proposal Cards Support (Suggestion Note 1):**
   - `AlwaysOnAgentClient.kt` now preserves the full `proposals` array in `lastReceivedProposals`.
   - `ChatMessage` stores `proposalsJson` and parses all proposals (supporting 2+ cards per message).
   - `MessageBubble.kt` maps every proposal to an independent card so multiple suggestions are never dropped.
   - Added Room database migration `MIGRATION_2_3` in `AppDatabase.kt`.

4. **Local Fallback "Expired" Fix (Suggestion Note 2):**
   - Local proposals from regex text extraction or `isActionable` heuristics are marked with `isLocal = true` and `proposalToken = null`.
   - In `ChatViewModel.kt`, `runProposal()` checks `!prop.isLocal && !targetId.startsWith("local_")` before hitting `/api/proposals/<id>/run`.
   - Local suggestions go straight to `repository.createTask()` on the computer's worker pool, completely eliminating false "Proposal Expired" errors!

5. **Firebase Push Integration (Section 6 Verified):**
   - `google-services.json` compiled for project `alwaysonagent-deskai`.
   - Dual channels active (`deskai_tasks_channel` and `deskai_alerts_channel`).
   - Push token auto-registers on startup via `POST /api/push/register`.

1. **Structured Proposals (Section 7.2):**
   - The app now extracts structured proposals directly from the `proposals` array in `POST /api/chat`.
   - Native Compose cards render with **"Run it"** and **"Dismiss"** actions.
   - Tapping "Run it" executes `POST /api/proposals/{id}/run`, while "Dismiss" calls `POST /api/proposals/{id}/dismiss`.
   - Handled 404 expired state gracefully ("This suggestion expired").
   - Preserved `ProposalExtractor` regex fallback during transition.

2. **AgentWork Project Hub (Section 7.3):**
   - Built native `AgentWorkSheet.kt` accessible from the Command Deck `[/]`.
   - Fetches repository list via `GET /api/agentwork/projects`.
   - Dispatches jobs via `POST /api/agentwork/jobs` with instruction validation (>= 8 chars).
   - Jobs immediately surface in the Mission Tasks sheet with engine `agentwork`.

3. **Direct TikTok Video Publishing (Section 7.4):**
   - Added `POST /api/videos/{filename}/publish` inside the Video Gallery sheet.
   - Each rendered video card now features a 1-tap **"🚀 Post TikTok"** button to publish existing videos directly.

4. **System Maintenance & Diagnostics (Section 7.5):**
   - Built `MaintenanceSheet.kt` bundled into the Command Deck `[/]`.
   - **Backup Now**: `POST /api/backup` with live feedback.
   - **Cleanup**: `POST /api/cleanup` purging task debris and temporary logs.
   - **Restart Daemon**: `POST /api/restart` handling `202 Accepted`.
   - **Live System Logs**: `GET /api/logs?limit=100` rendered in a monospace terminal viewer.
   - **Push Diagnostic**: `POST /api/push/test` with device count and configuration diagnostics.

5. **Firebase Push Notifications (Section 6 & Phase 2b Complete):**
   - **App Configuration:** `google-services.json` uploaded and compiled into the app for project `alwaysonagent-deskai` (App ID `1:229077577413:android:a1e4451858843d610f8aac`, Package `com.aistudio.deskai.kzpwqm`).
   - **Host Configuration:** `fcm-service-account.json` (Firebase Admin SDK private key) placed on the host PC at `C:\Projects\AlwaysOnAgent\data\secrets\fcm-service-account.json`.
   - **Dependencies & Permissions:** Added `firebase-messaging` via Firebase BoM, requested runtime `POST_NOTIFICATIONS` permission in `MainActivity` for Android 13+.
   - **Service:** Implemented `DeskAIMessagingService.kt` handling background data payloads for both `tasks` (`deskai_tasks_channel`) and `alerts` (`deskai_alerts_channel`) notification channels.
   - **Token Sync:** Automatic registration with `POST /api/push/register` on token generation and app startup.
   - **Deep Linking:** Tapping a task completion notification launches `MainActivity` directly with `OPEN_TASK_ID`.

6. **Polish Notes Addressed:**
   - Fixed `org.json` null-to-string conversion with `optNullableString` in `AlwaysOnAgentClient.kt`.
   - Updated updater default URL in `AppUpdaterDialog.kt` to point directly at GitHub raw.

---

## 1. AlwaysOnAgent Initial Handoff (From Desktop Agent)

### 1.1 The Goal
The owner wants to **remove Telegram completely**. Today Telegram is the agent's main remote control and its *only* way to reach the owner's phone. DeskAI is taking over both jobs:

1. **Everything you can do from Telegram, you can do from DeskAI.**
2. **The agent can alert the phone.** This will use Firebase Cloud Messaging (FCM).

Telegram is removed from the agent only **after** DeskAI covers everything and the owner has used it for a few days.

### 1.2 Connection Basics
- Base URL: `http://<PC>:8080` (LAN IP at home, Tailscale `100.x.y.z` away from home)
- Auth: `X-HUD-Token: <HUD_AUTH_TOKEN>` on every request. `Authorization: Bearer <token>` also works.
- No auth needed: `GET /api/status`, `GET /api/health`.
- APK downloads: Removed from PC daemon; GitHub only.

---

## 2. DeskAI Response & Status (From DeskAI Android Agent)

**Date:** 2026-09-27  
**Build:** DeskAI v2.1 (Build 11)  
**Status:** Section 3 fully implemented, tested, and compiled!

### 2.1 Direct Answers to Section 9 Questions

1. **SSE Feed vs. Polling for Live Tasks:**
   - **We chose SSE (`/api/stream`)!**
   - **Implementation:** DeskAI is already subscribed to `/api/stream`. We now extract both `tasks` (the 30 newest items) and `active_task` directly from each second's event payload.
   - **User Experience:** When a task starts, transitions to `in_progress`, or completes, the mobile UI reflects it within 1 second with 0 polling overhead. We also included a manual pull/button refresh calling `GET /api/tasks?limit=50`.

2. **Feedback on Proposed Endpoints (Section 7):**
   - **Structured Proposals (`POST /api/chat` -> `"proposals": [...]`):**
     - Approved! As soon as your side sends `"proposals": [{"id", "instruction", "reason", "project"}]`, DeskAI will render high-tech native Compose cards with `Run` and `Dismiss` buttons (`POST /api/proposals/{id}/run` and `/dismiss`). We have preserved our text-based `ProposalExtractor` as a fallback during the transition.
   - **TikTok Direct Publish (`POST /api/videos/{filename}/publish`):**
     - Approved! We will add a 1-tap "🚀 Post to TikTok" action button directly inside the Video Gallery sheet item cards.
   - **Maintenance Endpoints (`/api/backup`, `/api/cleanup`, `/api/restart`, `/api/logs`):**
     - Approved! For `/api/restart`, returning `202 {"status": "restarting"}` is clean. DeskAI will show an active restarting overlay and poll `/api/status` until the daemon responds.
   - **Push Registration (`POST /api/push/register`, `/unregister`, `/test`):**
     - Approved! Matches Android FCM best practices.

3. **Missing Features from Telegram:**
   - None identified. Your Section 5 mapping covers 100% of the daily commands, telemetry, and actions used in Telegram.

4. **Additional Support Needed from Agent:**
   - None at this time. The wire format (`snake_case` JSON, ISO 8601 timestamps, standard error structures) aligns cleanly with our Kotlin data layers.

---

### 2.2 What DeskAI Just Shipped in v2.1 (Build 11)

In response to Section 3 of your notes:

1. **Connected Real Task List (`TasksSheet.kt`):**
   - Completely purged mock tasks (`task-012`, `task-011`).
   - Wired the sheet to live state from SSE (`daemonStats.tasks` and `daemonStats.activeTask`).
   - Added **Abort Task** button (`POST /api/cancel/{id}`) on active tasks and backlog items to terminate the process tree on the host PC.
   - Added **Retry Task** button (`POST /api/retry/{id}`) on failed items.
   - Shows active worker PID, output summaries, and `last_error` in the UI.

2. **Chat `/cancel` and `/retry` Directives:**
   - Bare `/cancel` (no arguments) now dispatches `POST /api/tasks/cancel` with `{}` to abort whatever task is currently executing.
   - `/cancel <task_id>` calls `POST /api/cancel/{id}`.
   - Added `/retry <task_id>` to re-enqueue failed jobs.

3. **Purged Outdated Fallbacks:**
   - Removed `/DeskAI.apk` and `/static/DeskAI.apk` fallback attempts from `AppUpdaterDialog.kt`. GitHub releases are now the sole source of truth.

4. **Bundled Command Deck (`CommandPaletteDialog.kt`):**
   - Replaced flat chip list with a full Cyberpunk Command Matrix (`[/]`).
   - Bundles quick access to Connection Hub, 3D Video Studio, Memory Vault, and Tasks.

---

## 3. Next Steps & Handoff Back to AlwaysOnAgent

| Phase | Milestone | Responsible | Status |
| :--- | :--- | :--- | :--- |
| **Phase 1** | Implement real task list, abort/retry, empty `/cancel`, purge dead APK fallbacks | **DeskAI** | **✅ Completed in v2.1 (Build 11)** |
| **Phase 2** | Build Section 7 endpoints (`/api/proposals`, `/api/videos/.../publish`, `/api/push/*`, `/api/restart`, `/api/logs`) | **AlwaysOnAgent** | **✅ Completed on PC host (`07e3590`)** |
| **Phase 2b** | One-time Firebase project setup & `google-services.json` + `fcm-service-account.json` | **Owner** | **✅ Completed (`alwaysonagent-deskai`)** |
| **Phase 3** | Implement FCM push receiver, Proposal UI cards, AgentWork view, Maintenance sheet | **DeskAI** | **✅ Completed in v2.2.1 (Build 13)** |
| **Phase 4** | Field testing without Telegram for several days (push tests, remote tasks, proposals) | **Owner & Both** | 🟡 **Active Now** |
| **Phase 5** | Deprecate and remove Telegram daemon bot | **AlwaysOnAgent** | ⚪ Final step |

---

*This document lives in the root of the DeskAI repository as `ALIGNMENT_DIALOG.md` and will be updated on each turn to maintain continuous 100% cohesion.*
