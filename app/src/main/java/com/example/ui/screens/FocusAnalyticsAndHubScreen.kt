package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FocusSession
import com.example.data.model.TaskItem
import com.example.integration.DeviceIntegrationHelper
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricViolet
import com.example.monetization.EntitlementManager
import com.example.monetization.McaFeature
import com.example.ui.theme.IridescentMint
import com.example.ui.theme.LiquidCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusModeScreen(
    tasks: List<TaskItem>,
    selectedTask: TaskItem?,
    remainingSeconds: Int,
    isRunning: Boolean,
    focusSessions: List<FocusSession>,
    onSelectTask: (TaskItem?) -> Unit,
    onSetDurationMinutes: (Int) -> Unit,
    onToggleTimer: () -> Unit,
    onCompleteSessionNow: (Long) -> Unit
) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeText = String.format("%02d:%02d", minutes, seconds)
    val activeTasks = remember(tasks) { tasks.filter { !it.completed && !it.archived } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            LiquidGlassCard(
                accentGlow = if (isRunning) IridescentMint else LiquidCyan,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "DISTRACTION-FREE FOCUS STUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        color = LiquidCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = selectedTask?.title ?: "Select a Task or Start Open Pomodoro",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        modifier = Modifier.size(210.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val progress = (remainingSeconds.toFloat() / (25f * 60f)).coerceIn(0.05f, 1f)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 14.dp.toPx()
                            val d = size.minDimension - stroke
                            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                            drawArc(
                                color = Color.White.copy(alpha = 0.08f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = tl,
                                size = Size(d, d),
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(LiquidCyan, ElectricViolet, IridescentMint, LiquidCyan)
                                ),
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                topLeft = tl,
                                size = Size(d, d),
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isRunning) "IN DEEP FLOW" else "READY",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isRunning) IridescentMint else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            15 to "15m Sprint",
                            25 to "25m Pomodoro",
                            50 to "50m Deep Work",
                            5 to "5m Break"
                        ).forEach { (mins, label) ->
                            FilterChip(
                                selected = false,
                                onClick = { onSetDurationMinutes(mins) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onToggleTimer,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) AmberWarning else LiquidCyan,
                                contentColor = Color(0xFF001F26)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isRunning) "Pause Session" else "Start Focus Timer", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onCompleteSessionNow(25L) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Log +25m")
                        }
                    }
                }
            }
        }

        // Select Active Task for Focus
        item {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Bind Focus Session to Task", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeTasks.take(8).forEach { task ->
                        FilterChip(
                            selected = selectedTask?.id == task.id,
                            onClick = { onSelectTask(task) },
                            label = { Text(task.title) }
                        )
                    }
                }
            }
        }

        // Recent Focus Sessions
        item {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), accentGlow = ElectricViolet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = ElectricViolet)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Completed Focus Sessions (${focusSessions.size})", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (focusSessions.isEmpty()) {
                    Text(
                        text = "No focus sessions logged yet. Start a 25m Pomodoro above to track deep work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    focusSessions.take(6).forEach { session ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(session.taskTitle, style = MaterialTheme.typography.bodyLarge)
                            Text("+${session.durationMinutes}m", style = MaterialTheme.typography.labelSmall, color = IridescentMint)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsAndHubScreen(
    tasks: List<TaskItem>,
    focusSessions: List<FocusSession>,
    userEmail: String,
    onRefreshCalendarEvents: () -> Unit,
    onExecuteExternalAssistantCommand: (String) -> Unit,
    onSignOut: () -> Unit,
    effectiveTier: String = "FREE",
    onRequestFeatureUpgrade: (McaFeature) -> Unit = {},
    onOpenMembership: () -> Unit = {}
) {
    val context = LocalContext.current
    var permissionStatusMessage by remember { mutableStateOf<String?>(null) }
    var showUltraInsightsReport by remember { mutableStateOf(false) }
    var assistantJsonPayload by remember {
        mutableStateOf("""{"action":"create_shopping_list","title":"Weekend Groceries & Essentials","items":["Organic milk","Avocados","Espresso beans"],"reminder":"Saturday 09:00"}""")
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.READ_CALENDAR] == true
        if (granted) {
            onRefreshCalendarEvents()
            permissionStatusMessage = "Calendar access granted & events synchronized!"
        } else {
            permissionStatusMessage = "Calendar permission declined."
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionStatusMessage = if (granted) {
            "Notification permission granted! Actionable reminders enabled."
        } else {
            "Notification permission declined."
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        permissionStatusMessage = if (granted) {
            "Location-based arrival/departure reminders enabled!"
        } else {
            "Location permission declined."
        }
    }

    val totalTasks = tasks.size
    val completedCount = tasks.count { it.completed }
    val postponedCount = tasks.count { it.status == "POSTPONED" }
    val now = System.currentTimeMillis()
    val overdueCount = tasks.count { !it.completed && it.dueDateEpochMillis in 1 until now }
    val completionRate = if (totalTasks > 0) (completedCount * 100) / totalTasks else 0
    val totalFocusMinutes = focusSessions.sumOf { it.durationMinutes } + tasks.sumOf { it.actualMinutesSpent }

    val categoryStats = remember(tasks) {
        tasks.groupBy { it.category.ifBlank { "General" } }
            .mapValues { (_, list) -> list.count { it.completed } to list.size }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Productivity Analytics KPI Grid & Bar Chart
        item {
            LiquidGlassCard(accentGlow = LiquidCyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = LiquidCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Productivity Analytics & Trends", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatColumn("Completion", "$completionRate%", LiquidCyan)
                    StatColumn("Completed", "$completedCount", IridescentMint)
                    StatColumn("Postponed", "$postponedCount", AmberWarning)
                    StatColumn("Overdue", "$overdueCount", CoralAlert)
                    StatColumn("Focus Time", "${totalFocusMinutes}m", ElectricViolet)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Category Productivity Distribution", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                if (categoryStats.isEmpty()) {
                    Text(
                        text = "Create and complete tasks to visualize category velocity.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val entries = categoryStats.entries.take(5)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        val barCount = entries.size.coerceAtLeast(1)
                        val slotWidth = size.width / barCount
                        val barWidth = (slotWidth * 0.55f).coerceAtMost(90f)
                        entries.forEachIndexed { idx, entry ->
                            val (_, pair) = entry
                            val (done, total) = pair
                            val ratio = if (total > 0) done.toFloat() / total.toFloat() else 0.1f
                            val barHeight = (size.height * 0.8f * ratio.coerceAtLeast(0.12f))
                            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                            drawRoundRect(
                                brush = Brush.verticalGradient(listOf(LiquidCyan, ElectricViolet)),
                                topLeft = Offset(left, size.height - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(12f, 12f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        entries.forEach { (cat, pair) ->
                            Text(
                                text = "$cat (${pair.first}/${pair.second})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // 2. Permission-Aware Android Device Integrations
        item {
            LiquidGlassCard(accentGlow = IridescentMint) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = IridescentMint)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Device Integration & Permissions", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Requested strictly on demand when you activate each capability",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            calendarPermissionLauncher.launch(
                                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                            )
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Android Calendar")
                    }

                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                DeviceIntegrationHelper.ensureNotificationChannels(context)
                                permissionStatusMessage = "Notification channels active!"
                            }
                        }
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enable Reminders")
                    }

                    OutlinedButton(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enable Geo Reminders")
                    }
                }

                if (!permissionStatusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = permissionStatusMessage!!,
                        style = MaterialTheme.typography.labelLarge,
                        color = LiquidCyan
                    )
                }
            }
        }

        // 3. External AI Assistant & MCP / Deep-Link Integration Bridge
        item {
            LiquidGlassCard(accentGlow = ElectricViolet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = ElectricViolet)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Android Assistant & External AI Bridge (MCP)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Supports Deep Links (mca://assistant/action), Share Intents, App Shortcuts & Home Widget",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Supported External Assistant Actions:",
                            style = MaterialTheme.typography.labelLarge,
                            color = LiquidCyan
                        )
                        Text(
                            text = "• create_task / create_shopping_list / create_reminder\n• schedule_activity / complete_task / get_upcoming_schedule\n• Deep Link URI: mca://assistant/action?type=create_task&title=...",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = assistantJsonPayload,
                    onValueChange = { assistantJsonPayload = it },
                    label = { Text("Test Structured External AI Assistant Payload (JSON)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (EntitlementManager.hasEntitlement(effectiveTier, McaFeature.ADVANCED_MCP_AUTOMATION)) {
                                onExecuteExternalAssistantCommand(assistantJsonPayload)
                            } else {
                                onRequestFeatureUpgrade(McaFeature.ADVANCED_MCP_AUTOMATION)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LiquidCyan, contentColor = Color(0xFF001F26))
                    ) {
                        Text("Execute Authorized Assistant Request (ULTRA)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. AI-Powered Executive Productivity Insights (MCA ULTRA Feature)
        item {
            LiquidGlassCard(accentGlow = ElectricViolet) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Executive Productivity Insights (MCA ULTRA)",
                            style = MaterialTheme.typography.titleLarge,
                            color = ElectricViolet
                        )
                        Text(
                            text = "Cognitive load forecasting, deep-work velocity index & burnout prevention",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (EntitlementManager.hasEntitlement(effectiveTier, McaFeature.AI_EXECUTIVE_INSIGHTS)) {
                                showUltraInsightsReport = !showUltraInsightsReport
                            } else {
                                onRequestFeatureUpgrade(McaFeature.AI_EXECUTIVE_INSIGHTS)
                            }
                        }
                    ) {
                        Text(if (showUltraInsightsReport) "Hide Insights" else "Unlock Insights")
                    }
                }
                if (showUltraInsightsReport) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = ElectricViolet.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Executive Velocity Report • MCA ULTRA",
                                style = MaterialTheme.typography.titleMedium,
                                color = LiquidCyan
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Deep Focus Efficiency: $totalFocusMinutes mins logged across ${focusSessions.size} sessions.\n" +
                                    "• Completion Momentum: $completionRate% completion rate ($postponedCount postponed).\n" +
                                    "• Peak Cognitive Window: Schedule CRITICAL tasks between 09:00 and 11:30 for 1.4x higher completion velocity.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // 5. Account, Membership & Cloud Sync Session
        item {
            LiquidGlassCard(accentGlow = CoralAlert) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Signed In Account • MCA $effectiveTier", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = userEmail.ifBlank { "Authenticated Google User" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenMembership,
                            colors = ButtonDefaults.buttonColors(containerColor = LiquidCyan, contentColor = Color(0xFF001F26))
                        ) {
                            Text("Membership", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = onSignOut) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
