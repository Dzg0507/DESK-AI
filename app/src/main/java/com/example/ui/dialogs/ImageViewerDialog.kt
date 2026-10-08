package com.example.ui.dialogs

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.ElectricCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import com.example.ui.theme.Red400
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun ImageViewerDialog(
    imageUrl: String,
    altText: String,
    authToken: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isSaving by remember { mutableStateOf(false) }

    val imageRequest = remember(imageUrl, authToken) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .apply {
                if (authToken.isNotBlank()) {
                    addHeader("X-HUD-Token", authToken.trim())
                    addHeader("Authorization", "Bearer ${authToken.trim()}")
                }
            }
            .build()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF030712))
        ) {
            // Interactive Zoomable Image Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(0.8f, 7.0f)
                            scale = newScale
                            if (newScale > 1.0f) {
                                offset = Offset(
                                    x = offset.x + pan.x,
                                    y = offset.y + pan.y
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = altText.ifBlank { "Full Image" },
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = ElectricCyan, strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Loading high-res image...", color = Slate400, fontSize = 12.sp)
                            }
                        }
                    },
                    error = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⚠️", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Unable to load image stream", color = Red400, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Tap here to open in browser",
                                    color = ElectricCyan,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl)))
                                        } catch (_: Exception) {}
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit
                )
            }

            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate900.copy(alpha = 0.88f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = altText.ifBlank { "AI Generated Image" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = "Double-tap or pinch to zoom",
                        fontSize = 11.sp,
                        color = Slate400,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Save to Gallery Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate800)
                            .clickable(enabled = !isSaving) {
                                isSaving = true
                                scope.launch {
                                    saveImageToDevice(context, imageUrl, authToken, altText)
                                    isSaving = false
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isSaving) "Saving..." else "💾 Save",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    }

                    // Share Button
                    IconButton(
                        onClick = {
                            scope.launch {
                                shareImage(context, imageUrl, authToken, altText)
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Slate300,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Open in Browser
                    IconButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl)))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in Browser",
                            tint = Slate300,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Bottom Zoom HUD / Controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Slate800.copy(alpha = 0.92f))
                    .border(1.dp, Slate700, RoundedCornerShape(30.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Zoom Out Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Slate900)
                        .clickable {
                            val next = (scale - 0.5f).coerceAtLeast(1f)
                            scale = next
                            if (next == 1f) offset = Offset.Zero
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Current Zoom Level Badge
                Text(
                    text = "${(scale * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(46.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Zoom In Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Slate900)
                        .clickable {
                            scale = (scale + 0.5f).coerceAtMost(6.0f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Reset Button
                if (scale != 1f || offset != Offset.Zero) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate900)
                            .clickable {
                                scale = 1f
                                offset = Offset.Zero
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = Slate400,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("1x", fontSize = 11.sp, color = Slate400)
                        }
                    }
                }
            }
        }
    }
}

internal suspend fun saveImageToDevice(
    context: Context,
    imageUrl: String,
    authToken: String,
    altText: String
) = withContext(Dispatchers.IO) {
    try {
        val reqBuilder = Request.Builder().url(imageUrl)
        if (authToken.isNotBlank()) {
            reqBuilder.addHeader("X-HUD-Token", authToken.trim())
            reqBuilder.addHeader("Authorization", "Bearer ${authToken.trim()}")
        }
        val client = OkHttpClient()
        val response = client.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download failed (HTTP ${response.code})", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }
        val bytes = response.body?.bytes() ?: return@withContext
        val fileName = "deskai_${System.currentTimeMillis()}.jpg"

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DeskAI")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Saved to Pictures/DeskAI!", Toast.LENGTH_LONG).show()
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Could not open storage stream", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Save error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun shareImage(
    context: Context,
    imageUrl: String,
    authToken: String,
    altText: String
) = withContext(Dispatchers.IO) {
    try {
        val reqBuilder = Request.Builder().url(imageUrl)
        if (authToken.isNotBlank()) {
            reqBuilder.addHeader("X-HUD-Token", authToken.trim())
            reqBuilder.addHeader("Authorization", "Bearer ${authToken.trim()}")
        }
        val client = OkHttpClient()
        val response = client.newCall(reqBuilder.build()).execute()
        if (response.isSuccessful) {
            val bytes = response.body?.bytes()
            if (bytes != null) {
                val cacheImages = File(context.cacheDir, "images").apply { mkdirs() }
                val cacheFile = File(cacheImages, "share_${System.currentTimeMillis()}.jpg")
                cacheFile.writeBytes(bytes)

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, altText.ifBlank { "Generated by AlwaysOnAgent" })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                }
                return@withContext
            }
        }
    } catch (_: Exception) {}

    // Fallback: share URL text
    withContext(Dispatchers.Main) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, imageUrl)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Image Link"))
        } catch (_: Exception) {}
    }
}
