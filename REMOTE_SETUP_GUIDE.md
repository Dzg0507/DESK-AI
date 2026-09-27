# AlwaysOnAgent & DeskAI Remote Setup Guide
=============================================

This step-by-step guide explains how to connect your **DeskAI** Android app to your **AlwaysOnAgent (v2.1)** computer assistant, both on your home Wi-Fi and **away from home (5G / cellular data)** without opening risky router ports.

---

## Architecture Overview

* **Your Computer:** Runs `AlwaysOnAgent` with the Web HUD on port `8080` (FastAPI + SQLite WAL).
* **Your Phone:** Runs `DeskAI` with a chat interface, task proposals ("▶️ Run it"), 3D video controls, memory browser, and live daemon telemetry.
* **Security:** All connections from outside `127.0.0.1` are secured with `HUD_AUTH_TOKEN` (sent via `X-HUD-Token`).

---

## Step 1: Configure Your Computer (`.env`)

1. Open your `.env` file on your computer (located in the agent's root directory).
2. Look for the `Daemon, Web HUD & Local Engines` section:
   ```ini
   HUD_HOST=0.0.0.0
   HUD_PORT=8080
   HUD_AUTH_TOKEN=
   ```
3. Set a secure token for `HUD_AUTH_TOKEN`. You can generate one in Python or use any secure passphrase:
   ```bash
   python -c "import secrets; print(secrets.token_hex(24))"
   ```
   *Example `.env` setting:*
   ```ini
   HUD_AUTH_TOKEN=a8f4c2e9105b637841d279e8c3f15049a62bcde89104fa21
   ```
   *(You can also tap **"Generate New Token"** inside DeskAI's Connection Hub to copy one to your clipboard).*

4. **Verify Windows Firewall (One-time):**
   When `python supervisor.py` runs, Windows Firewall may ask to allow Python on Private Networks. Click **Allow**.

5. Restart the agent using `run_agent.bat` (or send `/restart` in Telegram if still running).

---

## Step 2: Choose Your Remote (Away-from-Home) Connection

Choose **one** of the following options. We strongly recommend **Option A (Tailscale)** for zero-setup ease.

---

### Option A: Tailscale (Recommended — Fastest & Most Secure)

* **Cost:** 100% Free
* **Port forwarding:** None required
* **Encryption:** End-to-end WireGuard mesh

1. **Install Tailscale on Your PC:**
   * Open PowerShell or Command Prompt and run:
     ```cmd
     winget install Tailscale.Tailscale
     ```
   * Open Tailscale from your Start Menu or taskbar system tray and click **Log in...**.
   * Sign in with Google, Microsoft, or GitHub.
   * To find your PC's Tailscale IP, run in PowerShell/CMD:
     ```cmd
     tailscale ip -4
     ```
     *(It will be a `100.x.y.z` address, e.g. `100.85.120.45`)*.

2. **Install Tailscale on Your Android Phone:**
   * Install **Tailscale** from Google Play.
   * Log in with the **same account**.
   * Toggle Tailscale **Connected**.

3. **Your Away-From-Home URL is:**
   ```
   http://<YOUR_TAILSCALE_PC_IP>:8080
   ```
   *(e.g., `http://100.85.120.45:8080`)*

---

### Option B: Cloudflare Tunnel (Free HTTPS URL)

* **Cost:** 100% Free
* **Port forwarding:** None required
* **Gives:** Public HTTPS URL

1. Download the standalone `cloudflared.exe` from Cloudflare on your PC.
2. In Command Prompt or PowerShell, run:
   ```cmd
   cloudflared.exe tunnel --url http://localhost:8080
   ```
3. Look for the line in the terminal that outputs:
   ```
   https://your-random-subdomain.trycloudflare.com
   ```
4. **Your Away-From-Home URL is:**
   ```
   https://your-random-subdomain.trycloudflare.com
   ```

---

### Option C: ngrok (Quick Temporary Link)

1. Download `ngrok` on your PC.
2. Run in terminal:
   ```cmd
   ngrok http 8080
   ```
3. Copy the `https://...ngrok-free.app` forwarding address.
4. **Your Away-From-Home URL is:**
   ```
   https://your-forwarding-address.ngrok-free.app
   ```

---

## Step 3: Configure the DeskAI Android App

1. Open **DeskAI** on your phone.
2. Tap the **Settings** icon (top right) or the **Host status pill** to open the **Connection Hub**.
3. Fill in the fields:
   * **Home Wi-Fi / Local LAN URL:**
     ```
     http://192.168.1.XXX:8080
     ```
     *(Your computer's local IP on home Wi-Fi; or `http://10.0.2.2:8080` if testing in emulator).*
   * **Away from Home (Remote URL):**
     ```
     http://100.XX.YY.ZZ:8080   (if using Tailscale)
     or
     https://your-tunnel-url     (if using Cloudflare / ngrok)
     ```
   * **HUD Auth Token:**
     Paste the exact `HUD_AUTH_TOKEN` you set in Step 1.
   * **Execution Engine:**
     Select `AUTO` (Antigravity CLI -> Cloud).

4. **1-Tap Shortcut (If using Telegram currently):**
   * Send `/hud` to your Telegram bot.
   * It responds with a link: `http://...:8080/?token=...`.
   * Copy that link, open DeskAI, and tap **"Paste Link from /hud or Web HUD"**. The app will extract the URL and token automatically!

5. Tap **"Test Connection (Auto-Detect Local / Remote)"**:
   * You should see a green checkmark `✓ Connected (e.g. 14ms)`.
6. Tap **"Save Bridge Configuration"**.

---

## Step 4: Using DeskAI

### Real-Time Live Status Bar
* **🟢 ONLINE / IDLE:** Worker daemon is active and listening for missions.
* **🟡 PROCESSING MISSION:** Background agent is currently executing a task.
* **⏸️ STANDBY (SLEEP):** Daemon is paused (0% CPU/GPU). Tap **Wake** anytime to resume.

### Quick Commands (Type or tap the chips above the keyboard):
* `/status` — View host CPU, RAM, pulse, and task queue statistics.
* `/wake` — Ignite daemon from standby.
* `/standby` — Put daemon to sleep.
* `/video [optional quote]` — Render 3D card flip affirmative video.
* `/tiktok [optional quote]` — Render video and auto-post to `@Thevibecheckproject`.
* `/memory` — Inspect what AlwaysOnAgent remembers about you.
* `/remember <fact>` — Save a permanent memory to `state.db`.
* `/forget <#id>` — Permanently erase a memory.
* `/tasks` — View recent tasks, abort running tasks, or retry failed missions.
* `/engine <auto|antigravity|cloud|ollama>` — Switch default execution engine.

### Interactive Task Proposals
When chatting, if the assistant suggests a coding task, a proposal card with **"▶️ Run it"** and **"✕ Dismiss"** appears directly in the conversation. Tapping **"Run it"** enqueues the job into your PC worker pool.

---

## Step 5: Enabling Conversational Chat with Your PC Assistant

DeskAI allows you to have conversations directly with your computer's assistant (using your PC's Groq, Mistral, Cloudflare, Antigravity, or Ollama models).

When you send a message, DeskAI sends a `POST` request to your PC at:
```http
POST /api/chat
Headers:
  X-HUD-Token: <YOUR_HUD_AUTH_TOKEN>
  Content-Type: application/json
Body:
  {
    "message": "Hello!",
    "prompt": "Hello!",
    "system": "You are AlwaysOnAgent...",
    "history": [{"role": "user", "content": "..."}]
  }
```

### Adding `/api/chat` to your `WebHUD.py` (One-time, 1 Minute)
If your `WebHUD.py` doesn't have `/api/chat` mounted yet, open `WebHUD.py` in your PC workspace (`C:\Projects\AlwaysOnAgent\WebHUD.py`) and add this endpoint:

```python
from fastapi import Request
from pydantic import BaseModel
from typing import List, Optional

class ChatRequest(BaseModel):
    message: str
    prompt: Optional[str] = None
    system: Optional[str] = None
    history: Optional[List[dict]] = []

@app.post("/api/chat")
async def api_chat(req: ChatRequest, request: Request):
    # Authenticate remote client
    auth_check(request)
    
    user_query = req.message or req.prompt
    
    # Forward query to your agent's LLM engine (e.g. Antigravity CLI / Groq / Mistral)
    # or generate response using your TelegramBridge / Worker LLM router:
    try:
        from engine_router import generate_chat_response # Or your agent's chat function
        reply = await generate_chat_response(user_query, history=req.history)
        return {"response": reply}
    except Exception as e:
        return {"response": f"Processed via AlwaysOnAgent engine. Details: {str(e)}"}
```

Save and restart `run_agent.bat`. DeskAI will immediately chat with your real computer AI models!

---

## Troubleshooting

| Problem | Cause | Solution |
| :--- | :--- | :--- |
| **HTTP 401: Unauthorized** | Token mismatch or missing | Ensure `HUD_AUTH_TOKEN` in your PC `.env` exactly matches the token in DeskAI. |
| **Connection Refused / Failed** | Agent not running or firewall blocked | Make sure `python supervisor.py` is running on your PC, and Windows Firewall allows port `8080`. |
| **Works on Wi-Fi but not away** | Remote URL / Tailscale not active | Ensure Tailscale is running on both your PC and phone, or verify your Cloudflare Tunnel is active. |
| **Daemon asleep** | Status shows Standby | Tap the **Wake** button in DeskAI or send `/wake`. |
