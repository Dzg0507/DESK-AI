package com.example.ui.dialogs

import com.example.ui.theme.DeskShapes
import com.example.ui.components.SheetHeader
import com.example.ui.components.deskCard
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import com.example.ui.theme.Gray800
import com.example.ui.theme.Gray900
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.components.deskSheet

enum class UpdateState {
    CHECKING,
    UPDATE_AVAILABLE,
    UP_TO_DATE,
    DOWNLOADING,
    READY_TO_INSTALL,
    PERMISSION_REQUIRED,
    ERROR
}

@Composable
fun AppUpdaterDialog(
    serverBaseUrl: String,
    authToken: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val defaultUpdateUrl = remember {
        "https://raw.githubusercontent.com/Dzg0507/Desk-ai/main/DeskAI.apk"
    }

    var downloadUrl by remember { mutableStateOf(defaultUpdateUrl) }
    var updateState by remember { mutableStateOf(UpdateState.CHECKING) }
    var statusMessage by remember { mutableStateOf("Checking for latest updates...") }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var showCustomUrl by remember { mutableStateOf(false) }

    // Version metadata
    var remoteVersionCode by remember { mutableIntStateOf(BuildConfig.VERSION_CODE) }
    var remoteVersionName by remember { mutableStateOf(BuildConfig.VERSION_NAME) }
    var remoteChangelog by remember { mutableStateOf<String?>(null) }
    var remoteFileSize by remember { mutableLongStateOf(0L) }

    fun downloadApkFile(targetUrl: String) {
        scope.launch {
            updateState = UpdateState.DOWNLOADING
            progress = 0f
            downloadedBytes = 0L
            totalBytes = 0L
            statusMessage = "Connecting to download server..."

            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(90, TimeUnit.SECONDS)
                        .build()

                    val githubMain = "https://raw.githubusercontent.com/Dzg0507/Desk-ai/main/DeskAI.apk"
                    val githubMaster = "https://raw.githubusercontent.com/Dzg0507/Desk-ai/master/DeskAI.apk"
                    val candidateUrls = mutableListOf<String>()
                    if (targetUrl.startsWith("http")) {
                        candidateUrls.add(targetUrl)
                    }
                    if (!candidateUrls.contains(githubMain)) candidateUrls.add(githubMain)
                    if (!candidateUrls.contains(githubMaster)) candidateUrls.add(githubMaster)

                    var successfulResponse: okhttp3.Response? = null
                    var usedUrl = targetUrl

                    for (rawUrl in candidateUrls) {
                        try {
                            val isLocal = !rawUrl.contains("github")
                            // The agent gets the token in the headers below, never in the URL (2026-10-07)
                            val url = if (isLocal) {
                                com.example.data.remote.MediaAuth.stripToken(rawUrl)
                            } else if (rawUrl.contains("github") && !rawUrl.contains("t=")) {
                                val sep = if (rawUrl.contains("?")) "&" else "?"
                                "$rawUrl${sep}t=${System.currentTimeMillis()}"
                            } else {
                                rawUrl
                            }
                            withContext(Dispatchers.Main) {
                                statusMessage = "Connecting: " + url.take(38) + "..."
                            }
                            val reqBuilder = Request.Builder().url(url)
                            reqBuilder.header("User-Agent", "DeskAI-Android")
                            if (authToken.isNotBlank() && isLocal) {
                                reqBuilder.addHeader("X-HUD-Token", authToken.trim())
                                reqBuilder.addHeader("Cookie", "hud_token=${authToken.trim()}")
                                reqBuilder.addHeader("Authorization", "Bearer " + authToken.trim())
                            }
                            val resp = client.newCall(reqBuilder.build()).execute()
                            val cType = resp.header("Content-Type", "")?.lowercase() ?: ""
                            val cLen = resp.body?.contentLength() ?: 0L
                            val isHtmlOrJson = cType.contains("html") || cType.contains("json")

                            if (resp.isSuccessful && resp.body != null && !isHtmlOrJson && (cLen > 1000000L || cLen == -1L)) {
                                successfulResponse = resp
                                usedUrl = url
                                break
                            } else {
                                resp.close()
                            }
                        } catch (_: Exception) {
                            // Try next candidate
                        }
                    }

                    if (successfulResponse == null) {
                        withContext(Dispatchers.Main) {
                            updateState = UpdateState.ERROR
                            statusMessage = "APK file not found on server. Ensure DeskAI.apk is in your PC agent folder or tap Use Cloud URL."
                        }
                        return@withContext
                    }

                    val body = successfulResponse.body
                    if (body == null) {
                        withContext(Dispatchers.Main) {
                            updateState = UpdateState.ERROR
                            statusMessage = "Empty response received from update server."
                        }
                        return@withContext
                    }

                    val length = body.contentLength()
                    withContext(Dispatchers.Main) {
                        totalBytes = length
                        val mbSize = if (length > 0) (length / (1024 * 1024)).toString() + " MB" else ""
                        statusMessage = if (length > 0) "Downloading update ($mbSize)..." else "Downloading update..."
                    }

                    val apkDir = File(context.cacheDir, "apks").apply { mkdirs() }
                    val apkFile = File(apkDir, "DeskAI-update.apk")
                    if (apkFile.exists()) apkFile.delete()

                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(apkFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (length > 0) {
                            val currentProgress = totalRead.toFloat() / length
                            withContext(Dispatchers.Main) {
                                downloadedBytes = totalRead
                                progress = currentProgress
                            }
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    try { apkFile.setReadable(true, false) } catch (_: Exception) {}

                    if (totalRead < 1000000L) {
                        withContext(Dispatchers.Main) {
                            updateState = UpdateState.ERROR
                            val kb = (totalRead / 1024).toString()
                            statusMessage = "Download incomplete ($kb KB). Please check your connection and retry."
                        }
                        return@withContext
                    }

                    withContext(Dispatchers.Main) {
                        downloadedFile = apkFile
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            if (!context.packageManager.canRequestPackageInstalls()) {
                                updateState = UpdateState.PERMISSION_REQUIRED
                                statusMessage = "Download complete! Android requires permission to install updates."
                            } else {
                                updateState = UpdateState.READY_TO_INSTALL
                                val mb = (totalRead / (1024 * 1024)).toString()
                                statusMessage = "Update downloaded ($mb MB). Ready to install!"
                                launchInstaller(context, apkFile)
                            }
                        } else {
                            updateState = UpdateState.READY_TO_INSTALL
                            statusMessage = "Update downloaded. Ready to install!"
                            launchInstaller(context, apkFile)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        updateState = UpdateState.ERROR
                        statusMessage = "Download error: " + (e.localizedMessage ?: "Connection interrupted")
                    }
                }
            }
        }
    }

    fun checkForUpdates() {
        scope.launch {
            updateState = UpdateState.CHECKING
            statusMessage = "Checking server for latest version..."

            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(8, TimeUnit.SECONDS)
                        .readTimeout(8, TimeUnit.SECONDS)
                        .build()

                    // The same check as the badge (UpdateSource): version.json and the APK read at the newest commit,
                    // which no cache can serve an old copy of
                    val latest = com.example.data.remote.UpdateSource.fetch(client)
                    val parsedJson: JSONObject? = latest?.json

                    if (parsedJson != null) {
                        val vCode = parsedJson.optInt("versionCode", 1)
                        val vName = parsedJson.optString("versionName", "1.0")
                        val changelog = parsedJson.optString("changelog", "")
                        val dlUrl = parsedJson.optString("downloadUrl", "")
                        val fSize = parsedJson.optLong("fileSizeBytes", 0L)

                        withContext(Dispatchers.Main) {
                            remoteVersionCode = vCode
                            remoteVersionName = vName
                            remoteChangelog = changelog.ifBlank { null }
                            remoteFileSize = fSize

                            val pinnedApk = latest?.apkUrl
                            if (pinnedApk != null) {
                                downloadUrl = pinnedApk                 // exactly this version's APK
                            } else if (dlUrl.isNotBlank()) {
                                if (dlUrl.startsWith("http")) {
                                    downloadUrl = dlUrl
                                } else {
                                    downloadUrl = "https://raw.githubusercontent.com/Dzg0507/Desk-ai/main/$dlUrl"
                                }
                            }

                            if (vCode > BuildConfig.VERSION_CODE) {
                                updateState = UpdateState.UPDATE_AVAILABLE
                                statusMessage = "New update available: v$vName (Build $vCode)"
                            } else {
                                updateState = UpdateState.UP_TO_DATE
                                statusMessage = "DeskAI is up to date (v${BuildConfig.VERSION_NAME})"
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            updateState = UpdateState.ERROR
                            statusMessage = "Could not find version manifest on server. You can check again or download directly."
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        updateState = UpdateState.ERROR
                        statusMessage = "Version check error: " + (e.localizedMessage ?: "Connection timed out")
                    }
                }
            }
        }
    }

    LaunchedEffect(serverBaseUrl) {
        checkForUpdates()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .deskSheet()
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                SheetHeader(
                    title = "In-App Updater",
                    icon = Icons.Default.SystemUpdate,
                    subtitle = "New builds straight from your host",
                    onClose = onDismiss
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Installed Version Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .deskCard()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Installed Version",
                                fontSize = 11.sp,
                                color = Slate400
                            )
                            Text(
                                text = "DeskAI v" + BuildConfig.VERSION_NAME + " (Build " + BuildConfig.VERSION_CODE + ")",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(DeskShapes.chip)
                                .background(
                                    when (updateState) {
                                        UpdateState.UP_TO_DATE -> EmeraldConnected.copy(alpha = 0.2f)
                                        UpdateState.UPDATE_AVAILABLE -> ElectricCyan.copy(alpha = 0.2f)
                                        else -> Slate700
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = when (updateState) {
                                    UpdateState.UP_TO_DATE -> "LATEST"
                                    UpdateState.UPDATE_AVAILABLE -> "UPDATE NEEDED"
                                    UpdateState.CHECKING -> "CHECKING..."
                                    UpdateState.DOWNLOADING -> "UPDATING"
                                    UpdateState.READY_TO_INSTALL -> "DOWNLOADED"
                                    else -> "STATUS"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (updateState) {
                                    UpdateState.UP_TO_DATE -> EmeraldConnected
                                    UpdateState.UPDATE_AVAILABLE -> ElectricCyan
                                    else -> Color.White
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Banner based on state
                if (updateState == UpdateState.UPDATE_AVAILABLE) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DeskShapes.control)
                            .background(ElectricCyan.copy(alpha = 0.12f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), DeskShapes.control)
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NewReleases,
                                    contentDescription = "New Release",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "New Update Available: v$remoteVersionName (Build $remoteVersionCode)",
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            remoteChangelog?.let { log ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = log,
                                    color = Slate200,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            if (remoteFileSize > 0L) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val mb = (remoteFileSize / (1024 * 1024)).toString()
                                Text(
                                    text = "Package size: ~$mb MB",
                                    color = Slate400,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (updateState == UpdateState.UP_TO_DATE) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DeskShapes.control)
                            .background(EmeraldConnected.copy(alpha = 0.12f))
                            .border(1.dp, EmeraldConnected.copy(alpha = 0.4f), DeskShapes.control)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Up to date",
                                tint = EmeraldConnected,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "DeskAI is Up to Date!",
                                    color = EmeraldConnected,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "You are running the newest version (v" + BuildConfig.VERSION_NAME + "). No update is needed.",
                                    color = Slate300,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Status Message Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DeskShapes.control)
                        .background(
                            when (updateState) {
                                UpdateState.READY_TO_INSTALL -> EmeraldConnected.copy(alpha = 0.12f)
                                UpdateState.ERROR -> RoseError.copy(alpha = 0.12f)
                                else -> Gray900
                            }
                        )
                        .border(
                            1.dp,
                            when (updateState) {
                                UpdateState.READY_TO_INSTALL -> EmeraldConnected.copy(alpha = 0.4f)
                                UpdateState.ERROR -> RoseError.copy(alpha = 0.4f)
                                else -> Gray800
                            },
                            DeskShapes.control
                        )
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (updateState == UpdateState.DOWNLOADING || updateState == UpdateState.CHECKING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = statusMessage,
                            color = when (updateState) {
                                UpdateState.READY_TO_INSTALL -> EmeraldConnected
                                UpdateState.ERROR -> RoseError
                                else -> Slate300
                            },
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Progress Bar (when downloading)
                if (updateState == UpdateState.DOWNLOADING) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = ElectricCyan,
                            trackColor = Slate800,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = ((progress * 100).toInt()).toString() + "%",
                                fontSize = 11.sp,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            val downMb = (downloadedBytes / (1024 * 1024)).toString()
                            val totMb = (totalBytes / (1024 * 1024)).toString()
                            Text(
                                text = "$downMb MB / $totMb MB",
                                fontSize = 11.sp,
                                color = Slate400,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = ElectricCyan,
                            trackColor = Slate800,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom URL accordion
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomUrl = !showCustomUrl }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showCustomUrl) "▾ Update URL / Source" else "▸ Update URL / Source",
                        fontSize = 12.sp,
                        color = Slate400
                    )
                    Text(
                        text = "Customize",
                        fontSize = 11.sp,
                        color = ElectricCyan
                    )
                }

                if (showCustomUrl) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = downloadUrl,
                        onValueChange = { downloadUrl = it },
                        label = { Text("APK Download URL", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { downloadUrl = "https://ais-dev-q3rsvd2z4tkbdyypw443cl-695475584713.us-east1.run.app/DeskAI.apk" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                        ) {
                            Text("Use Cloud URL", fontSize = 11.sp, color = ElectricCyan)
                        }
                        OutlinedButton(
                            onClick = { downloadUrl = defaultUpdateUrl },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate400)
                        ) {
                            Text("Reset Local", fontSize = 11.sp, color = Slate400)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons based on state
                when (updateState) {
                    UpdateState.CHECKING -> {
                        Button(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(disabledContainerColor = Slate800)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking for updates...", color = Slate400)
                        }
                    }

                    UpdateState.UPDATE_AVAILABLE -> {
                        Button(
                            onClick = { downloadApkFile(downloadUrl) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Download & Install Update (v$remoteVersionName)",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    UpdateState.UP_TO_DATE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { checkForUpdates() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Slate800)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check Again", color = Color.White)
                            }
                            OutlinedButton(
                                onClick = { downloadApkFile(downloadUrl) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate400)
                            ) {
                                Text("Force Re-install", color = Slate400, fontSize = 11.sp)
                            }
                        }
                    }

                    UpdateState.DOWNLOADING -> {
                        Button(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(disabledContainerColor = Slate800)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Downloading Update...", color = Slate400)
                        }
                    }

                    UpdateState.PERMISSION_REQUIRED -> {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                        data = Uri.parse("package:" + context.packageName)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                        ) {
                            Icon(imageVector = Icons.Default.InstallMobile, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Install Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        if (downloadedFile != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { launchInstaller(context, downloadedFile!!) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldConnected)
                            ) {
                                Text("Try Installing Now", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    UpdateState.READY_TO_INSTALL -> {
                        Button(
                            onClick = {
                                downloadedFile?.let { launchInstaller(context, it) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldConnected)
                        ) {
                            Icon(imageVector = Icons.Default.InstallMobile, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Install Update Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    UpdateState.ERROR -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { checkForUpdates() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Slate800)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Check", color = Color.White)
                            }
                            Button(
                                onClick = { downloadApkFile(downloadUrl) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                            ) {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Direct Install", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun launchInstaller(context: Context, apkFile: File) {
    try {
        if (!apkFile.exists() || apkFile.length() < 1000000L) {
            Toast.makeText(context, "Error: APK file incomplete (" + apkFile.length() + " bytes)", Toast.LENGTH_LONG).show()
            return
        }
        val apkUri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val standardInstallers = listOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.coloros.packageinstaller",
            "com.oppo.packageinstaller"
        )
        for (pkg in standardInstallers) {
            try {
                context.grantUriPermission(pkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) PackageManager.MATCH_UNINSTALLED_PACKAGES else 0
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(installIntent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(installIntent, flags)
        }
        for (resolveInfo in resolveList) {
            try {
                val pkg = resolveInfo.activityInfo.packageName
                context.grantUriPermission(pkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
        }

        context.startActivity(installIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open installer: " + (e.localizedMessage ?: "Unknown error"), Toast.LENGTH_LONG).show()
    }
}
