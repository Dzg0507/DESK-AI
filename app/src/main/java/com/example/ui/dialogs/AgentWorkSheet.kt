package com.example.ui.dialogs

import com.example.ui.components.SheetHeader
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.theme.DeskShapes
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AgentWorkProject
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import kotlinx.coroutines.launch
import com.example.ui.theme.DeepCard
import com.example.ui.theme.DeepPanel
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.components.deskSheet

@Composable
fun AgentWorkSheet(
    onDismiss: () -> Unit,
    onFetchProjects: suspend () -> List<AgentWorkProject>,
    onDispatchJob: suspend (project: String, instruction: String) -> Result<String>,
    onAddProject: (suspend (name: String, repo: String, description: String) -> Result<AgentWorkProject>)? = null
) {
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<AgentWorkProject>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<AgentWorkProject?>(null) }
    var instruction by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusSuccess by remember { mutableStateOf(true) }

    // Add Project Dialog State
    var showAddProject by remember { mutableStateOf(false) }
    var newProjName by remember { mutableStateOf("") }
    var newProjRepo by remember { mutableStateOf("") }
    var newProjDesc by remember { mutableStateOf("") }
    var isAddingProj by remember { mutableStateOf(false) }
    var addProjError by remember { mutableStateOf<String?>(null) }

    fun refreshProjects() {
        scope.launch {
            isLoading = true
            try {
                projects = onFetchProjects()
                if (projects.isNotEmpty() && selectedProject == null) {
                    selectedProject = projects.first()
                }
            } catch (_: Exception) {}
            finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshProjects()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .deskSheet()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                SheetHeader(
                    title = "AgentWork Project Hub",
                    icon = Icons.Default.Build,
                    subtitle = "Autonomous code workflows on host repositories",
                    onClose = onDismiss
                ) {
                    IconButton(onClick = { refreshProjects() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Project Selector Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TARGET REPOSITORY / PROJECT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate400,
                        letterSpacing = 0.8.sp
                    )
                    if (onAddProject != null) {
                        Text(
                            text = if (showAddProject) "Cancel" else "+ Add Project",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            modifier = Modifier.clickable {
                                showAddProject = !showAddProject
                                addProjError = null
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (showAddProject) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DeskShapes.control)
                            .background(Slate800)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), DeskShapes.control)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Register GitHub Repository with AgentWork",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        OutlinedTextField(
                            value = newProjName,
                            onValueChange = { newProjName = it },
                            placeholder = { Text("Project name (e.g. recipe-app)", color = Slate500, fontSize = 11.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newProjRepo,
                            onValueChange = { newProjRepo = it },
                            placeholder = { Text("https://github.com/owner/repo.git", color = Slate500, fontSize = 11.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newProjDesc,
                            onValueChange = { newProjDesc = it },
                            placeholder = { Text("Description (e.g. Recipe companion app)", color = Slate500, fontSize = 11.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (addProjError != null) {
                            Text(
                                text = "⚠️ $addProjError",
                                fontSize = 11.sp,
                                color = RoseError
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DeskShapes.chip)
                                .background(if (isAddingProj) Slate600 else ElectricCyan)
                                .clickable(enabled = !isAddingProj && newProjName.isNotBlank() && newProjRepo.isNotBlank()) {
                                    scope.launch {
                                        isAddingProj = true
                                        addProjError = null
                                        val res = onAddProject?.invoke(newProjName.trim(), newProjRepo.trim(), newProjDesc.trim())
                                        isAddingProj = false
                                        if (res != null && res.isSuccess) {
                                            val added = res.getOrNull()
                                            if (added != null) {
                                                projects = projects + added
                                                selectedProject = added
                                            }
                                            showAddProject = false
                                            newProjName = ""
                                            newProjRepo = ""
                                            newProjDesc = ""
                                            statusMessage = "Added project '${added?.name}'! Tasks can now target it."
                                            statusSuccess = true
                                        } else {
                                            addProjError = res?.exceptionOrNull()?.message ?: "Failed to add project"
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAddingProj) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Validating with GitHub...", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text("Register Project Now", color = Slate900, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (isLoading) {
                    LoadingState("Scanning host repositories…")
                } else if (projects.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Build,
                        title = "No AgentWork projects yet",
                        message = "Add project folders in supervisor settings on the PC.",
                        accent = ElectricCyan
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(DeskShapes.control)
                            .background(DeepCard)
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(projects) { proj ->
                            val isSel = selectedProject?.name == proj.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(DeskShapes.chip)
                                    .background(if (isSel) ElectricCyan.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSel) ElectricCyan else Color.Transparent,
                                        DeskShapes.chip
                                    )
                                    .clickable { selectedProject = proj }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isSel) ElectricCyan else Slate500,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = proj.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else Slate300
                                    )
                                    if (proj.description.isNotBlank()) {
                                        Text(
                                            text = proj.description,
                                            fontSize = 10.sp,
                                            color = Slate400,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Instruction Input Field
                Text(
                    text = "ENGINEERING MISSION INSTRUCTION (Min 8 characters)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    placeholder = {
                        Text(
                            "e.g., Add a holidays page with responsive CSS and tests",
                            color = Slate500,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DeepCard,
                        unfocusedContainerColor = DeepPanel,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Slate200
                    ),
                    shape = DeskShapes.control
                )

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusMessage!!,
                        fontSize = 11.sp,
                        color = if (statusSuccess) EmeraldConnected else RoseError,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dispatch Button
                val canSubmit = (selectedProject != null || projects.isNotEmpty()) &&
                        instruction.trim().length >= 8 && !isSubmitting

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DeskShapes.control)
                        .background(if (canSubmit) ElectricCyan else Slate700)
                        .clickable(enabled = canSubmit) {
                            val projName = selectedProject?.name ?: projects.firstOrNull()?.name ?: return@clickable
                            scope.launch {
                                isSubmitting = true
                                statusMessage = "Dispatching AgentWork mission..."
                                val res = onDispatchJob(projName, instruction.trim())
                                if (res.isSuccess) {
                                    statusSuccess = true
                                    statusMessage = "🚀 Job queued in Tasks! ID: [${res.getOrNull()}]"
                                    instruction = ""
                                } else {
                                    statusSuccess = false
                                    statusMessage = "⚠️ ${res.exceptionOrNull()?.message}"
                                }
                                isSubmitting = false
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = if (canSubmit) Color.Black else Slate400, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isSubmitting) "STARTING AGENTWORK..." else "START AGENTWORK MISSION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canSubmit) Color.Black else Slate400
                        )
                    }
                }
            }
        }
    }
}
