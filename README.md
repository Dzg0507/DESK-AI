# DeskAI

The Android app for **AlwaysOnAgent**, the owner's always-on AI agent on a home mini PC. It's the only way in:
chat with the assistant, watch tasks run live, play and post videos, manage memory and project work, and
get push notifications.

The app only *shows* things. All logic, keys and configuration live on the server, so the app never needs an AI
key of its own. The agent's API is documented in [docs/API.md](docs/API.md), a copy of the source of truth in
the AlwaysOnAgent repo.

## What's in the app

| Screen / piece | Code | What it does |
|---|---|---|
| **Chat** | `ui/screens/ChatScreen.kt`, `ChatViewModel.kt`, `ui/components/MessageBubble.kt` | Talks to `POST /api/chat`. It renders suggestion cards (Run / Dismiss / Add project), tap-to-answer question buttons and markdown, and speaks replies aloud (text-to-speech). |
| **Live task cards** | `ui/components/LiveTaskCard.kt` | Any chat message that started a task gets a card: the stage, a progress bar, a time-left countdown, Abort, Retry, and ▶ Play Video when done. It polls `GET /api/tasks/{id}` every 2 s. |
| **Input** | `ui/components/ChatInputBar.kt` | Text, voice through Android's speech recognizer, and command chips |
| **Command palette** | `ui/dialogs/CommandPaletteDialog.kt` | Quick commands, plus the hubs: Connection, Video Gallery, Memory, Tasks, AgentWork, Maintenance, and **Web Dashboard** (opens the server's HUD signed in) |
| **Tasks** | `ui/dialogs/TasksSheet.kt` | The real task queue with progress, Abort and Retry |
| **Video Gallery** | `ui/dialogs/MediaGallerySheet.kt` | Render or post-to-TikTok buttons with a live card; every video with Watch and Post to TikTok |
| **Memory** | `ui/dialogs/MemorySheet.kt` | Browse, add and forget the assistant's long-term memories |
| **AgentWork** | `ui/dialogs/AgentWorkSheet.kt` | Start a code change on a project, or register a new repo |
| **Maintenance** | `ui/dialogs/MaintenanceSheet.kt` | Backup, cleanup, restart, logs |
| **Connection Hub** | `ui/dialogs/ConnectionHubDialog.kt` | Server addresses and the token |
| **Status bar** | `ui/components/AgentStatusBar.kt` | Live daemon state from `/api/stream` |
| **Updater** | `ui/dialogs/AppUpdaterDialog.kt` | Updates in place from GitHub (below) |
| **Push** | `service/DeskAIMessagingService.kt` | Firebase messages from the agent. Tapping one opens its task (`OPEN_TASK_ID`). |
| **Network client** | `data/remote/AlwaysOnAgentClient.kt`, `data/repository/ChatRepository.kt` | All calls to the agent, with LAN → Tailscale failover and an `Idempotency-Key` on everything that starts a task |
| **Local data** | `data/local/` (Room) | Chat sessions and messages, and the connection settings |

## Connecting

| | Address |
|---|---|
| At home (Wi-Fi) | `http://192.168.12.151:8080` |
| Away (Tailscale on) | `http://100.109.85.92:8080` |

- **Failover:** the app pings `GET /api/status` (no token needed) on the home address first and switches to the
  Tailscale address when that doesn't answer.
- **The token:** `HUD_AUTH_TOKEN` from the server's `.env` is sent as `X-HUD-Token` on every request, and only
  to the agent's own address, never to GitHub or anywhere else.
- **Settings survive updates:** the addresses and token are stored in Room (`BridgeConfig`) and mirrored in
  `SharedPreferences` (`desk_ai_secure_prefs`).

## Updates

1. **Checking:** the app checks `https://raw.githubusercontent.com/Dzg0507/Desk-ai/main/web_dist/version.json`,
   cache-busted.
2. **Downloading:** when its `versionCode` is newer than the installed one, the app downloads
   `…/main/DeskAI.apk` into its cache.
3. **Installing:** it hands the APK to Android's installer through a `FileProvider`.

Every build is signed with the same key, so updates install in place without losing data. **Every release
must bump `versionCode`**, or phones won't be offered it. Building and publishing are in
[BUILD.md](BUILD.md).

## Working on the app

The app is the owner's creative space. The look and flow are open to ideas; only the wire (URLs, fields,
auth in [docs/API.md](docs/API.md)) has to match the agent. Two developers work on it: the DeskAI AI (in AI
Studio) and the AlwaysOnAgent side (on the owner's PC). **Pull before you change anything**, and note what you
changed at the top of [ALIGNMENT_DIALOG.md](ALIGNMENT_DIALOG.md) so the other side knows.
