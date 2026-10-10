package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.data.local.AppDatabase
import com.example.data.remote.AlwaysOnAgentClient
import com.example.data.remote.TaskEvents
import com.example.ui.screens.ChatScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private var openTaskIdState by mutableStateOf<String?>(null)
  private var openNeedsYouState by mutableStateOf<String?>(null)
  // Bumped when something is shared to DeskAI (ShareReceiverActivity): the chat picks it up from SharedInbox
  private var sharedTick by mutableStateOf(0)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    openTaskIdState = intent?.getStringExtra("OPEN_TASK_ID")
    openNeedsYouState = intent?.getStringExtra(com.example.service.NeedsYou.EXTRA_OPEN)
    if (intent?.getBooleanExtra(SharedInbox.EXTRA_SHARED, false) == true) sharedTick++
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme(darkTheme = true) {
        val launcher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission(),
          onResult = { isGranted ->
            Log.d("MainActivity", "Notification permission granted: $isGranted")
          }
        )

        LaunchedEffect(Unit) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
              this@MainActivity,
              Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
              launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
          }

          // Register current FCM device token to AlwaysOnAgent host
          try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
              if (task.isSuccessful) {
                val token = task.result
                Log.d("MainActivity", "Fetched FCM token: $token")
                CoroutineScope(Dispatchers.IO).launch {
                  try {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val config = db.bridgeConfigDao().getConfigSync() ?: return@launch
                    val client = AlwaysOnAgentClient()
                    client.registerPushToken(config, token, Build.MODEL ?: "Android Companion")
                  } catch (e: Exception) {
                    Log.e("MainActivity", "Error registering push token: ${e.message}")
                  }
                }
              }
            }
          } catch (e: Exception) {
            Log.e("MainActivity", "FirebaseMessaging not initialized: ${e.message}")
          }
        }

        ChatScreen(
          modifier = Modifier.fillMaxSize(),
          openTaskId = openTaskIdState,
          onClearOpenTaskId = { openTaskIdState = null },
          openNeedsYou = openNeedsYouState,
          onClearOpenNeedsYou = { openNeedsYouState = null },
          sharedTick = sharedTick
        )
      }
    }
  }

  // Live task events only while the app is on screen; push notifications cover it when it's closed
  override fun onStart() {
    super.onStart()
    TaskEvents.start(applicationContext)
  }

  override fun onStop() {
    TaskEvents.stop()
    super.onStop()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    val taskId = intent.getStringExtra("OPEN_TASK_ID")
    if (!taskId.isNullOrBlank()) {
      openTaskIdState = taskId
    }
    intent.getStringExtra(com.example.service.NeedsYou.EXTRA_OPEN)?.let { openNeedsYouState = it }
    if (intent.getBooleanExtra(SharedInbox.EXTRA_SHARED, false)) sharedTick++
  }
}

