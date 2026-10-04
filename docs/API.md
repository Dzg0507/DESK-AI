# The AlwaysOnAgent API

This is what DeskAI (and anything else) talks to. **This file is the source of truth.** A copy lives in the
DeskAI repo at `docs/API.md`, updated whenever this one changes.

## Basics

| | |
|---|---|
| Base URL | `http://192.168.12.151:8080` on the home Wi-Fi, `http://100.109.85.92:8080` over Tailscale (the mini PC `devinmini`) |
| Auth | `X-HUD-Token: <HUD_AUTH_TOKEN>` on **every** request (`Authorization: Bearer <token>` also works). Send it only to this host. |
| No auth | `GET /api/status`, `GET /api/health` |
| Token in a URL | `?token=<token>` also works, for media links opened outside the app |
| Errors | `401`: missing or wrong token. Otherwise `{"detail": "..."}` with a normal HTTP code. |
| JSON | `snake_case`; timestamps are ISO 8601 in the server's local time |
| CORS | none on purpose (it would let any website read the owner's memory). Native HTTP clients are unaffected. |
| Retries | send `Idempotency-Key: <uuid>` on task-creating requests: one UUID per user action, reused on every retry or failover. A repeat within 24 h returns the first `task_id` plus `"duplicate": true`. Accepted by `POST /api/tasks`, `/api/tasks/run`, `/api/trigger_media` and `/api/agentwork/jobs`. |

## Chat

**`POST /api/chat`** `{"message": "...", "model": "auto", "engine": "auto", "conversation_id": null,
"attachments": []}` →

```json
{"success": true, "reply": "...", "model": "...", "agent_status": "idle|working|waiting_for_approval|error",
 "task_id": null, "proposals": [], "question": null}
```

- **Timing:** replies take several seconds (a model with tool calls), so use a read timeout of 90 s or more.
- **One conversation and one memory** for every surface.
- **`task_id`:** set when the message started a task, e.g. "make me a video" or `/task …`. Show a live task
  card for it.
- **Commands** the agent handles itself:
  - `/status`;
  - `/task <instruction>` (queues a task);
  - `/cancel` (the running task);
  - `/restart`;
  - `/image <prompt>`, `/imagine <prompt>`, `/draw <prompt>`, which replies with markdown `![...](/images/gen_xxxx.jpg)` (supports `--style <anime|photo|3d|painting|pixel|cyberpunk|fantasy|retro|cinematic>`, `--nsfw` / `--adult` for uncensored generation via AI Horde, `--raw`, and Gemini prompt enrichment). The separate ImageGen tool makes the image; load it with the token. `/image help` returns the guide.
- **`proposals`:** suggested actions, each shown as a card:
  ```json
  [{"id": "a1b2c3d4e5", "instruction": "...", "reason": "...", "project": null, "kind": "task"}]
  ```
  - `kind` is `task`, or `add_project` (register a repo; label the button **Add project**).
  - **Run:** `POST /api/proposals/{id}/run` returns `{"status": "success", "task_id": "..."}`. For
    `add_project` it returns `{"status": "success", "project": "...", "message": "..."}` with no `task_id`,
    or 400 with the reason.
  - **Dismiss:** `POST /api/proposals/{id}/dismiss` returns `{"status": "ok"}`, and the assistant remembers it.
  - **404:** the suggestion expired because the agent restarted.
- **`question`:** one multiple-choice question when a detail is missing:
  ```json
  {"id": "9f2c1a7b3d", "text": "Which project?", "options": ["alwaysonagent", "sandbox-site"], "allow_other": true}
  ```
  - Show the options as buttons.
  - A tap sends that option's text as the next ordinary chat message. **Other…** focuses the text box.
  - A reply never has both a `question` and `proposals`.

### Attaching documents

Upload each file first, then send its id with the message (up to 5 per message):

| Method & path | Body | Returns |
|---|---|---|
| `POST /api/chat/attachments` | `{"name": "notes.pdf", "data_b64": "<the file, base64>", "conversation_id"?}` | `{"status": "success", "attachment": {"id": "att-1a2b3c4d5e6f", "name", "kind": "pdf"\|"word"\|"text"\|"image", "method": "text"\|"ocr"\|"mixed", "chars", "pages"?, "confidence"?, "warnings": [...], "seconds", ...}, "preview": "first 300 characters"}`; `422` with the reason (unsupported type, empty, over 15 MB, can't be read); `400` bad base64 |
| `GET /api/chat/attachments?conversation_id=&limit=10` | | `{"attachments": [attachment]}`, newest first |

- **Types:** PDF, Word `.docx`, text-like files (`.txt`, `.md`, `.csv`, `.json`, code…) and images (`.png`,
  `.jpg`, `.webp`…). Up to 15 MB.
- **How the text is read:** documents directly; images and scanned PDF pages by OCR (Tesseract, on the agent's
  PC), which takes a few seconds and can misread words. `method` says which; `warnings` flags an uncertain
  OCR reading or no text found. Show them on the file's chip.
- **In the chat:** `POST /api/chat` with `"attachments": ["att-…"]`. The message itself is required (send
  e.g. "What's in this file?" if the owner typed nothing). The history stores the message plus
  `[attached: name (id …)]`; the document's text goes to the model beside it, and the assistant can read further
  into it later in the conversation. `404` if an id is unknown. Uploads are kept 30 days.
- **Images also get a scene note** from a small vision model on the agent's PC (Ollama, `gemma3:4b`), in the
  background, about 40 s per image. A chat turn that carries an image waits up to 50 s for its note; without
  one the assistant says it can't see the image rather than guessing. Every 5 noted images, anything durable
  (things that repeat, or clearly matter) goes into long-term memory as facts with source `images`.

## Tasks

A **task object** (from `GET /api/tasks`, `GET /api/tasks/{id}`, and the stream's `tasks` / `active_task`):

| Field | Meaning |
|---|---|
| `id`, `title`, `prompt` | e.g. `task-041` |
| `phase` | `backlog`, `in_progress`, `completed`, `failed` |
| `cancelled` | `true` when the owner cancelled it (`phase` is then `failed`, `exit_code` 137) |
| `needs_input` | `true` when an AgentWork job ran but changed nothing, usually blocked or unsure (`phase` is then `completed`, `exit_code` 4). Its `output_summary` starts with "⚠️ Needs your input" and holds the job's explanation. Worth showing differently from a success |
| `status_text` | ready to show: `Waiting to start`, `Capturing frames: 42%, about 1 min 35 s left`, `Running`, `Completed`, `Needs your input`, `Cancelled`, `Failed: <reason>` |
| `progress` | while running, when the task reports it (videos do), otherwise `null` (show a spinner) |
| `result` | a finished video: `{"type": "video", "filename": "vibe_check_….mp4", "url": "/videos/vibe_check_….mp4"}`. Otherwise `null`: show `output_summary`. It's also `null` once the video has been pruned. |
| `engine` | `auto`, `antigravity`, `cloud`, `ollama`, `media_pipeline` (TiktokVideos), `agentwork` |
| `priority` | `low`, `medium`, `high`, `urgent` |
| `actions` | contextual 1-tap action chips: `[{"label", "action": "stream"|"post"|"chat", "url"|"command", "variant": "primary"|"secondary"|"danger"}]`. e.g. "Preview Diff", "Apply Update", "Discuss Blockers", "Post to TikTok" |
| `created_at`, `started_at`, `completed_at`, `output_summary`, `last_error`, `exit_code`, `engine_used`, `retry_count`, `worker_pid`, `metadata` | as named; ignore any others |

`progress`:

```json
{"stage": "capture", "label": "Capturing frames", "percent": 42, "detail": "Frame 480 of 1104",
 "eta_seconds": 95, "updated_at": "2026-09-28T20:06:02-05:00"}
```

- **Video stages:** `narration` → `capture` → `assemble`, then `post` ("Posting to TikTok") for TikTok jobs.
  `label` is display text; `detail` may be empty.
- **`percent`:** 0–99, based on time, so it can dip a point. Clamp it if you want it to only go up.
- **`eta_seconds`:** the time left as of `updated_at`. Count down locally between updates, which come about
  every 12 s while capturing and every 5 s otherwise, and show "almost done" rather than going negative.

### Action Chips (`actions`)
Each task object includes a dynamic `actions` array of contextual 1-tap buttons:
```json
[
  {"label": "🔍 Preview Diff", "action": "chat", "command": "Show diff for task-052", "variant": "secondary"},
  {"label": "⚡ Apply Update", "action": "chat", "command": "apply task-052", "variant": "primary"}
]
```
- **`action` types:**
  - `stream`: Opens media stream URL in video player (`url`: `/videos/...`).
  - `post`: Sends an authenticated HTTP POST request directly (`url`: `/api/cancel/{id}`, `/api/retry/{id}`, `/api/videos/{name}/publish`).
  - `chat`: Sends pre-formatted command/prompt into chat conversation (`command`: `apply task-052`, `Show diff for task-052`, etc.).
- **`variant` styles:** `primary` (highlight/action), `secondary` (neutral/outline), `danger` (destructive/cancel).

| Method & path | Body | Returns |
|---|---|---|
| `GET /api/tasks?limit=50&phase=in_progress` | | array, newest first |
| `GET /api/tasks/{id}` | | one task, or 404. Cheap: polling every 2 s is fine. |
| `POST /api/tasks` | `{"title", "prompt", "priority": "medium", "engine": "auto", "mode": "accept-edits", "timeout_seconds": 300}` | `{"status", "task_id", "woke_daemon", "message"}` |
| `POST /api/tasks/run` | `{"command": "..."}` (new) or `{"task_id": "..."}` (re-run a failed one) | `{"status", "task_id", "message"}` |
| `POST /api/cancel/{id}` | | 200; 404 if unknown; **409** if it already finished |
| `POST /api/tasks/cancel` | `{}` (the running task) or `{"task_id": "..."}` | as above; `{"status": "idle"}` if nothing is running |
| `POST /api/retry/{id}` | | back to `backlog`; **409** while it's still running |

## Videos (TiktokVideos)

| Method & path | Body | Returns |
|---|---|---|
| `POST /api/trigger_media` | `{"action": "video" or "tiktok", "quote": null}` | `{"status", "task_id", "woke_daemon", "message"}`. With no quote, a fresh one is written. `tiktok` renders then posts. |
| `POST /api/videos/{filename}/publish` | | `{"status": "success", "task_id": "..."}`: posts an existing video to TikTok (about a minute). The task's result says whether it worked. |
| `GET /api/videos` | | `{"videos": [{"filename", "size_mb", "created_at", "url"}]}`, newest first |
| `GET /videos/{filename}` | | the MP4, with byte-range support. `/videos/{name}.jpg` is its cover. |
| `GET /images/{filename}` | | images made by `/image` |

## Memory

| Method & path | Notes |
|---|---|
| `GET /api/memory` | stats, profile, facts, summaries, categories; add `?query=` to search |
| `GET /api/memory/search?q=...` | matching facts and archived messages |
| `GET /api/memory/conversation?limit=50&before_id=` | chat history, oldest first |
| `POST /api/memory/facts` | `{"content", "category": "fact", "importance": 5, "pinned": true}`. Categories: profile, preference, project, plan, person, instruction, fact. |
| `PATCH /api/memory/facts/{id}` | any of `content`, `category`, `importance` (1–5), `pinned`, `status` (`active`/`faded`) |
| `DELETE /api/memory/facts/{id}?erase=true` | forgets it everywhere, including old messages |
| `POST /api/memory/profile/rebuild` | rebuilds the owner profile now |
| `GET /api/memory/usage` | AI token usage by purpose over the last day |
| `GET /api/memory/budget` | today's calls and tokens per model, with countdowns to Google (Pacific) and Cloudflare (UTC) daily resets |

## AgentWork (project jobs)

| Method & path | Body | Returns |
|---|---|---|
| `GET /api/agentwork/projects` | | `[{"name", "description", "repo"}]` |
| `POST /api/agentwork/projects` | `{"name": "recipe-app", "repo": "https://github.com/o/r.git", "description": "..."}` | `{"status", "project": {...}}`, or 400 (bad name, not https, a duplicate, unreachable). It checks the repo first, which takes a few seconds. |
| `POST /api/agentwork/jobs` | `{"project": "...", "instruction": "at least 8 characters"}` | `{"status": "success", "task_id": "..."}`; 400 for an unknown project |

## Media and Gallery

Generated images are stored outside the agent repository in the dedicated gallery directory (`AGENT_GALLERY_DIR/images`), while 3D videos are rendered by TiktokVideos (`TIKTOK_VIDEOS_DIR/videos`, documented under [Videos](#videos-tiktokvideos)).

| Method & path | Body | Returns |
|---|---|---|
| `GET /api/images` | | `{"images": [{"filename", "size_mb", "created_at", "mtime", "url"}]}`: lists all generated artwork |
| `DELETE /api/images/{filename}` | | `{"status": "success", "message": "Deleted <filename>"}`: removes an image from disk |
| `GET /images/{filename}` | | Static image file (supports token parameter or auth headers) |

## Agent controls and maintenance

| Method & path | Body | Returns |
|---|---|---|
| `GET /api/status` | | `{"status": "online", "version", "uptime_seconds", "cpu_percent", "memory_percent", "active_model", "active_tasks_count"}`: no auth, so use it for the LAN → Tailscale failover ping (`active_model` is the default task engine) |
| `GET /api/health` | | `{"status": "online", ...}`, no auth |
| `POST /api/daemon/state` | `{"status": "wake"}` or `{"status": "standby"}` | `{"status", "daemon_status", "message"}` |
| `POST /api/engine` | `{"engine": "auto"}` (auto, antigravity, cloud, ollama) | `{"status", "default_engine"}` |
| `POST /api/backup` | | `{"ok", "message"}`: a memory backup now (a few seconds) |
| `POST /api/cleanup` | | `{"message"}`: TiktokVideos' temp files and old videos, stale logs (> 30 days, preserving `affirmations.log`), and abandoned task workspaces (> 7 days, via AgentWork worktree discard) |
| `POST /api/restart` | | `202 {"status": "restarting"}`: back in about 3 s. Poll `/api/status`. |
| `GET /api/logs?limit=100` | | `[{"id", "timestamp", "level", "message"}]`, newest last |
| `GET /api/stream` | | Server-Sent Events, one `data: {json}` per second: `timestamp`, `stats`, `active_task`, `tasks` (30 newest), `blockers`, `new_logs`, `daemon_status`, `overall_phase`, `last_heartbeat`, `default_engine` |

## Schedules

Videos, Pinterest pins, the weekly report, phone reminders and background tasks that run by themselves (see AGENT.md, Schedules). Times are 5-field
cron in the server's local time.

| Method & path | Body | Returns |
|---|---|---|
| `GET /api/schedules` | | `{"schedules": [schedule], "timezone": "Central Daylight Time", "kinds": [...], "min_gap_minutes": {"video": 60, "pins": 60, "report": 720, "task": 15, "reminder": 5}}` |
| `GET /api/schedules/preview?cron=0 9 * * 1-5&kind=video` | | `{"valid": true, "description": "weekdays at 09:00", "next_runs": [iso, iso, iso]}`, or `{"valid": false, "error": "..."}` |
| `POST /api/schedules` | `{"name", "kind": "video"\|"pins"\|"report"\|"reminder"\|"task", "cron", "enabled"?, "post_to_tiktok"?, "quote"?, "count"? (pins, 1-5), "text"? (reminder), "instruction"? + "project"? (task)}` | `{"status": "success", "schedule": schedule}`; `400` with the reason if the cron is invalid or too frequent. Accepts `Idempotency-Key`. |
| `PATCH /api/schedules/{id}` | any of the create fields except `kind` | `{"status": "success", "schedule": schedule}`. Changing `cron`, or turning it back on, re-plans the next run. |
| `DELETE /api/schedules/{id}` | | `{"status": "success", "id": id}` |
| `POST /api/schedules/{id}/run` | | Runs it once now, even in standby: `{"status": "success", "result": "started task-051", "task_id": "task-051", "ran": true}`. The next run doesn't change. |

A `schedule`: `id`, `name`, `kind`, `cron`, `description` (plain English), `enabled`, `next_run_at`, `last_run_at`,
`last_result`, `runs`, `post_to_tiktok`, `quote`, `text`, `instruction`, `project`, `count`, `created_at`, `source`.

A `pins` schedule posts the next `count` of the site's ready-made pins to Pinterest (SocialPostEngine), each run a
task with the usual live card (engine `social_posts`). Its `instruction` is filled in for display ("Post 2 pins to
Pinterest") and can't be set; the app can list it like a task until it has its own icon.

A `report` schedule puts together the weekly numbers (site visits by source, cards sent, daily-email subscribers,
TikTok views and followers, Pinterest impressions and clicks) as a task (engine `weekly_report`): the owner gets the
usual "task finished" push and reads the report as the task's result. Its `instruction` is also filled in for
display.

## Push notifications (Firebase Cloud Messaging)

| Method & path | Body | Returns |
|---|---|---|
| `POST /api/push/register` | `{"token": "<fcm token>", "device_name": "..."}` | `{"status": "ok", "push_ready": true}`. It's idempotent, so call it on every connect. |
| `POST /api/push/unregister` | `{"token": "..."}` | `{"status": "ok"}` |
| `POST /api/push/test` | | `{"status": "sent", "devices": 1}`, `{"status": "no_devices"}`, or `{"status": "not_configured", ...}` |

Messages are **data-only and high priority**, and the app builds the notification. The `data` map (all
strings):
- `type`: `task_completed`, `task_failed`, `alert`, `reminder`, `agent_message` or `test`;
- `task_id`;
- `title` and `body`, both short. They pass through Google's servers, so they never carry memory or chat
  content. The one exception is a reminder's body: the text the owner wrote for that reminder.
- `channel`: `tasks`, `alerts`, `reminders` or `assistant`. Apps older than 2.3.9 show reminders on the alerts
  channel.
- `agent_message` only: `message_id` and `kind` (see "Messages the agent starts").

**When pushes are sent:**
- `task_completed` for every finished task. One that needs the owner's input (`needs_input`) has the title
  "⚠️ Needs your input" instead of "✅ Task finished".
- `task_failed` for every failure except cancels.
- `alert` for backup failures, stuck tasks and safety-check failures.
- `reminder` when a reminder schedule runs.
- `agent_message` when the agent starts a message (below). The push has no text of its own, only what kind of
  message is waiting.

Tokens that FCM rejects are removed automatically.

## Messages the agent starts (proactive)

The agent reaches out by itself: a morning brief, notices, suggestions, and questions to get to know the owner
(design: [PROACTIVE.md](PROACTIVE.md)). Each one is announced by an `agent_message` push carrying `message_id`
and `kind`, but **not the text**. The app fetches the text here and shows it in the chat as an assistant
message, and the owner simply replies in the chat.

| Method & path | Returns |
|---|---|
| `GET /api/inbox?after=<id>&limit=50` | `{"messages": [{"id": 12, "kind": "brief"\|"notice"\|"suggestion"\|"question", "text", "created_at"}], "last_id": 12}`, oldest first, only ids greater than `after` |

The app keeps the highest id it has shown and asks for newer ones when a push arrives and whenever it opens, so a
missed push loses nothing. Using `agent-<id>` as the message's id in the app's chat keeps a message from showing
twice. The settings (on/off, brief time, quiet hours, caps) are changed in chat; the agent uses its
`proactive_settings` tool.

## Connection check

`GET /` returns `{"status": "online", "message": "AlwaysOnAgent is running"}`, or `401` with a missing or wrong
token. DeskAI's connection test uses it. There are no web pages: the dashboard and the Memory page were removed
on 2026-09-29, since DeskAI shows everything they did.
