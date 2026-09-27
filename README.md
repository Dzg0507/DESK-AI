# DeskAI (Android Companion) & AlwaysOnAgent (Desktop Daemon)
## System Architecture, Integration Specification & Cohesion Guide

This documentation provides a comprehensive architectural and protocol specification for the cohesion between **DeskAI** (Android Client) and **AlwaysOnAgent** (Desktop PC Assistant Daemon). 

This guide is intended for AI agents, developers, and system administrators maintaining or extending either side of the ecosystem.

---

## 1. High-Level Ecosystem Overview

```
 ┌────────────────────────────────────────────────────────┐
 │                    DeskAI (Android)                    │
 │  - Material 3 Jetpack Compose UI                       │
 │  - Chat Interface & Task Execution Controls            │
 │  - Connection Hub (Local Wi-Fi & Tailscale/Cloudflare) │
 │  - In-App OTA Updater (Direct GitHub delivery)         │
 └───────────────────────┬────────────────────────────────┘
                         │
                         │ HTTP / REST / WebSockets / SSE
                         │ Auth: X-HUD-Token / Bearer / Cookie
                         ▼
 ┌────────────────────────────────────────────────────────┐
 │               AlwaysOnAgent (Host PC Daemon)           │
 │  - FastAPI / WebHUD Server (Default Port: 8080)        │
 │  - Task Execution Engine & Background Supervisor       │
 │  - Long-term Memory (SQLite WAL + Vector Store)        │
 │  - Multi-Model Router (Gemini, Claude, GPT, Local LLM) │
 └────────────────────────────────────────────────────────┘
```

* **DeskAI** serves as the mobile commanding interface: it monitors the computer, prompts the agent, approves proposed tasks, triggers screen captures/commands, browses memory, and receives live telemetry.
* **AlwaysOnAgent** is the desktop daemon running on the host PC: it controls the operating system, runs scheduled and background tasks, accesses local tools, and manages long-term memory.

---

## 2. Network Connectivity & Routing

DeskAI supports automatic multi-network connectivity via its **Connection Hub**:

| Network Mode | Typical Address | Description |
| :--- | :--- | :--- |
| **Local Network (Wi-Fi)** | `http://192.168.12.153:8080` | Ultra-low latency communication on the same home network. Default base URL. |
| **Tailscale / Mesh VPN** | `http://100.x.y.z:8080` | Encrypted peer-to-peer connection for access away from home (cellular / 5G). |
| **Cloudflare Tunnel / DDNS** | `https://agent.yourdomain.com` | HTTPS reverse-proxy alternative for remote cellular connectivity. |

### Automatic Failover
The mobile client tests the Primary (Local) endpoint first with a lightweight `/api/status` or ping check. If unreachable (e.g., user is outside on mobile data), it seamlessly switches requests to the Remote URL.

---

## 3. Authentication & Security Contract

Every request originating from DeskAI to the AlwaysOnAgent server carries the user's `HUD_AUTH_TOKEN`. 

### Required Headers & Auth Methods
AlwaysOnAgent's WebHUD middleware validates requests using any of the following:

1. **Header:** `X-HUD-Token: <HUD_AUTH_TOKEN>`
2. **Header:** `Authorization: Bearer <HUD_AUTH_TOKEN>`
3. **Cookie:** `hud_token=<HUD_AUTH_TOKEN>`
4. **Query Parameter:** `?token=<HUD_AUTH_TOKEN>`

### Desktop Agent Server Requirements:
* **CORS:** Ensure the server allows mobile origins and headers:
  ```python
  from fastapi.middleware.cors import CORSMiddleware
  app.add_middleware(
      CORSMiddleware,
      allow_origins=["*"],
      allow_credentials=True,
      allow_methods=["*"],
      allow_headers=["*"],
  )
  ```
* **Authentication Enforcement:** If `HUD_AUTH_TOKEN` is defined in `.env`, reject requests lacking a matching token with `HTTP 401 Unauthorized`.
* **Zero Token Leakage:** The Android app strictly isolates authentication headers to local/private server endpoints; it never forwards the `HUD_AUTH_TOKEN` to external endpoints like GitHub.

---

## 4. API Endpoints Specification

### 4.1 `/api/chat` (Primary Command & Dialogue Route)
* **Method:** `POST`
* **Path:** `/api/chat`
* **Content-Type:** `application/json`

#### Request Body
```json
{
  "message": "Check my unread emails and summarize the top 3.",
  "model": "auto",
  "engine": "auto",
  "conversation_id": "optional-uuid"
}
```

