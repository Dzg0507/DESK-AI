# AlwaysOnAgent /api/chat Integration Specification

This document provides complete instructions, architecture, and code for an AI or developer to automatically implement the `/api/chat` endpoint on a host machine (e.g., Python FastAPI/Flask or Node.js) for seamless integration with the DeskAI mobile companion.

---

## 1. Overview & Protocol Specification

The DeskAI client communicates with the AlwaysOnAgent server over HTTP / REST.

* **Method:** `POST`
* **Path:** `/api/chat`
* **Content-Type:** `application/json`
* **Default Port:** `8080` (e.g., `http://127.0.0.1:8080/api/chat`)

### Authentication
DeskAI supports three ways to authenticate with `HUD_AUTH_TOKEN`:
1. **Header:** `Authorization: Bearer <HUD_AUTH_TOKEN>`
2. **Header:** `X-HUD-Token: <HUD_AUTH_TOKEN>`
3. **URL Query Param:** `http://...:8080/api/chat?token=<HUD_AUTH_TOKEN>`

If `HUD_AUTH_TOKEN` is set on the server, reject unauthorized requests with **HTTP 401 Unauthorized**. If `HUD_AUTH_TOKEN` is empty or unset in `.env`, allow local requests.

---

## 2. Request & Response Formats

### Request Body (`application/json`)
```json
{
  "message": "Hello agent, what is the status of my tasks?",
  "model": "auto",
  "engine": "auto",
  "conversation_id": "optional-session-uuid"
}
```

* `message` *(string, required)*: The user's prompt, command, or question.
* `model` / `engine` *(string, optional)*: Chosen engine (e.g. `"auto"`, `"gemini-2.5-flash"`, `"claude-3-7-sonnet"`, `"gpt-4o"`, `"local"`).
* `conversation_id` *(string, optional)*: Identifier for continuing multi-turn dialogue.

### Response Body (`application/json`)
```json
{
  "success": true,
  "reply": "All background tasks are currently idle. Memory contains 12 active facts.",
  "model": "gemini-2.5-flash",
  "agent_status": "idle",
  "task_id": null
}
```

* `reply` *(string, required)*: The text output from the agent/LLM to display in the chat bubble.
* `success` *(boolean, optional, defaults to true)*: Status flag.
* `agent_status` *(string, optional)*: `"idle"`, `"working"`, `"waiting_for_approval"`, or `"error"`.
* `task_id` *(string, optional)*: If the chat message enqueued an autonomous background task, return the task ID here.

---

## 3. Reference Implementation: Python (FastAPI)

Save this file as `agent_server.py` in your agent folder:

```python
import os
import uvicorn
from fastapi import FastAPI, Header, HTTPException, Query, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional
from dotenv import load_dotenv

load_dotenv()

HUD_AUTH_TOKEN = os.getenv("HUD_AUTH_TOKEN", "").strip()

app = FastAPI(title="AlwaysOnAgent Bridge")

# Allow requests from mobile and web clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def verify_token(
    authorization: Optional[str] = Header(None),
    x_hud_token: Optional[str] = Header(None),
    token: Optional[str] = Query(None)
):
    if not HUD_AUTH_TOKEN:
        return True # Auth disabled if no token set
    
    extracted = None
    if authorization and authorization.startswith("Bearer "):
        extracted = authorization.split("Bearer ", 1)[1].strip()
    elif x_hud_token:
        extracted = x_hud_token.strip()
    elif token:
        extracted = token.strip()
        
    if extracted != HUD_AUTH_TOKEN:
        raise HTTPException(status_code=401, detail="Unauthorized: Invalid HUD_AUTH_TOKEN")
    return True

class ChatRequest(BaseModel):
    message: str
    model: Optional[str] = "auto"
    engine: Optional[str] = "auto"
    conversation_id: Optional[str] = None

class ChatResponse(BaseModel):
    success: bool = True
    reply: str
    model: str = "auto"
    agent_status: str = "idle"
    task_id: Optional[str] = None

@app.get("/")
def health_check(_: bool = Depends(verify_token)):
    return {"status": "online", "message": "AlwaysOnAgent is running"}

@app.post("/api/chat", response_model=ChatResponse)
async def chat_endpoint(payload: ChatRequest, _: bool = Depends(verify_token)):
    user_msg = payload.message.strip()
    
    # 1. Handle Built-in Agent Quick Commands
    if user_msg.lower() == "/status":
        return ChatResponse(
            reply="Daemon is active and listening.\nCPU: Normal | Memory: SQLite OK",
            agent_status="idle"
        )
    
    # 2. Hook up your AI Model (e.g. Gemini, Anthropic, or OpenAI)
    # Example using Google GenAI or local agent execution:
    try:
        # REPLACE THIS with your actual LLM or agent invocation logic:
        agent_reply = f"Agent processed: '{user_msg}' using model {payload.model}"
        
        return ChatResponse(
            reply=agent_reply,
            model=payload.model or "auto",
            agent_status="idle"
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    # Listen on all interfaces (0.0.0.0) so LAN and Tailscale can connect
    uvicorn.run(app, host="0.0.0.0", port=8080)
```

---

## 4. Reference Implementation: Node.js (Express)

```javascript
require('dotenv').config();
const express = require('express');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

const HUD_AUTH_TOKEN = (process.env.HUD_AUTH_TOKEN || '').trim();

function authMiddleware(req, res, next) {
    if (!HUD_AUTH_TOKEN) return next();

    const authHeader = req.headers['authorization'];
    const bearer = authHeader && authHeader.startsWith('Bearer ') ? authHeader.slice(7).trim() : null;
    const token = bearer || req.headers['x-hud-token'] || req.query.token;

    if (token !== HUD_AUTH_TOKEN) {
        return res.status(401).json({ error: 'Unauthorized: Invalid HUD_AUTH_TOKEN' });
    }
    next();
}

app.use(authMiddleware);

app.get('/', (req, res) => {
    res.json({ status: 'online', message: 'AlwaysOnAgent running' });
});

app.post('/api/chat', async (req, res) => {
    const { message, model = 'auto' } = req.body;
    if (!message) {
        return res.status(400).json({ error: 'Missing message field' });
    }

    try {
        // Run your agent or LLM logic here:
        const responseText = `Agent received: "${message}" (Engine: ${model})`;

        res.json({
            success: true,
            reply: responseText,
            model: model,
            agent_status: 'idle'
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

const PORT = process.env.PORT || 8080;
app.listen(PORT, '0.0.0.0', () => {
    console.log(`AlwaysOnAgent API listening on http://0.0.0.0:${PORT}`);
});
```

---

## 5. Host Firewall & Network Binding Checklist
For DeskAI to reach this server:
1. **Bind to `0.0.0.0`**, NOT `127.0.0.1` (otherwise only local PC apps can reach it).
2. **Windows Firewall:** Allow incoming TCP traffic on Port `8080` (or run in Private network mode).
3. **Environment Variable (`.env`):**
   ```ini
   PORT=8080
   HUD_AUTH_TOKEN=your_secure_token_here
   ```
