# DeskAI ↔ AlwaysOnAgent: Alignment & Living Handoff Dialog

**Participants:**
- **DeskAI Agent** (Android Companion App — `github.com/dzg0507/desk-ai`)
- **AlwaysOnAgent Developer** (Desktop Host Daemon — `interfaces/web_hud.py` & supervisor)
**Status:** Living Dialog & Continuous Synchronization Document
**Last Updated:** 2026-10-10 (DeskAI v2.10.0 / Build 61 shipped — Share to agent)

---

> [!IMPORTANT]
> ### Inviolable Rules for AlwaysOnAgent & DeskAI Pairing
> 1. **No Local Android Build Tools:** Never download, install, or run Gradle / Android SDK on this HP EliteDesk Mini PC. This host only runs Python/FastAPI server tasks. DeskAI compilation and debug signing live exclusively on the owner's workstation where `debug.keystore` is stored.
> 2. **Never Advance `web_dist/version.json` Without the Binary:** `version.json` must always reflect the exact `versionCode` compiled into the committed `DeskAI.apk`. Bumping `version.json` prematurely triggers an infinite update loop on the user's phone.

---

## Shipped 2026-10-10: DeskAI v2.10.0 (build 61), Share to agent (by the laptop Claude session, at the owner's request)

- New `ShareReceiverActivity` (no UI, translucent, excluded from recents) receives ACTION_SEND (text, images, video, PDF, Word) and ACTION_SEND_MULTIPLE (images, PDFs). It copies each shared file into `cacheDir/shared/` at once (the read permission is only its own, and short-lived), hands the text and copies to `SharedInbox`, and brings `MainActivity` forward (NEW_TASK|CLEAR_TOP|SINGLE_TOP, extra SHARED_TO_AGENT). `ChatScreen` (`sharedTick`) passes it to `ChatViewModel.acceptShared`: the text joins the message box, the files go through the usual `attachFile` upload. Nothing is sent until the owner taps Send. Up to 5 files.
- Not tried on the phone before shipping (it wasn't connected).

## Shipped 2026-10-10: DeskAI v2.9.1 (build 60), task details from the chat (by the laptop Claude session, at the owner's request)

- Tapping a task card in the chat (not its buttons) opens `TaskDetailsSheet`, the same sheet as tapping the task in Missions (`LiveTaskCard.onOpenDetails`, `ChatScreen.chatTaskDetails`). Not tried on the phone before shipping (it wasn't connected).

## Shipped 2026-10-10: DeskAI v2.9.0 (build 59), task cards for every task; Run & View only; landscape View only (by the laptop Claude session, at the owner's request)

- **Every task gets a card in the chat** (`ChatRepository.syncTaskCards`, called from the stats loop, so within seconds): a task no message links to yet gets an assistant message "📋 <source> started a task: <title>" with its live card. Message id `taskcard-<task id>` (never twice); a 6 s grace lets the app's own cards come first; tasks whose source starts with "DeskAI chat" are skipped (the reply brings their card); the first run starts from the newest task (no flood of old ones). New DAO query `countMessagesForTask`.
- **Run & View only** on computer-task suggestion cards (`ProposalCard.onRunAndWatch`): runs it and opens Take over in View only. Running computer tasks' cards get a **View only** button (`LiveTaskCard.onWatch`).
- **View only is landscape and full screen** (`TakeOverScreen.WatchFullScreen`): sensor-landscape, system bars hidden, the picture fills the display (pinch to zoom), a tap shows the bar (what the run is doing, Close) for a few seconds; leaving restores the orientation. `MainActivity` now handles orientation/screen-size changes itself (`android:configChanges`), so rotating doesn't recreate the activity and close Take over.
- Not tried on the phone before shipping (it wasn't connected); compiled and signed with the usual key.

## Shipped 2026-10-08: DeskAI v2.8.3 (build 58), Take over shows running task & status (by Antigravity session, at owner's request)

- Set `versionCode = 58` and `versionName = "2.8.3"` in `app/build.gradle.kts`.
- Take over shows the running task and what it's doing.
- Rebuilt debug APK signed with debug keystore and synced to `DeskAI.apk`, `DeskAI-update.apk`, and `web_dist/DeskAI.apk`.
- Updated OTA update manifest `web_dist/version.json`.

## Shipped 2026-10-08: DeskAI v2.8.2 (build 57), View only mode in Take over (by Antigravity session, at owner's request)

- Pulled `Dzg0507/DESK-AI` `main` (`fb7f056`).
- `TakeOverScreen.kt`: Added a "View only" checkbox mode to Take over, allowing the owner to watch real-time computer use runs without sending input or interrupting the active task.
- `CommandPaletteDialog.kt` & `ChatScreen.kt`: Cleaned up action handlers and dialog interactions.

## Shipped 2026-10-07: DeskAI v2.8.1 (build 56), Media URL security & signing isolation (by Antigravity session, at owner's request)

- Applied `scripts/deskai_patches/security.patch` (2 commits).
- Security fix: Token removed from media URLs. In-app media (chat images, Media Hub photos/videos/thumbnails) loaded exclusively with Authorization header. External sharing, browser viewing, and external players obtain signed, time-limited expiring URLs via `POST /api/media/link`.
- Keystore & signing isolation: Stopped tracking `DeskAI-source.zip` and `web_dist/DeskAI-source.zip` (removed from disk). `debug.keystore` moved to `~/.deskai/debug.keystore` outside repository tree. Signing settings sourced cleanly from `local.properties` / environment variables.

## Shipped 2026-10-07: DeskAI v2.8.0 (build 55), App-wide visual polish & design system (by Antigravity session, at owner's request)

- Applied `scripts/deskai_patches/beautify-all.patch` (7-commit series).
- `ui/theme` & `ui/components/DeskUi.kt`: Added shared color palette tokens, typography scale, spacing and shape tokens, plus unified card/sheet surfaces (`deskSheet`, `deskCard`).
- Visual styling overhaul across all screens:
  - Chat: gradient message bubbles, live connection status indicator in top bar, refined empty chat state.
  - Missions: status-colored left border accent, status pills, readable short results, timestamp and source chips.
  - Task Details: clean structured layout with status headers and rich output.
  - Maintenance: colored log levels, tag pills, interactive test push status with colored transition.
  - Schedules, Memory, Media Hub tabs, Needs-you, AgentWork, Repos, Connection Hub, Updater, Command Deck, image viewer, and canvas all updated with consistent theme tokens and elevated surfaces.

## Shipped 2026-10-07: DeskAI v2.7.1 (build 54), Readable mission results & maintenance logs (by Antigravity session, at owner's request)

- Applied `scripts/deskai_patches/beautify.patch`.
- `RichText.kt`: Lightweight dependency-free parser and Compose renderer for agent output (headings, bullets, bold, code pills, clickable web links, file path chips, tag pills, diffstat green/red, git unpushed/pushed status badges).
- `TaskDetailsSheet.kt`: Mission results displayed with RichText formatting along with Copy and Raw/Pretty view toggle chips; error output highlighted in rose-tinted container with dedicated Copy chip.
- `MaintenanceSheet.kt`: Server logs parsed and styled per refresh (dimmed timestamps, colored log levels ERR/WARN/INFO/DBG, tag pills, tinted rows for errors/warnings).
- Added `RichTextParserTest` JVM unit tests.

## Shipped 2026-10-07: DeskAI v2.7.0 (build 53), Mission details & video thumbnail playback (by Antigravity session, at owner's request)

- Applied `scripts/deskai_patches/task-details.patch` (AlwaysOnAgent PR #5 companion).
- `TasksSheet.kt`: Cards in Missions list are now tappable to open detailed inspection.
- `TaskDetailsSheet.kt`: Shows task source, full prompt, created/started/completed timestamps, elapsed duration, engine/engine used, retries, exit code, and complete output or error.
- Video tasks display server-kept thumbnails (`thumb_url`) with `FrostedPlayBadge`. Tapping plays via `VideoPlayerDialog`; pruned videos display "Video no longer kept".
- `MediaGallerySheet.kt`: Extracted `FrostedPlayBadge` into a reusable composable.
- Updated `docs/API.md` mirrored from AlwaysOnAgent.

## Shipped 2026-10-07: DeskAI v2.6.9 (build 52), Live task events from /api/events SSE stream (by Antigravity session, at owner's request)

- Applied `scripts/deskai_patches/live-events.patch` (AlwaysOnAgent PR #4 companion).
- `MainActivity.kt`: Lifecycle hooks `onStart()` / `onStop()` manage `TaskEvents` connection so SSE stream only consumes network/battery while app is foregrounded.
- `AlwaysOnAgentClient.kt`: Added `streamTaskEvents()` listening to `GET /api/events` (`text/event-stream`) with dedicated 45s silence timeout and ping tracking.
- `TaskEvents.kt`: Foreground SSE manager with exponential backoff retry (2s–60s) and coroutine change counters (`awaitChange`, `awaitAnyChange`).
- `LiveTaskCard.kt`: Task cards update immediately when a server task event arrives (`TaskEvents.awaitChange`), falling back cleanly to 2s polling if stream is offline.
- `ChatViewModel.kt`: Status bar and daemon stats refresh immediately on task updates; inbox sync decoupled to 60s wall-clock interval.
- FCM push messaging remains untouched for background alerts. Tested and verified on physical device via ADB.

## Shipped 2026-10-06: DeskAI v2.6.8 (build 51), Video Hub thumbnails (by Antigravity session, at owner's request)

- `MediaGallerySheet.kt`: Added `VideoGalleryCard` featuring extracted video thumbnails (60dp x 86dp, 9:16 ratio) loaded via Coil `SubcomposeAsyncImage` with token auth headers, dark rounded border, and frosted glass play circle badge overlay. Polished title with 2-line ellipsis and metadata row with monospace size and date. Added quick "▶ Watch" and "🚀 Post TikTok" action chips.
- `AlwaysOnModels.kt`: `VideoItem` has `thumbnailUrl: String = ""`.
- `AlwaysOnAgentClient.kt`: `getVideos()` extracts `thumbnail_url` from backend (falling back to `/api/videos/$fname/thumbnail`) and attaches auth token.
- `AlwaysOnAgent` backend (`interfaces/hud/media.py`): Added `thumbnail_url` to `GET /api/videos` and added `GET /api/videos/{filename}/thumbnail` endpoint generating 360px JPEG thumbnails on-demand via FFmpeg and caching in `.thumbnails/`.

## Shipped 2026-10-06: DeskAI v2.6.7 (build 50), Take over redesign (by the laptop Claude session, at the owner's request)

- `TakeOverScreen.kt`: top = Mini/Laptop chips, "You're in control" with the last action, a round check button (hand back). Bottom = one icon bar: stop (hand back), keyboard, tap, 2x (double), drag, scroll up/down, zoom out (only when zoomed). Typing is hidden until the keyboard button: then the special keys and the text bar show right above the phone's keyboard (focused, so it opens), and closing the keyboard hides them. Modelled on a remote-desktop screenshot the owner sent. Not tried on the phone before shipping (unplugged).

## Shipped 2026-10-06: DeskAI v2.6.6 (build 49), videos centred in the player (by the laptop Claude session)

- `MediaGallerySheet.kt` `VideoPlayerDialog`: the VideoView gets `Gravity.CENTER` in its FrameLayout. VideoView shrinks itself to the video's shape and sat at the top-left, so a video taller than the frame (the owner's full-screen phone recording) had all the black on the right. Sizing is unchanged (MATCH_PARENT); checked on the owner's phone before shipping: a TikTok render fills the frame exactly as before, the recording is centred.

## Shipped 2026-10-06: DeskAI v2.6.5 (build 48), Repos (by the laptop Claude session, at the owner's request)

- New `ui/dialogs/ReposScreen.kt`, opened from the command deck's **Repos** card: the owner's GitHub repos (both accounts, filterable) -> folders -> a file's text (monospace, selectable). **Work on this** takes an instruction (with the folder or file being viewed) and calls `POST /api/github/work`; the agent registers the repo with AgentWork by itself when it isn't a project yet. Back goes up one level.
- Drawn in the activity (not a Dialog), like Take over: pop-ups don't get keyboard insets on the owner's phone.
- Plumbing: `AlwaysOnAgentClient.githubCall` (uses `scheduleCall`), `ChatRepository.github`, `ChatViewModel.github`. API contract: `docs/API.md` "Repos".

## Shipped 2026-10-06: DeskAI v2.6.4 (build 47), Take over without a pop-up window

- Builds 45-46 tried to make the Dialog's window resize for the keyboard; on the owner's phone it never did (46 was worse). `TakeOverScreen` is no longer a Dialog: it's drawn in the activity (edge-to-edge, so the keyboard's insets arrive), over the chat, with `BackHandler` = Hand back. Owner's design: two containers, the computer's screen (weight 1, shrinks) and the controls panel pinned under it, with `imePadding()` on the column.

## Shipped 2026-10-06: DeskAI v2.6.3 (build 46), Take over keyboard

- Build 45's `decorFitsSystemWindows = false` + `imePadding()` wasn't enough on the owner's phone (screenshot: the keyboard still covered the text box and keys). The dialog's own window (via `DialogWindowProvider`) is now set to `SOFT_INPUT_ADJUST_RESIZE`, `WindowCompat.setDecorFitsSystemWindows(w, false)` and MATCH_PARENT, so the keyboard's insets reach the layout.

## Shipped 2026-10-06: DeskAI v2.6.2 (build 45), Take over layout

- `TakeOverScreen`: the Dialog uses `decorFitsSystemWindows = false` and its Column `systemBarsPadding().imePadding()`. With the default the window never got the keyboard's or navigation bar's insets, so the text box and Send sat under them (the owner could barely see them even with the keyboard closed).

## Shipped 2026-10-06: DeskAI v2.6.1 (build 44), Take over the laptop

- `TakeOverScreen`: a Mini / Laptop switch in the top bar (not shown when opened from a Needs you request: those are the Mini's). Every call carries `machine` (docs/API.md "Take over" → "Which computer"); switching stops the old machine and starts the new one; the frame is `/api/computer/takeover/frame?machine=...`. The agent passes laptop calls to the laptop's own Take over (started at its login, Tailscale only); 503 when the laptop isn't answering.

## Shipped 2026-10-06: DeskAI v2.6.0 (build 43), Take over

- `ui/dialogs/TakeOverScreen.kt`: full-screen dialog. `POST /api/computer/takeover/start` (with the request id when opened from a Needs you request), then the frame (`GET .../frame`, JPEG) every 0.6 s; taps on the picture map to 0..1 coordinates in the picture's own space (the pointerInput sits after the zoom graphicsLayer, so pinch-zoom doesn't throw taps off); hold = right-click; Double / Drag one-shot modes; scroll, text and key buttons. Hand back or leaving the screen calls `stop` (the agent answers the request with `__handed_back__`, the task looks again). Entry points: the command palette card "Take over" and "Take over and do it yourself" on every Needs you card. The owner tested the same flow as a web page on the laptop first and found it fast.
- `data/remote/UpdateSource.kt`: the badge (ChatScreen) and AppUpdaterDialog both read version.json, and download the APK, at the newest commit (GitHub API `commits/main`, Accept `application/vnd.github.sha`), because GitHub's raw cache served the previous version.json for minutes after a release (badge said update, screen said up to date). Falls back to the plain raw URL.

## Shipped 2026-10-06: DeskAI v2.5.3 (build 42), tappable links

- `MarkdownText.buildStyledInlineMarkdown` handled only bold and code, so a web address in a chat message was plain text, and chat text can't be long-pressed to copy. Now `appendWithLinks` turns `[label](https://...)` and bare `http(s)://` addresses into `LinkAnnotation.Url` links (underlined, sky blue), opened by Text itself.

## Shipped 2026-10-06: DeskAI v2.5.2 (build 41), task results shown

- The owner never saw the weekly numbers: the agent's `status_text` for a finished task is always "Completed", and both `LiveTaskCard` and `TasksSheet` showed `statusText ?: outputSummary`, so `output_summary` (the report, a computer task's answer) was never displayed.
- `LiveTaskCard`: under the status line, a completed or failed task's `outputSummary` in full (selectable; over 24 lines it collapses with Show all). `TasksSheet`: its first 6 lines under the status, tap to expand.

## Shipped 2026-10-06: DeskAI v2.5.1 (build 40), sign-ins

- New request kind `secret` (docs/API.md "Computer tasks: Needs you"): a password or code. `NeedsYouSheet` shows a hidden (password) field with Show/Hide and blocks screenshots on the sheet (`SecureFlagPolicy.SecureOn` whenever a secret is listed); the field is cleared on Send. The notification has no reply box for it (a RemoteInput shows what's typed), only "Type it in DeskAI", which opens the sheet. The tool types the value into the outlined box; the AI never sees it. Please keep it that way: never put a secret in a notification, a log or the chat.
- On a sign-in page a `choice` request can offer `Type my password` next to the page's own options (`Try another way`).
- `needs_you_cancel` push (c758b7d): removes the request's notification when it was answered elsewhere or its task ended.

## Shipped 2026-10-05: DeskAI v2.5.0 (build 39), Needs you

- AgentComputerUse (a new tool on the Mini, agent engine `computer`) waits for the owner instead of stopping; the agent pushes `type: needs_you` on `channel: needs_you` (docs/API.md "Computer tasks: Needs you").
- `service/NeedsYou.kt`: its own loud channel (alarm sound, strong vibration), the screenshot fetched with the token and shown as BigPicture, answer actions in the notification (Approve/Deny, up to 3 choices, or a RemoteInput reply) handled by `NeedsYouReceiver` (POST /api/computer/requests/{id}/answer). Stays until answered; reminders replace it and alert again.
- `ui/dialogs/NeedsYouSheet.kt`: what's waiting, with screenshot, site, question and answers; opened from the notification (`OPEN_NEEDS_YOU`) or a banner in the chat (`ChatViewModel.needsYou`, polled every 20 s).

## Shipped 2026-10-05: DeskAI v2.4.10 (build 38), Stop cancels the reply on the agent too

- Owner's case: they stopped a reply and retyped. The agent still finished the old reply and stored it, so a stale answer landed in its history.
- `ChatViewModel` gives each send a `turnId` (UUID). `/api/chat` carries it as `turn_id`. `stopStreaming()` calls `ChatRepository.cancelChat(turnId)` → `POST /api/chat/cancel` (agent e6a8a27).
- `AlwaysOnAgentClient`: the chat call goes through `awaitResponse()` (enqueue + `suspendCancellableCoroutine`, so cancelling aborts the OkHttp call; `execute()` blocked until the reply). `executeWithFailover` rethrows `CancellationException` instead of trying the next address.
- docs/API.md synced from the agent (Stop section).

## Shipped 2026-10-05: DeskAI v2.4.9 (build 37), images in chat

- Owner's report: the chip's thumbnail stayed blank, the bubble only showed "📎 name", and a file sent without text showed "What's in this file?" as if they'd typed it.
- Cause of the blank thumbnail: the agent sends `thumb_b64` as a `data:` URI, which Coil 2.7 can't load. `ChatAttachment.thumbModel()` now gives Coil the phone's copy (File) or the decoded bytes (ByteBuffer).
- Picked images are saved at most 1280 px, upright (EXIF), in `filesDir/chat_images/<localId>.jpg` (`ChatAttachment.localPath`).
- New column `chat_messages.attachmentsJson` (Room v6, MIGRATION_5_6): `[{name, kind, path}]`, via `MessageAttachment`. `MessageBubble` draws images (tap = full screen) and other files as an icon row.
- The user message stores only the typed text; `ChatRepository` sends the agent "What's in this file?" when it's blank. `ChatMessage.historyText()` puts "[attached: name]" in the history for file-only messages.

## Shipped 2026-10-05: DeskAI v2.4.8 (build 36), Restart reports when it's done

- Owner's report: after Restart in the Maintenance sheet the status said "restarting" forever.
- `restartAgent` shows the agent's own answer (`message`; `status: "draining"` = it waits for a running task, shown with ⏳). New `getAgentUptime` (GET /api/status). The sheet polls every 3 s until the agent is online with an uptime shorter than the time since the tap (3 min, or 32 min while draining), then shows "✅ Back online" or a warning, and refreshes the logs.

## Shipped 2026-10-04: DeskAI v2.4.7 (build 35), learned recipes

- Agent side (AlwaysOnAgent `10c3c90`, `60cf693`): procedural memory. `GET/POST /api/memory/procedures`, `PATCH/DELETE /api/memory/procedures/{id}` (docs/API.md, synced here).
- App: `RecipeItem` (origin(), track()); `getMemoryOverview` also loads the recipes (empty from an older agent); the Memory sheet has a RECIPES section above the facts: "When X:" / what to do / where it came from / area and track record, with pin, restore (retired ones) and delete. No redesign.

## Shipped 2026-10-04: DeskAI v2.4.6 (build 34), where each memory came from

- Agent side (AlwaysOnAgent `560eb33`): each fact in `/api/memory` has `source`, `evidence` and `source_message` {id, at, excerpt}.
- App: `MemoryFactItem` parses them; `origin()` makes the line under each fact in the Memory sheet ("From your message on Sep 24: ...", "Added by you"). No redesign.

## Shipped 2026-10-04: DeskAI v2.4.5 (build 33), draft cards on the agent's own messages

- Agent side (AlwaysOnAgent `bf31b1e`, `449f0eb`): the Sunday growth review. `GET /api/inbox` messages can carry `canvas` ({"id","title","kind","version"}, the same shape as a chat reply's), and a new kind `review`.
- App side (laptop Claude Code session): `InboxMessage.canvasJson`, parsed from `canvas`; the inbox sync stores it on the `agent-<id>` chat message, so the existing `CanvasCard` in `MessageBubble` shows it; the label "📊 Weekly review". No UI redesign.

## Shipped 2026-10-04: DeskAI v2.4.4 (build 32), the full profile in the Memory sheet

- The owner couldn't read the profile (it was a banner capped at 8 lines). It's now the first item of the sheet's scrolling list, a "🧠 PROFILE" card with the full text; the facts follow.
- Agent side (AlwaysOnAgent `6a77cf8`): the profile is now written as a few paragraphs of prose about the owner by name ("Devin…", not "The user…"), and facts use the name too. Not editable, at the owner's call: the agent rewrites it.

## Shipped 2026-10-04: DeskAI v2.4.3 (build 31), the Memory sheet loads the real memories

- **Bug:** `MemorySheet.kt` started from four hard-coded sample facts (ids 1-4) and never called `/api/memory`, so the owner always saw the same four. Worse, Forget on a sample sent its id to `DELETE /api/memory/facts/{id}?erase=true`, which would have erased the real fact with that id (#4 is the owner's location).
- **Fix** (laptop Claude Code session): new `onLoad` parameter (`ChatViewModel.fetchMemoryOverview()` → `/api/memory`), loaded when the sheet opens; the banner shows the real profile (max 8 lines); Add reloads from the agent; Forget removes a row only when the agent confirms. Loading and can't-reach-the-agent states in the header. Layout and styling unchanged.
- **Don't undo:** never seed this sheet with sample data; ids in it must come from the agent.

## Shipped 2026-10-04: DeskAI v2.4.2 (build 30), attachment thumbnails

- Built and shipped by the laptop Claude Code session: the thumbnail and video-attachment work below (`8fc746a`) was committed but not in a release; build 29 didn't have it.
- Only versionCode/versionName changed in code. Same debug key (`3315b429…`), Firebase included; the APK copies and `web_dist/version.json` change in the same commit.

## Implemented 2026-10-04: smart attachment thumbnails and AI vision downscaling

- **Chat attachment thumbnails & preview in ChatInputBar:**
  - `ChatAttachment.kt` updated with `thumbUrl`, `thumbB64`, `hasThumb`, and video method status support.
  - `AlwaysOnAgentClient.kt` parses `thumb_url`, `thumb_b64`, `has_thumb`, and resolves relative thumbnail URLs against `baseUrl`.
  - `ChatInputBar.kt`:
    - File picker launcher now allows `video/*` in addition to PDF, Word, text, and images.
    - `AttachmentChip` renders a clipped thumbnail with Coil's `AsyncImage` whenever a thumbnail (base64 or URL) is available, with an electric cyan border accent and smooth fallback to category icons (`PictureAsPdf`, `Image`, `Videocam`, `Description`).
- **AlwaysOnAgent side:**
  - `core/documents.py`: Generates `thumb.jpg` (256×256) and `ai_view.jpg` (768px Lanczos, quality 85, EXIF-transposed, metadata stripped) for images, videos (via FFmpeg), PDFs (page 1 cover), and documents (badged card).
  - `interfaces/hud/chat.py`: Exposes `GET /api/chat/attachments/{id}/thumb` and `GET /api/chat/attachments/{id}/file`.
  - Zero-touch housekeeping: `documents.prune()` deletes all thumbnail and vision view variants alongside the original attachment.

## Shipped 2026-10-04: build 29, one-tap go-live chips (by the local Claude session)

- Finished AgentWork jobs on `website` and `alwaysonagent` now carry a go-live chip: `🚀 Push live` / `⚡ Apply
  update`, with `action: "post"` and `url: /api/tasks/{id}/apply`. The tap is the owner's go-ahead (no chat turn).
  The chip then shows `⏳ Going live…` / `✅ Live` (`action: "none"`) or `🔁 Try again`.
- `ChatScreen` "post" chips: only a real `/api/videos/…/publish` URL goes to the TikTok path. Before this, Kotlin's
  `substringAfter` returned the whole URL when the marker was missing, so any other post chip would have been
  "published" as a video. Every other post chip calls `ChatViewModel.runChipAction(label, url)` →
  `AlwaysOnAgentClient.postChipAction`, which shows the agent's `message` (or its 409 `detail`) as a chat message.
  `"none"` chips do nothing.
- Version 29 / 2.4.1; `docs/API.md` synced (chip types, "Going live", `POST /api/tasks/{id}/apply`).

## Shipped 2026-10-04: build 28, the canvas (by the local Claude session)

Drafts the owner and the agent refine together (AlwaysOnAgent `docs/CANVAS.md`): documents (guides, scripts,
plans, emails) and website page previews. The app side:
- **The chat reply's `canvas`** (`{"id","title","kind","version"}`) is stored in the new `chat_messages.canvasJson`
  column (Room 4→5, `MIGRATION_4_5`). `MessageBubble` shows a `CanvasCard` ("📄/🌐 title · version N · Open").
- **`CanvasDialog`** (`ui/components/CanvasViewer.kt`): full screen, a version strip (from `GET /api/canvas/{id}`)
  and a WebView on `GET /api/canvas/{id}/preview?version=`, with JavaScript off and no file access. The token goes
  in the `X-HUD-Token` header, never the URL. "💬 Discuss" prefills the chat input with "About the draft …".
- The agent serves previews with a script-free, sandboxed CSP. A page preview uses the website's live stylesheets.
- Version 28 / 2.4.0; `docs/API.md` synced ("Canvas (drafts)").

**Don't undo:** JavaScript off in the canvas WebView, and the token in a header, never in the URL.

## Shipped 2026-10-04: build 27, the agent's own messages in the chat (by the local Claude session)

The agent now reaches out first (AlwaysOnAgent `docs/PROACTIVE.md`): a morning brief, notices, suggestions and
questions to get to know the owner. The app side:
- **Push `agent_message`** (channel `assistant`, with `message_id` and `kind`) carries **no text**, because pushes
  pass through Google. `DeskAIMessagingService` shows the notification on a new "Assistant messages" channel at
  normal importance, not heads-up, and calls `ChatRepository.syncInbox()`.
- **`ChatRepository.syncInbox(sessionId?)`** fetches `GET /api/inbox?after=<last id>` (`AlwaysOnAgentClient.fetchInbox`)
  and stores each message as an assistant `ChatMessage` with id `agent-<id>`. It uses
  `ChatDao.insertMessageIfAbsent` (IGNORE), so a push plus a later sync never shows it twice. Messages go into
  the conversation on screen, else the one the app opens on (`ChatDao.getLatestSession`, same order as the list).
  The last id is in SharedPreferences `deskai_inbox`. `modelUsed` labels the kind ("AlwaysOnAgent · ☀️ Morning
  brief", "· 💬 Question", …).
- **`ChatViewModel`** also syncs when the chat opens and then once a minute (every 10th stats poll), so a missed
  push loses nothing.
- The owner answers a question by replying in the chat as usual; the agent has the question in its own
  conversation history.
- Version 27 / 2.3.13; `docs/API.md` synced ("Messages the agent starts").

**Don't undo:** no message text in pushes (the app fetches it), and the `agent-<id>` ids with insert-if-absent.

## Shipped 2026-10-04: build 26 compiled and published (by the local Claude session on the owner's laptop)

- Build 26 had never compiled: `ChatScreen.kt` called the suspend function `viewModel.publishVideoToTikTok()`
  directly from the chip's click handler. Fixed by launching it like the Media screen's call does
  (`scope.launch { viewModel.publishVideoToTikTok(filename) }`); behaviour unchanged (the linked task card shows
  the real result).
- Built on the laptop: versionCode 26 / 2.3.12, signed with the usual debug key (SHA-256 `3315b429…`), Firebase
  app ID present. `DeskAI.apk`, `DeskAI-update.apk`, `web_dist/DeskAI.apk` and `web_dist/version.json` (26,
  24,050,382 bytes) were updated together in one commit (542e02f), per the build rules.

## 0. Sync Status: DeskAI v2.3.12 (Build 26), Contextual Action Chips & Task Diff Previews (by the AlwaysOnAgent side)

The agent now provides dynamic contextual `actions` on tasks (`GET /api/tasks` and `/api/tasks/{task_id}`, documented in `docs/API.md`), allowing the Android client to trigger actions directly from `LiveTaskCard`:
- **Contextual Action Chips:**
  - Tasks now include dynamic interactive chips in `LiveTaskCard`: `🔍 Preview Diff` (invoking `preview_task_diff` tool via chat), `⚡ Apply Update` (applying self-update commits), `💬 Discuss Blockers` (when blocked/needs_input), and `🚀 Post to TikTok` (when video is rendered and tiktok is pending).
  - Existing native controls (Abort Task while running, Play Video when completed, Retry Task on failure) remain dedicated, and duplicate action chips are automatically filtered out.
- **Data & Client Layer:**
  - `TaskAction` model in `AlwaysOnModels.kt` (`label`, `action`, `url`, `command`, `variant`).
  - Added `actions: List<TaskAction> = emptyList()` to `AgentTaskItem`.
  - `AlwaysOnAgentClient.kt`: parses `actions` array in `parseTaskJson` resolving relative server URLs.
  - `LiveTaskCard.kt`: renders styled interactive chips with variant-aware tinting and dispatches commands/URLs to chat and media handlers.
  - `ChatScreen.kt` & `MessageBubble.kt`: plumbed `onActionClick` callback for action execution.
  - `AppUpdaterDialog.kt` & `ChatScreen.kt`: added dynamic timestamp cache-busting (`?t=$now`) to GitHub raw requests to prevent CDN serving stale APKs, and aligned `web_dist/version.json` with the deployed APK to eliminate the infinite update prompt loop.

---

## 0. Sync Status: DeskAI v2.3.11 (Build 25), attach files in the chat (by the AlwaysOnAgent side)

The owner asked for a way to add documents to the chat. Server: `POST /api/chat/attachments` + `"attachments"`
on `/api/chat` (see docs/API.md, "Attaching documents"); the agent reads the text itself (documents directly,
images and scans by Tesseract OCR on the Mini, no AI).
- **ChatInputBar:** a paperclip button after the mic (the system file picker: PDF, Word, text, JSON, images;
  several at once), and a row of chips above the input: type icon, name, "Reading…" with a spinner, then how it
  was read ("Read by OCR (96% sure)") or a warning (amber) / error (red), and a remove button. Send is enabled with
  a ready file and no text (it sends "What's in this file?"), and waits while a file is still uploading.
- **ChatViewModel:** `attachments` state, `attachFile(uri)` (reads it, uploads at once, 5 files / 15 MB max),
  `removeAttachment`. **ChatRepository/AlwaysOnAgentClient:** `uploadAttachment`, and `attachmentIds` passed down to
  the `/api/chat` payload (default empty, so other callers are unchanged). The user bubble lists "📎 name" lines.
- New model: `data/model/ChatAttachment.kt`. The look follows the existing bar (same circle buttons, slate chips);
  change it however you like.

---

## 0. Sync Status: DeskAI v2.3.10 (Build 24), the update check asks only GitHub (by the AlwaysOnAgent side)

At every launch, `ChatScreen`'s background update check first asked the agent's server for `/version.json` and
`/static/version.json`, without the token. The server has never hosted that file, so each launch logged two
"Rejected unauthenticated request" warnings on the Mini (visible now that its console window is shown). Updates
are published only on GitHub (`web_dist/version.json`), so the check now asks GitHub only. Nothing else changed.

---

## 0. Sync Status: DeskAI v2.3.9 (Build 23), Schedules (by the AlwaysOnAgent side)

The owner asked for a scheduler. The agent now runs **videos (optionally posted to TikTok), phone reminders and
background tasks at set times** (server: `core/schedules.py`, API: `/api/schedules`, see docs/API.md).
- **New screen:** `ui/dialogs/SchedulesSheet.kt`, opened from the **Schedules** card in the command palette
  (where Web Dashboard was).
  - **List:** each schedule shows what it does, when (the server's plain-English `description`), next and last
    run, an on/off switch, **Run now**, and **Delete** (tap twice).
  - **New schedule form:**
    - Video / Reminder / Task.
    - A time picker and weekday toggles (Every day / Weekdays / Weekends), or an advanced cron field.
    - For videos, a **Post to TikTok** switch.
    - A live preview from `GET /api/schedules/preview` (it shows why a time is refused, e.g. videos must be 60 min
      apart).
    - Save sends an `Idempotency-Key`.
- **Data layer:**
  - `AgentSchedule`, `ScheduleList` and `SchedulePreview` in `AlwaysOnModels.kt`.
  - A small `scheduleCall()` helper plus six calls in `AlwaysOnAgentClient`.
  - Pass-throughs in `ChatRepository` / `ChatViewModel`.
- **Push:** reminders arrive as `type: "reminder"`, `channel: "reminders"`. `DeskAIMessagingService` has a new
  high-importance **Reminders** channel. Older builds show them on the alerts channel.
- **In chat:** the assistant has `create_schedule` / `list_schedules` / `change_schedule`, so "post a TikTok every
  day at 6pm" or "remind me on weekdays at 8:30" works without the screen.

---

## 0. Sync Status: DeskAI v2.3.8 (Build 22), the server's web dashboard is gone (by the AlwaysOnAgent side)

The owner doesn't use the browser dashboard (DeskAI shows everything), so the server dropped it while splitting
its 3,200-line `web_hud.py` into `interfaces/hud/` (one module per area). **Every API endpoint is unchanged.**
- **Removed on the server:**
  - The dashboard page at `/` and the Memory page at `/memory`.
  - The `?token=` cookie login those pages used.
  - `GET /` now returns `{"status": "online", ...}` (or 401), which is exactly what `ping()` checks.
- **Removed in the app:**
  - The **Web Dashboard** card in the command palette, and `onOpenDashboard`.
  - `/hud` now prints the server address and `/docs` (the memory page link is gone).
  - The connection dialog's paste button is now labelled **Paste Server Link** (it still parses
    `http://host:8080/?token=...`).
- **Images:** `/image` requests are now made by a separate tool, **ImageGen** (repo `TheVibeCheckProject/ImageGen`),
  that the agent runs. The chat reply format is the same (`![...](/images/gen_xxxx.jpg)`), and so are the
  gallery endpoints.

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

---

### Note from AlwaysOnAgent (2026-09-30): `needs_input` on tasks

- Tasks have a new boolean `needs_input` (see `docs/API.md`, synced from the agent). It's `true` when an AgentWork
  job ran but changed no files, usually because it was blocked or unsure; `phase` stays `completed`,
  `exit_code` is 4, `status_text` is "Needs your input", and `output_summary` starts with "⚠️ Needs your input"
  followed by the job's own explanation. Older app versions keep working (it's just a completed task).
- Its push is the usual `task_completed` type, with the title "⚠️ Needs your input".
- Optional, app side, whenever it suits: show these differently from a success (e.g. an amber badge).
- Related, no app change needed: finished jobs on the `alwaysonagent` project now end with "To put this change
  live, say "apply task-NNN"". The agent then tests, merges, restarts, health-checks and rolls back by itself.