#### Response Body
```json
{
  "success": true,
  "reply": "You have 3 unread emails: 1 from GitHub, 1 from AWS billing, and 1 from calendar.",
  "model": "gemini-2.5-flash",
  "agent_status": "idle",
  "task_id": null
}
```

### 4.2 `/api/status` (Health & Telemetry)
* **Method:** `GET`
* **Path:** `/api/status`

#### Response Body
```json
{
  "status": "online",
  "version": "2.1",
  "uptime_seconds": 86400,
  "cpu_percent": 12.4,
  "memory_percent": 45.2,
  "active_model": "claude-3-7-sonnet",
  "active_tasks_count": 1
}
```

### 4.3 `/api/tasks` & `/api/tasks/run` (Task Management)
* **GET `/api/tasks`**: Returns a list of pending, running, and recent background tasks.
* **POST `/api/tasks/run`**: Accepts `{ "task_id": "...", "command": "..." }` to trigger an agent task approved by the user on mobile.
* **POST `/api/tasks/cancel`**: Halts an executing background task.

### 4.4 `/api/memory` (Knowledge & Context Retrieval)
* **Method:** `GET`
* **Path:** `/api/memory?query=...`
* Returns agent memory entries, contextual notes, and facts stored in the desktop agent's SQLite WAL database.

---

## 5. Mobile In-App OTA Update System

DeskAI features a zero-friction Over-The-Air (OTA) updater that allows users to receive builds without manual USB debugging or sideloading.

### Update Workflow:
1. **Manifest Polling:**
   * DeskAI queries `https://raw.githubusercontent.com/Dzg0507/Desk-ai/main/web_dist/version.json`.
   * Cache-busted with dynamic timestamps to avoid stale GitHub CDN caching.
2. **Version Evaluation:**
   * Compares `versionCode` in the manifest against `BuildConfig.VERSION_CODE`.
   * If `remoteVersionCode > localVersionCode`, the UI prompts: **"Download & Install Update"**.
3. **Direct Binary Download:**
   * Directly downloads the fresh APK from `https://raw.githubusercontent.com/Dzg0507/Desk-ai/main/DeskAI.apk`.
   * Saves to the secure application cache directory (`context.cacheDir/apks/DeskAI-update.apk`).
4. **Android PackageInstaller Handoff:**
   * Uses `FileProvider` (`content://com.aistudio.deskai.kzpwqm.fileprovider/cached_apks/DeskAI-update.apk`).
   * Explicitly grants URI read permissions to system package installers (`com.google.android.packageinstaller`, `com.android.packageinstaller`, `com.samsung.android.packageinstaller`, `com.miui.packageinstaller`).
   * Requires package visibility queries declared in `AndroidManifest.xml`.
5. **Seamless In-Place Update:**
   * Because all repository builds are signed with the project's consistent `debug.keystore`, Android updates the existing app in-place without data loss.

---

## 6. Android Client Persistence Architecture

DeskAI utilizes dual-layer credential and preference persistence to ensure credentials survive updates, device reboots, and process recreation:

1. **Primary Layer:** Standard `SharedPreferences` (`desk_ai_secure_prefs`) for fast synchronous access during application startup (`MainActivity.onCreate`).
2. **Secondary Layer:** Local cached storage backing the `BridgeConfig` state.
3. **Persisted Attributes:**
   * `server_url` (Primary connection string, e.g., `http://192.168.12.153:8080`)
   * `remote_url` (Tailscale / Cloudflare tunnel URL)
   * `api_key` (`HUD_AUTH_TOKEN`)
   * Selected AI model and UI theme preferences

---

## 7. Guidelines for the Desktop AI Agent

When implementing or modifying features on the host computer (`AlwaysOnAgent`):

1. **Preserve Endpoint Contracts:** Do not break the request/response structure of `/api/chat` and `/api/status`. DeskAI expects JSON objects containing `reply`, `success`, and `status`.
2. **Support Token Auth on All Routes:** Any new route added for the mobile app must pass through the WebHUD authentication middleware check.
3. **Do Not Serve Outdated APKs:** If serving APK downloads from the PC WebHUD, ensure the files in the PC's directory match the latest repository build, or defer to GitHub distribution.
4. **Handle Long-Running Commands Gracefully:** For operations taking more than 15 seconds, return an immediate acknowledgment with a `task_id` so the mobile app can poll `/api/tasks` rather than holding an open HTTP socket indefinitely.
