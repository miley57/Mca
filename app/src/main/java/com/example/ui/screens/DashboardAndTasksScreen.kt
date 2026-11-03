package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DeviceCalendarEvent
import com.example.data.model.TaskItem
import com.example.integration.DeviceIntegrationHelper
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.PriorityBadge
import com.example.ui.components.ProductivityRingIndicator
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.IridescentMint
import com.example.monetization.EntitlementManager
import com.example.monetization.McaFeature
import com.example.monetization.McaNativeProductivityAdCard
import com.example.ui.theme.LiquidCyan
import com.example.viewmodel.AiOrganizerState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DashboardTab(
    tasks: List<TaskItem>,
    focusMinutesTotal: Long,
    calendarEvents: List<DeviceCalendarEvent>,
    aiState: AiOrganizerState,
    onQuickAddTask: (String, String) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onStartFocusOnTask: (TaskItem) -> Unit,
    onRequestBriefing: (String) -> Unit,
    onOpenAiOrganiser: () -> Unit,
    onOpenVoiceCapture: () -> Unit,
    effectiveTier: String = "FREE",
    adsEnabledInConfig: Boolean = true,
    onOpenMembership: () -> Unit = {}
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..21 -> "Good Evening"
        else -> "Late Night Focus"
    }
    val formattedDate = remember {
        SimpleDateFormat("EEEE, MMM d • HH:mm", Locale.getDefault()).format(Date())
    }

    var quickTitle by remember { mutableStateOf("") }
    var quickPriority by remember { mutableStateOf("HIGH") }

    val activeTasks = remember(tasks) { tasks.filter { !it.archived } }
    val completedTasks = remember(activeTasks) { activeTasks.filter { it.completed } }
    val now = System.currentTimeMillis()
    val overdueTasks = remember(activeTasks, now) {
        activeTasks.filter { !it.completed && it.dueDateEpochMillis in 1 until now }
    }
    val priorityTasks = remember(activeTasks) {
        activeTasks.filter { !it.completed && (it.priority == "CRITICAL" || it.priority == "HIGH") }.take(4)
    }
    val recommendedNextTask = remember(activeTasks) {
        activeTasks.filter { !it.completed }
            .sortedWith(
                compareByDescending<TaskItem> { it.priorityRank() }
                    .thenBy { if (it.dueDateEpochMillis > 0L) it.dueDateEpochMillis else Long.MAX_VALUE }
                    .thenBy { it.estimatedMinutes }
            ).firstOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Liquid Glass Banner with Greeting & Live Date
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_liquid_glass_1793661360818),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0x55070A12), Color(0xE6070A12))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = formattedDate.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LiquidCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = greeting,
                                    style = MaterialTheme.typography.displayMedium,
                                    color = Color.White
                                )
                            }
                            IconButton(
                                onClick = onOpenVoiceCapture,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .testTag("dashboard_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Command",
                                    tint = LiquidCyan
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { onRequestBriefing("MORNING_BRIEFING") },
                                label = { Text("Morning Briefing", color = Color.White) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.WbSunny,
                                        contentDescription = null,
                                        tint = AmberWarning,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            AssistChip(
                                onClick = { onRequestBriefing("EOD_REVIEW") },
                                label = { Text("End-of-Day Review", color = Color.White) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = ElectricViolet,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // 2. Visual Productivity Ring Card
        item {
            LiquidGlassCard(accentGlow = LiquidCyan) {
                ProductivityRingIndicator(
                    completedCount = completedTasks.size,
                    totalCount = activeTasks.size,
                    focusMinutes = focusMinutesTotal
                )
            }
        }

        // 3. AI Daily Overview (when active or ready)
        item {
            AnimatedVisibility(
                visible = aiState is AiOrganizerState.Analyzing ||
                    aiState is AiOrganizerState.StreamingOverview ||
                    aiState is AiOrganizerState.OverviewReady ||
                    aiState is AiOrganizerState.Error
            ) {
                LiquidGlassCard(accentGlow = ElectricViolet) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = LiquidCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MCA AI Daily Overview",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextButton(onClick = onOpenAiOrganiser) {
                            Text("Open AI Hub")
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    when (aiState) {
                        is AiOrganizerState.Analyzing -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = LiquidCyan)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Synthesizing personalized schedule insights...", style = MaterialTheme.typography.bodyMedium)
                        }
                        is AiOrganizerState.StreamingOverview -> {
                            Text(aiState.partialText, style = MaterialTheme.typography.bodyMedium)
                        }
                        is AiOrganizerState.OverviewReady -> {
                            Text(aiState.text, style = MaterialTheme.typography.bodyMedium)
                        }
                        is AiOrganizerState.Error -> {
                            Text(aiState.message, style = MaterialTheme.typography.bodyMedium, color = CoralAlert)
                        }
                        else -> {}
                    }
                }
            }
        }

        // 4. Smart Task Prioritization — Next Recommended Action
        if (recommendedNextTask != null) {
            item {
                LiquidGlassCard(accentGlow = ElectricViolet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = ElectricViolet,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SMART NEXT PRIORITY",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricViolet
                            )
                        }
                        PriorityBadge(recommendedNextTask.priority)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = recommendedNextTask.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (recommendedNextTask.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = recommendedNextTask.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onStartFocusOnTask(recommendedNextTask) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LiquidCyan,
                                contentColor = Color(0xFF001F26)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Focus (${recommendedNextTask.estimatedMinutes}m)")
                        }
                        OutlinedButton(
                            onClick = { onToggleTask(recommendedNextTask) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Complete")
                        }
                    }
                }
            }
        }

        // 5. Quick Task Creation Bar
        item {
            LiquidGlassCard(accentGlow = IridescentMint) {
                Text(
                    text = "Quick Task Capture",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quickTitle,
                        onValueChange = { quickTitle = it },
                        placeholder = { Text("Add a new task or deadline...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_task_input")
                    )
                    Button(
                        onClick = {
                            if (quickTitle.isNotBlank()) {
                                onQuickAddTask(quickTitle.trim(), quickPriority)
                                quickTitle = ""
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("quick_task_submit_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Task")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LOW", "NORMAL", "HIGH", "CRITICAL").forEach { level ->
                        FilterChip(
                            selected = quickPriority == level,
                            onClick = { quickPriority = level },
                            label = { Text(level) }
                        )
                    }
                }
            }
        }

        // 6. Overdue Tasks Alert (if any)
        if (overdueTasks.isNotEmpty()) {
            item {
                LiquidGlassCard(accentGlow = CoralAlert) {
                    Text(
                        text = "Overdue Tasks (${overdueTasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = CoralAlert
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    overdueTasks.take(3).forEach { task ->
                        CompactTaskRow(
                            task = task,
                            onToggle = { onToggleTask(task) },
                            onClick = { onEditTask(task) }
                        )
                    }
                }
            }
        }

        // 7. Priority & Today's Tasks
        item {
            LiquidGlassCard(accentGlow = LiquidCyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Priority & Today's Schedule (${activeTasks.count { !it.completed }})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val displayList = if (priorityTasks.isNotEmpty()) priorityTasks else activeTasks.filter { !it.completed }.take(5)
                if (displayList.isEmpty()) {
                    Text(
                        text = "All caught up! Use Quick Capture above or ask MCA AI Organiser to plan your week.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    displayList.forEach { task ->
                        CompactTaskRow(
                            task = task,
                            onToggle = { onToggleTask(task) },
                            onClick = { onEditTask(task) }
                        )
                    }
                }
            }
        }

        // 8. Upcoming Device Calendar Events
        item {
            LiquidGlassCard(accentGlow = AmberWarning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = AmberWarning)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Synced Calendar Events (${calendarEvents.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (calendarEvents.isEmpty()) {
                    Text(
                        text = "No upcoming calendar events loaded yet. Visit Hub > Permissions to sync your Android Calendar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val fmt = SimpleDateFormat("EEE, MMM d • HH:mm", Locale.getDefault())
                    calendarEvents.take(4).forEach { ev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = ev.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = fmt.format(Date(ev.startMillis)),
                                style = MaterialTheme.typography.labelSmall,
                                color = LiquidCyan
                            )
                        }
                    }
                }
            }
        }

        // 9. Non-Intrusive Native Ad Slot for MCA FREE (Never shown during Quick Task Creation!)
        if (quickTitle.isBlank()) {
            item {
                McaNativeProductivityAdCard(
                    effectiveTier = effectiveTier,
                    adsEnabledInConfig = adsEnabledInConfig,
                    currentTab = "DASHBOARD",
                    onUpgradeClick = onOpenMembership
                )
            }
        }
    }
}

@Composable
private fun CompactTaskRow(
    task: TaskItem,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (task.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Toggle Complete",
                    tint = if (task.completed) IridescentMint else LiquidCyan
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${task.category} • ${task.estimatedMinutes}m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (task.scheduledTimeSlot.isNotBlank()) {
                        Text(
                            text = task.scheduledTimeSlot,
                            style = MaterialTheme.typography.labelSmall,
                            color = LiquidCyan
                        )
                    }
                }
            }
        }
        PriorityBadge(task.priority)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksWorkspaceTab(
    tasks: List<TaskItem>,
    onCreateTask: (TaskItem) -> Unit,
    onUpdateTask: (TaskItem) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onDuplicateTask: (TaskItem) -> Unit,
    onArchiveTask: (TaskItem) -> Unit,
    onPostponeTask: (TaskItem) -> Unit,
    onDeleteTask: (String) -> Unit,
    onStartFocus: (TaskItem) -> Unit,
    effectiveTier: String = "FREE",
    onRequestFeatureUpgrade: (McaFeature) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedViewMode by remember { mutableStateOf("LIST") } // LIST, KANBAN, CALENDAR, TIMELINE, PRIORITY
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var showArchived by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var showTaskDialog by remember { mutableStateOf(false) }

    val categories = remember(tasks) {
        listOf("ALL") + tasks.map { it.category }.distinct().filter { it.isNotBlank() }
    }

    val filteredTasks = remember(tasks, searchQuery, selectedCategory, showArchived) {
        tasks.filter { task ->
            val archiveMatch = if (showArchived) task.archived else !task.archived
            val categoryMatch = selectedCategory == "ALL" || task.category.equals(selectedCategory, ignoreCase = true)
            val queryMatch = searchQuery.isBlank() ||
                task.title.contains(searchQuery, ignoreCase = true) ||
                task.description.contains(searchQuery, ignoreCase = true) ||
                task.project.contains(searchQuery, ignoreCase = true) ||
                task.tagsText.contains(searchQuery, ignoreCase = true)
            archiveMatch && categoryMatch && queryMatch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top View Switcher & Create Task Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Task Command Center",
                style = MaterialTheme.typography.headlineMedium
            )
            Button(
                onClick = {
                    editingTask = null
                    showTaskDialog = true
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiquidCyan,
                    contentColor = Color(0xFF001F26)
                ),
                modifier = Modifier.testTag("open_new_task_dialog_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Task", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5 View Modes: List, Kanban, Calendar, Timeline, Priority (Pro views check entitlement)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val modes = listOf(
                "LIST" to "List View",
                "KANBAN" to "Kanban Board (PRO)",
                "CALENDAR" to "Calendar View",
                "TIMELINE" to "Timeline View (PRO)",
                "PRIORITY" to "Priority Matrix (PRO)"
            )
            items(modes) { (key, label) ->
                FilterChip(
                    selected = selectedViewMode == key,
                    onClick = {
                        val requiresPro = key == "KANBAN" || key == "TIMELINE" || key == "PRIORITY"
                        if (requiresPro && !EntitlementManager.hasEntitlement(effectiveTier, McaFeature.ADVANCED_TASK_VIEWS)) {
                            onRequestFeatureUpgrade(McaFeature.ADVANCED_TASK_VIEWS)
                        } else {
                            selectedViewMode = key
                        }
                    },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search & Category Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search tasks, tags, projects...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = showArchived,
                onClick = { showArchived = !showArchived },
                label = { Text(if (showArchived) "Archived" else "Active") },
                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) }
                )
            }
        }

        // Main View Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedViewMode) {
                "KANBAN" -> KanbanBoardView(
                    tasks = filteredTasks,
                    onMoveStatus = { task, newStatus ->
                        onUpdateTask(
                            task.copy(
                                status = newStatus,
                                completed = newStatus == "COMPLETED"
                            )
                        )
                    },
                    onEditTask = {
                        editingTask = it
                        showTaskDialog = true
                    }
                )
                "CALENDAR" -> CalendarTasksView(
                    tasks = filteredTasks,
                    onEditTask = {
                        editingTask = it
                        showTaskDialog = true
                    },
                    onSyncCalendar = { task ->
                        val ok = DeviceIntegrationHelper.exportTaskToDeviceCalendar(context, task)
                        if (ok) {
                            onUpdateTask(task.copy(calendarSynced = true))
                        }
                    }
                )
                "TIMELINE" -> TimelineTasksView(
                    tasks = filteredTasks,
                    onEditTask = {
                        editingTask = it
                        showTaskDialog = true
                    }
                )
                "PRIORITY" -> PriorityMatrixView(
                    tasks = filteredTasks,
                    onToggleTask = onToggleTask,
                    onEditTask = {
                        editingTask = it
                        showTaskDialog = true
                    }
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredTasks, key = { it.id.ifBlank { it.title } }) { task ->
                        FullTaskCard(
                            task = task,
                            onToggle = { onToggleTask(task) },
                            onEdit = {
                                editingTask = task
                                showTaskDialog = true
                            },
                            onDuplicate = { onDuplicateTask(task) },
                            onArchive = { onArchiveTask(task) },
                            onPostpone = { onPostponeTask(task) },
                            onDelete = { onDeleteTask(task.id) },
                            onFocus = { onStartFocus(task) },
                            onTriggerReminder = {
                                DeviceIntegrationHelper.sendActionableTaskNotification(context, task, "Scheduled Reminder")
                            }
                        )
                    }
                }
            }
        }
    }

    if (showTaskDialog) {
        TaskEditorDialog(
            initialTask = editingTask,
            allTasks = tasks,
            onDismiss = { showTaskDialog = false },
            onSave = { savedTask ->
                if (editingTask == null) {
                    onCreateTask(savedTask)
                } else {
                    onUpdateTask(savedTask)
                }
                showTaskDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FullTaskCard(
    task: TaskItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onPostpone: () -> Unit,
    onDelete: () -> Unit,
    onFocus: () -> Unit,
    onTriggerReminder: () -> Unit
) {
    val dueFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    LiquidGlassCard(
        accentGlow = when (task.priority) {
            "CRITICAL" -> CoralAlert
            "HIGH" -> AmberWarning
            else -> LiquidCyan
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (task.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Complete Task",
                        tint = if (task.completed) IridescentMint else LiquidCyan
                    )
                }
                Column {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleLarge,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Text(
                        text = "${task.category} • Project: ${task.project} • ${task.status}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            PriorityBadge(task.priority)
        }

        if (task.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Metadata badges: time slot, duration, recurrence, location, dependencies, attachments
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (task.dueDateEpochMillis > 0L) {
                MetadataPill(Icons.Default.CalendarMonth, dueFmt.format(Date(task.dueDateEpochMillis)))
            }
            if (task.scheduledTimeSlot.isNotBlank()) {
                MetadataPill(Icons.Default.Schedule, "${task.scheduledTimeSlot} (${task.estimatedMinutes}m)")
            }
            if (task.recurrence != "NONE") {
                MetadataPill(Icons.Default.Repeat, task.recurrence)
            }
            if (task.locationLabel.isNotBlank()) {
                MetadataPill(Icons.Default.LocationOn, "${task.locationLabel} (${task.locationTrigger})")
            }
            if (task.dependsOnTaskTitle.isNotBlank()) {
                MetadataPill(Icons.Default.Link, "Depends on: ${task.dependsOnTaskTitle}")
            }
            if (task.attachmentName.isNotBlank()) {
                MetadataPill(Icons.Default.AttachFile, task.attachmentName)
            }
        }

        // Subtasks checklist preview
        val subtasks = remember(task.subtasksText) { task.parsedSubtasks() }
        if (subtasks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                subtasks.take(4).forEach { (done, itemText) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (done) IridescentMint else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = itemText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(onClick = onFocus) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Focus")
            }
            TextButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit")
            }
            TextButton(onClick = onPostpone) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reschedule")
            }
            TextButton(onClick = onTriggerReminder) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Remind")
            }
            TextButton(onClick = onDuplicate) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Duplicate")
            }
            TextButton(onClick = onArchive) {
                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (task.archived) "Unarchive" else "Archive")
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = CoralAlert, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun MetadataPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = LiquidCyan, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun KanbanBoardView(
    tasks: List<TaskItem>,
    onMoveStatus: (TaskItem, String) -> Unit,
    onEditTask: (TaskItem) -> Unit
) {
    val columns = listOf(
        "TODO" to "To Do",
        "IN_PROGRESS" to "In Progress",
        "POSTPONED" to "Postponed",
        "COMPLETED" to "Completed"
    )
    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        columns.forEach { (statusKey, statusTitle) ->
            val colTasks = tasks.filter { it.status == statusKey }
            LiquidGlassCard(
                modifier = Modifier
                    .width(275.dp)
                    .fillMaxSize(),
                accentGlow = if (statusKey == "COMPLETED") IridescentMint else LiquidCyan
            ) {
                Text(
                    text = "$statusTitle (${colTasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = LiquidCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colTasks, key = { it.id.ifBlank { it.title } }) { task ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditTask(task) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    PriorityBadge(task.priority)
                                }
                                if (task.scheduledTimeSlot.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(task.scheduledTimeSlot, style = MaterialTheme.typography.labelSmall, color = LiquidCyan)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    columns.filter { it.first != statusKey }.forEach { (targetKey, targetLabel) ->
                                        AssistChip(
                                            onClick = { onMoveStatus(task, targetKey) },
                                            label = { Text("→ ${targetLabel.take(6)}", style = MaterialTheme.typography.labelSmall) }
                                        )
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

@Composable
private fun CalendarTasksView(
    tasks: List<TaskItem>,
    onEditTask: (TaskItem) -> Unit,
    onSyncCalendar: (TaskItem) -> Unit
) {
    var selectedDayOffset by remember { mutableStateOf(0) }
    val dayFmt = remember { SimpleDateFormat("EEE\nMMM d", Locale.getDefault()) }
    val baseNow = remember { System.currentTimeMillis() }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(7) { offset ->
                val dateMillis = baseNow + offset * 86_400_000L
                FilterChip(
                    selected = selectedDayOffset == offset,
                    onClick = { selectedDayOffset = offset },
                    label = {
                        Text(
                            text = if (offset == 0) "Today" else dayFmt.format(Date(dateMillis)),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tasks, key = { it.id.ifBlank { it.title } }) { task ->
                LiquidGlassCard(onClick = { onEditTask(task) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Slot: ${task.scheduledTimeSlot.ifBlank { "Flexible" }} • ${task.estimatedMinutes}m",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LiquidCyan
                            )
                        }
                        OutlinedButton(onClick = { onSyncCalendar(task) }) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (task.calendarSynced) "Synced" else "Export to Calendar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineTasksView(
    tasks: List<TaskItem>,
    onEditTask: (TaskItem) -> Unit
) {
    val sorted = remember(tasks) {
        tasks.sortedBy { if (it.dueDateEpochMillis > 0L) it.dueDateEpochMillis else Long.MAX_VALUE }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(sorted, key = { it.id.ifBlank { it.title } }) { task ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.width(92.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = task.scheduledTimeSlot.ifBlank { "Anytime" },
                        style = MaterialTheme.typography.labelSmall,
                        color = LiquidCyan
                    )
                    Text(
                        text = "${task.estimatedMinutes}m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                LiquidGlassCard(
                    modifier = Modifier.weight(1f),
                    onClick = { onEditTask(task) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        PriorityBadge(task.priority)
                    }
                    Text(task.project, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PriorityMatrixView(
    tasks: List<TaskItem>,
    onToggleTask: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit
) {
    val tiers = listOf("CRITICAL", "HIGH", "NORMAL", "LOW")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(tiers) { tier ->
            val tierTasks = tasks.filter { it.priority == tier }
            LiquidGlassCard(
                accentGlow = when (tier) {
                    "CRITICAL" -> CoralAlert
                    "HIGH" -> AmberWarning
                    "NORMAL" -> LiquidCyan
                    else -> IridescentMint
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriorityBadge(tier)
                    Text("${tierTasks.size} tasks", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                tierTasks.forEach { task ->
                    CompactTaskRow(
                        task = task,
                        onToggle = { onToggleTask(task) },
                        onClick = { onEditTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(
    initialTask: TaskItem?,
    allTasks: List<TaskItem>,
    onDismiss: () -> Unit,
    onSave: (TaskItem) -> Unit
) {
    var title by remember { mutableStateOf(initialTask?.title.orEmpty()) }
    var description by remember { mutableStateOf(initialTask?.description.orEmpty()) }
    var notes by remember { mutableStateOf(initialTask?.notes.orEmpty()) }
    var category by remember { mutableStateOf(initialTask?.category ?: "Work") }
    var project by remember { mutableStateOf(initialTask?.project ?: "General") }
    var priority by remember { mutableStateOf(initialTask?.priority ?: "NORMAL") }
    var status by remember { mutableStateOf(initialTask?.status ?: "TODO") }
    var scheduledTimeSlot by remember { mutableStateOf(initialTask?.scheduledTimeSlot ?: "09:00 - 10:00") }
    var estimatedMinutes by remember { mutableStateOf((initialTask?.estimatedMinutes ?: 45L).toString()) }
    var recurrence by remember { mutableStateOf(initialTask?.recurrence ?: "NONE") }
    var reminderOffsetsText by remember { mutableStateOf(initialTask?.reminderOffsetsText ?: "30m before, 10m before") }
    var locationLabel by remember { mutableStateOf(initialTask?.locationLabel.orEmpty()) }
    var locationTrigger by remember { mutableStateOf(initialTask?.locationTrigger ?: "NONE") }
    var subtasksText by remember { mutableStateOf(initialTask?.subtasksText.orEmpty()) }
    var tagsText by remember { mutableStateOf(initialTask?.tagsText.orEmpty()) }
    var attachmentUri by remember { mutableStateOf(initialTask?.attachmentUri.orEmpty()) }
    var attachmentName by remember { mutableStateOf(initialTask?.attachmentName.orEmpty()) }
    var dependsOnTaskTitle by remember { mutableStateOf(initialTask?.dependsOnTaskTitle.orEmpty()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            attachmentUri = uri.toString()
            attachmentName = "Attached Photo (${uri.lastPathSegment ?: "media"})"
        }
    }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            attachmentUri = "camera://captured_${System.currentTimeMillis()}"
            attachmentName = "Captured Receipt/Note (${bitmap.width}x${bitmap.height})"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialTask == null) "Create Intelligent Task" else "Edit Task")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = project,
                        onValueChange = { project = it },
                        label = { Text("Project") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("LOW", "NORMAL", "HIGH", "CRITICAL").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = scheduledTimeSlot,
                        onValueChange = { scheduledTimeSlot = it },
                        label = { Text("Time Range (e.g. 14:00 - 15:30)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = estimatedMinutes,
                        onValueChange = { estimatedMinutes = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Est. Mins") },
                        singleLine = true,
                        modifier = Modifier.width(105.dp)
                    )
                }

                Text("Recurrence Schedule", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("NONE", "DAILY", "WEEKLY", "MONTHLY", "MONDAYS")) { rec ->
                        FilterChip(
                            selected = recurrence == rec,
                            onClick = { recurrence = rec },
                            label = { Text(rec) }
                        )
                    }
                }

                OutlinedTextField(
                    value = reminderOffsetsText,
                    onValueChange = { reminderOffsetsText = it },
                    label = { Text("Reminders (e.g., 30m before, Every Monday 8 AM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = locationLabel,
                        onValueChange = { locationLabel = it },
                        label = { Text("Location Reminder (e.g. Work, Library)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NONE", "ARRIVE", "LEAVE").forEach { trig ->
                        FilterChip(
                            selected = locationTrigger == trig,
                            onClick = { locationTrigger = trig },
                            label = { Text(if (trig == "NONE") "No Geo Trigger" else "On $trig") }
                        )
                    }
                }

                OutlinedTextField(
                    value = subtasksText,
                    onValueChange = { subtasksText = it },
                    label = { Text("Subtasks (one per line, e.g. [ ] Draft outline)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Additional Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dependsOnTaskTitle,
                    onValueChange = { dependsOnTaskTitle = it },
                    label = { Text("Depends On Task (Prerequisite title)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Attachments: Photo Picker & Camera Capture
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Attach Image")
                    }
                    OutlinedButton(
                        onClick = { cameraPreviewLauncher.launch(null) }
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan / Camera")
                    }
                }
                if (attachmentName.isNotBlank()) {
                    Text(
                        text = "Attached: $attachmentName",
                        style = MaterialTheme.typography.labelSmall,
                        color = LiquidCyan
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = estimatedMinutes.toLongOrNull()?.coerceIn(1L, 1440L) ?: 30L
                    val base = initialTask ?: TaskItem()
                    onSave(
                        base.copy(
                            title = title.ifBlank { "Untitled Task" },
                            description = description,
                            notes = notes,
                            category = category.ifBlank { "General" },
                            project = project.ifBlank { "General" },
                            priority = priority,
                            status = status,
                            scheduledTimeSlot = scheduledTimeSlot,
                            estimatedMinutes = mins,
                            recurrence = recurrence,
                            reminderOffsetsText = reminderOffsetsText,
                            locationLabel = locationLabel,
                            locationTrigger = locationTrigger,
                            subtasksText = subtasksText,
                            tagsText = tagsText,
                            attachmentUri = attachmentUri,
                            attachmentName = attachmentName,
                            dependsOnTaskTitle = dependsOnTaskTitle
                        )
                    )
                }
            ) {
                Text("Save Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
