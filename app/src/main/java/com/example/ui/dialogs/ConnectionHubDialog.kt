package com.example.ui.dialogs

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.BridgeConfig
import com.example.data.model.BridgeProtocol
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.RoseError
import kotlinx.coroutines.launch
import com.example.ui.theme.ElectricCyanGlow
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SlateDarkSurface
import com.example.ui.components.deskSheet
import com.example.ui.components.deskCard

@Composable
fun ConnectionHubDialog(
    config: BridgeConfig,
    onSaveConfig: (BridgeConfig) -> Unit,
    onTestPing: suspend (BridgeConfig) -> Triple<Boolean, Long, String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("desk_ai_credentials", Context.MODE_PRIVATE) }
    var localUrl by remember {
        val savedServer = prefs.getString("server_url", null)
        val initial = if (config.serverUrl.isNotBlank() && config.serverUrl != "http://10.0.2.2:8080" && config.serverUrl != "http://192.168.12.153:8080") {
            config.serverUrl
        } else {
            savedServer ?: "http://192.168.12.151:8080"
        }
        mutableStateOf(initial.replace("192.168.12.2.246", "192.168.12.246"))
    }
    var remoteUrl by remember {
        val savedRemote = prefs.getString("remote_url", null)
        val initial = if (config.remoteUrl.isNotBlank() && config.remoteUrl != "http://100.111.163.124:8080") {
            config.remoteUrl
        } else {
            savedRemote ?: "http://100.109.85.92:8080"
        }
        mutableStateOf(initial)
    }
    var token by remember {
        val savedToken = prefs.getString("api_key", null)
        val initial = if (config.apiKey.isNotBlank()) config.apiKey else (savedToken ?: "")
        mutableStateOf(initial)
    }
    var selectedProtocol by remember { mutableStateOf(config.protocol) }
    var engine by remember { mutableStateOf(config.selectedModel) }

    var showRemoteGuide by remember { mutableStateOf(false) }
    var isPinging by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<Triple<Boolean, Long, String>?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .deskSheet()
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lan,
                            contentDescription = "Connection Hub",
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connection Hub",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Slate400
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Paste a server link (http://host:8080/?token=...) from the clipboard
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Slate800)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable {
                            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipMgr.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (clipText.isNotBlank()) {
                                val (cleanUrl, parsedToken) = BridgeConfig.parseImportUrl(clipText)
                                localUrl = cleanUrl
                                if (!parsedToken.isNullOrBlank()) {
                                    token = parsedToken
                                }
                                Toast.makeText(context, "Parsed connection link from clipboard!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard is empty. Copy the server link (with ?token=) first.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = ElectricCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Paste Server Link",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 1: Home Network (Local LAN)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = "Home", tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HOME WI-FI / LOCAL LAN URL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate400,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = localUrl,
                    onValueChange = { localUrl = it.replace("192.168.12.2.246", "192.168.12.246") },
                    placeholder = { Text("e.g. http://192.168.1.100:8080 or http://10.0.2.2:8080", color = Slate500, fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Slate700,
                        focusedContainerColor = Slate800,
                        unfocusedContainerColor = Slate800
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: Away From Home (Remote / Cloud)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Public, contentDescription = "Remote", tint = NeonIndigo, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AWAY FROM HOME (REMOTE URL)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonIndigo,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Help toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showRemoteGuide = !showRemoteGuide }
                    ) {
                        Icon(imageVector = Icons.Default.HelpOutline, contentDescription = "Help", tint = ElectricCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Setup Guide", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = remoteUrl,
                    onValueChange = { remoteUrl = it },
                    placeholder = { Text("e.g. http://100.x.y.z:8080 or https://tunnel.trycloudflare.com", color = Slate500, fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonIndigo,
                        unfocusedBorderColor = Slate700,
                        focusedContainerColor = Slate800,
                        unfocusedContainerColor = Slate800
                    ),
                    singleLine = true
                )
                Text(
                    text = "Tailscale uses http:// (not https) with :8080 (e.g. http://100.x.y.z:8080)",
                    fontSize = 10.sp,
                    color = Slate400,
                    modifier = Modifier.padding(top = 2.dp, start = 2.dp)
                )

                // Expandable Remote Guide
                AnimatedVisibility(visible = showRemoteGuide) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateDarkSurface)
                            .border(1.dp, NeonIndigo.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "🌐 3 Easy Ways to Connect Away from Home:",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1. **Tailscale (Recommended & Easiest):** Install free Tailscale on your PC & phone. Enter your PC's 100.x.y.z IP: `http://100.x.y.z:8080`. No router setup required!\n\n" +
                                        "2. **Cloudflare Tunnel (`cloudflared`):** Run `cloudflared tunnel --url http://localhost:8080`. Gives you a free secure HTTPS URL.\n\n" +
                                        "3. **ngrok:** Run `ngrok http 8080` on your PC and paste the `https://...` URL here.",
                                color = Slate300,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 3: Auth Token
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HUD AUTH TOKEN (HUD_AUTH_TOKEN)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate400
                    )

                    // Quick generate helper
                    Text(
                        text = "Generate New Token",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            val hexChars = "0123456789abcdef"
                            val genToken = (1..32).map { hexChars.random() }.joinToString("")
                            token = genToken
                            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipMgr.setPrimaryClip(android.content.ClipData.newPlainText("env", "HUD_AUTH_TOKEN=$genToken"))
                            Toast.makeText(context, "Copied 'HUD_AUTH_TOKEN=$genToken' to clipboard! Paste into your PC .env", Toast.LENGTH_LONG).show()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    placeholder = { Text("Required for phone access. Matches HUD_AUTH_TOKEN in PC .env", color = Slate500, fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Slate700,
                        focusedContainerColor = Slate800,
                        unfocusedContainerColor = Slate800
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Engine Selector
                Text(
                    text = "EXECUTION ENGINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("auto", "antigravity", "cloud", "ollama").forEach { item ->
                        val isSelected = engine == item
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ElectricCyan.copy(alpha = 0.2f) else Slate800)
                                .border(1.dp, if (isSelected) ElectricCyan else Slate700, RoundedCornerShape(8.dp))
                                .clickable { engine = item }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) ElectricCyan else Slate400,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ping Test Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .deskCard()
                        .clickable(enabled = !isPinging) {
                            scope.launch {
                                isPinging = true
                                val normalizedLocal = BridgeConfig.normalizeUrl(localUrl)
                                val normalizedRemote = if (remoteUrl.isNotBlank()) BridgeConfig.normalizeUrl(remoteUrl) else ""
                                localUrl = normalizedLocal
                                if (remoteUrl.isNotBlank()) remoteUrl = normalizedRemote
                                val testConfig = config.copy(
                                    serverUrl = normalizedLocal,
                                    remoteUrl = normalizedRemote,
                                    apiKey = token.trim(),
                                    selectedModel = engine
                                )
                                pingResult = onTestPing(testConfig)
                                isPinging = false
                            }
                        }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPinging) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.NetworkPing,
                                contentDescription = "Ping",
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPinging) "Testing Connection..." else "Test Connection (Auto-Detect Local / Remote)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    }
                }

                if (pingResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val (success, latency, details) = pingResult!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (success) EmeraldConnected.copy(alpha = 0.12f) else RoseError.copy(alpha = 0.12f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = if (success) "✓" else "✕", color = if (success) EmeraldConnected else RoseError, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (success) "${latency}ms • $details" else details,
                            color = if (success) EmeraldConnected else RoseError,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // App Updates OTA Section
                var showUpdaterFromHub by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Slate800)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { showUpdaterFromHub = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("In-App OTA App Updater", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Text("CHECK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectricCyan)
                }

                if (showUpdaterFromHub) {
                    AppUpdaterDialog(
                        serverBaseUrl = BridgeConfig.normalizeUrl(localUrl),
                        authToken = token.trim(),
                        onDismiss = { showUpdaterFromHub = false }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricCyanGlow)
                        .clickable {
                            val normalizedLocal = BridgeConfig.normalizeUrl(localUrl)
                            val normalizedRemote = if (remoteUrl.isNotBlank()) BridgeConfig.normalizeUrl(remoteUrl) else ""
                            try {
                                prefs.edit()
                                    .putString("server_url", normalizedLocal)
                                    .putString("remote_url", normalizedRemote)
                                    .putString("api_key", token.trim())
                                    .putString("selected_model", engine)
                                    .putString("protocol", selectedProtocol)
                                    .apply()
                            } catch (_: Exception) {}
                            onSaveConfig(
                                config.copy(
                                    serverUrl = normalizedLocal,
                                    remoteUrl = normalizedRemote,
                                    apiKey = token.trim(),
                                    selectedModel = engine
                                )
                            )
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save Bridge Configuration",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
