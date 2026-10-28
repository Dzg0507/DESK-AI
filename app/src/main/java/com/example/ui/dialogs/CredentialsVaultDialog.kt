package com.example.ui.dialogs

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.VpnKeyOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.StoredCredential
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.DeviceUnlock
import com.example.ui.components.SheetHeader
import com.example.ui.components.TagChip
import com.example.ui.components.circuitTexture
import com.example.ui.components.deskCard
import com.example.ui.components.deskSheet
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CredentialsVaultDialog(
    onDismiss: () -> Unit,
    onFetchCredentials: suspend () -> Result<List<StoredCredential>>,
    onSaveCredential: suspend (domain: String, username: String, password: String) -> Result<Boolean>,
    onDeleteCredential: suspend (domain: String, username: String) -> Result<Boolean>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Unlocked by Android's own prompt (DeviceUnlock); the old pattern's hash is deleted (2026-10-27: its Reset
    // button worked from the locked screen, so the lock protected nothing)
    LaunchedEffect(Unit) {
        context.getSharedPreferences("desk_ai_credentials", Context.MODE_PRIVATE)
            .edit().remove("vault_pattern_hash").apply()
    }
    var isUnlocked by remember { mutableStateOf(false) }
    var lockPrompt by remember { mutableStateOf("Confirm it's you to open the Vault") }
    var lockError by remember { mutableStateOf(false) }

    fun askToUnlock() {
        DeviceUnlock.prompt(context, "Unlock the Vault", "Passwords the PC's computer tasks sign in with") { ok, reason ->
            if (ok) {
                isUnlocked = true
            } else {
                lockError = reason != null
                lockPrompt = reason ?: "Locked. Tap Unlock to try again."
            }
        }
    }

    LaunchedEffect(Unit) { askToUnlock() }

    // Vault contents state
    var credentials by remember { mutableStateOf<List<StoredCredential>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    // Add account form
    var showAddForm by remember { mutableStateOf(false) }
    var newDomain by remember { mutableStateOf("") }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    fun refreshList() {
        scope.launch {
            isLoading = true
            onFetchCredentials()
                .onSuccess {
                    credentials = it
                    statusMessage = null
                }
                .onFailure {
                    statusMessage = "⚠️ ${it.message}" to false
                }
            isLoading = false
        }
    }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            refreshList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .deskSheet()
                .padding(16.dp)
        ) {
            if (!isUnlocked) {
                // ==================== LOCKED: ANDROID'S OWN UNLOCK ====================
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    SheetHeader(
                        title = "Vault Security",
                        icon = Icons.Default.Lock,
                        subtitle = "Protected by your phone's screen lock",
                        onClose = onDismiss
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (lockError) RoseError else ElectricCyan,
                        modifier = Modifier.size(56.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = lockPrompt,
                        color = if (lockError) RoseError else Slate300,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    TextButton(onClick = { askToUnlock() }) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = ElectricCyan,
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock", color = ElectricCyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // ==================== UNLOCKED CREDENTIALS VAULT ====================
                Column(modifier = Modifier.fillMaxWidth()) {
                    SheetHeader(
                        title = "Credentials Vault",
                        icon = Icons.Default.VpnKey,
                        subtitle = "Host Windows Credential Manager · DPAPI",
                        onClose = onDismiss,
                        actions = {
                            IconButton(onClick = { refreshList() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ElectricCyan)
                            }
                            IconButton(
                                onClick = {
                                    isUnlocked = false
                                    lockError = false
                                    lockPrompt = "Locked. Tap Unlock to open it again."
                                }
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = Slate400)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Security & Explanation Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DeskShapes.control)
                            .background(Slate800.copy(alpha = 0.5f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.25f), DeskShapes.control)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Accounts are stored in the host PC's vault. Computer tasks autofill sign-in forms without sending passwords to the AI or chat logs.",
                                fontSize = 11.sp,
                                color = Slate300,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    statusMessage?.let { (msg, success) ->
                        Text(
                            text = msg,
                            color = if (success) EmeraldConnected else RoseError,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    // Add Credential Accordion / Form
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .deskCard()
                            .clickable { showAddForm = !showAddForm }
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (showAddForm) Icons.Default.Close else Icons.Default.Add,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (showAddForm) "Cancel Adding Account" else "Add New Account",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = ElectricCyan
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showAddForm) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    OutlinedTextField(
                                        value = newDomain,
                                        onValueChange = { newDomain = it.trim().lowercase() },
                                        label = { Text("Domain (e.g. google.com, github.com)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ElectricCyan,
                                            unfocusedBorderColor = Slate700
                                        ),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = newUsername,
                                        onValueChange = { newUsername = it.trim() },
                                        label = { Text("Email / Username") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ElectricCyan,
                                            unfocusedBorderColor = Slate700
                                        ),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = newPassword,
                                        onValueChange = { newPassword = it },
                                        label = { Text("Password") },
                                        singleLine = true,
                                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                                Icon(
                                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = "Toggle password visibility",
                                                    tint = Slate400
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ElectricCyan,
                                            unfocusedBorderColor = Slate700
                                        ),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(DeskShapes.control)
                                            .background(
                                                if (isSaving || newDomain.isBlank() || newUsername.isBlank() || newPassword.isBlank()) Slate800
                                                else ElectricCyan
                                            )
                                            .clickable(enabled = !isSaving && newDomain.isNotBlank() && newUsername.isNotBlank() && newPassword.isNotBlank()) {
                                                scope.launch {
                                                    isSaving = true
                                                    onSaveCredential(newDomain, newUsername, newPassword)
                                                        .onSuccess {
                                                            Toast.makeText(context, "Saved to host vault!", Toast.LENGTH_SHORT).show()
                                                            newDomain = ""
                                                            newUsername = ""
                                                            newPassword = ""
                                                            showAddForm = false
                                                            refreshList()
                                                        }
                                                        .onFailure {
                                                            statusMessage = "Save failed: ${it.message}" to false
                                                        }
                                                    isSaving = false
                                                }
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSaving) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ElectricCyan, strokeWidth = 2.dp)
                                        } else {
                                            Text(
                                                text = "Save to Host Vault",
                                                fontWeight = FontWeight.Bold,
                                                color = if (newDomain.isNotBlank() && newUsername.isNotBlank() && newPassword.isNotBlank()) Color.Black else Slate500,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Saved Accounts List
                    if (isLoading) {
                        LoadingState(label = "Reading host vault...")
                    } else if (credentials.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.VpnKeyOff,
                            title = "No credentials in host vault",
                            message = "Entering your password during Needs You or Takeover will automatically save it here.",
                            accent = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(credentials) { cred ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .deskCard()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(ElectricCyan.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Key, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = cred.domain,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = cred.username,
                                                    fontSize = 12.sp,
                                                    color = Slate400,
                                                    fontFamily = com.example.ui.theme.JetBrainsMono
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    onDeleteCredential(cred.domain, cred.username)
                                                        .onSuccess {
                                                            Toast.makeText(context, "Deleted ${cred.domain}", Toast.LENGTH_SHORT).show()
                                                            refreshList()
                                                        }
                                                        .onFailure {
                                                            statusMessage = "Delete failed: ${it.message}" to false
                                                        }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Slate500)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
