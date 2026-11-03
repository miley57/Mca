package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.configureAppCheck
import com.example.auth.signOut
import com.example.data.model.TaskItem
import com.example.data.repository.McaRepository
import com.example.integration.DeviceIntegrationHelper
import com.example.monetization.EntitlementManager
import com.example.monetization.McaEntitlementUpgradeDialog
import com.example.monetization.McaSafeAdMobBanner
import com.example.ui.components.AuthScreen
import com.example.ui.components.LiquidGlassFloatingBar
import com.example.ui.components.LiquidGlassIconButton
import com.example.ui.components.LiquidGlassShowcaseDialog
import com.example.ui.components.liquidGlassCanvasBackdrop
import com.example.ui.screens.AiSchedulerAndInboxScreen
import com.example.ui.screens.AnalyticsAndHubScreen
import com.example.ui.screens.DashboardTab
import com.example.ui.screens.FocusModeScreen
import com.example.ui.screens.McaMembershipScreen
import com.example.ui.screens.TasksWorkspaceTab
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.IridescentMint
import com.example.ui.theme.LiquidCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DataUiState
import com.example.viewmodel.McaViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        DeviceIntegrationHelper.ensureNotificationChannels(this)
        DeviceIntegrationHelper.registerDynamicShortcuts(this)

        setContent {
            var isDarkMode by rememberSaveable { mutableStateOf(true) }
            MyApplicationTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    McaRootNavigation(
                        launchIntent = intent,
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = { isDarkMode = !isDarkMode }
                    )
                }
            }
        }
    }
}

@Composable
fun McaRootNavigation(
    launchIntent: Intent?,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        AuthenticatedMcaWorkspace(
            currentUserId = user.uid,
            userEmail = user.email.orEmpty(),
            launchIntent = launchIntent,
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode
        )
    }
}

@Composable
fun AuthenticatedMcaWorkspace(
    currentUserId: String,
    userEmail: String,
    launchIntent: Intent?,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: McaViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val taskDao = com.example.data.local.AppDatabase.getInstance(app).taskDao()
                val offlineTier = EntitlementManager.getOfflineFallbackTier(app)
                McaViewModel(McaRepository(db, taskDao), currentUserId, offlineTier)
            }
        }
    )

    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val inboxState by viewModel.inboxState.collectAsStateWithLifecycle()
    val focusSessionsState by viewModel.focusSessionsState.collectAsStateWithLifecycle()
    val subscription by viewModel.subscriptionState.collectAsStateWithLifecycle()
    val monetizationConfig by viewModel.monetizationConfigState.collectAsStateWithLifecycle()
    val aiUsageLogs by viewModel.aiUsageLogsState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val calendarEvents by viewModel.calendarEvents.collectAsStateWithLifecycle()
    val focusRemainingSeconds by viewModel.focusRemainingSeconds.collectAsStateWithLifecycle()
    val focusRunning by viewModel.focusRunning.collectAsStateWithLifecycle()
    val selectedFocusTask by viewModel.selectedFocusTask.collectAsStateWithLifecycle()
    val upgradePromptFeature by viewModel.upgradePromptFeature.collectAsStateWithLifecycle()
    val aiQuotaExhaustedMessage by viewModel.aiQuotaExhaustedMessage.collectAsStateWithLifecycle()

    val effectiveTier = remember(subscription) { subscription.effectiveEntitledTier() }
    val remainingAiPair = remember(subscription, monetizationConfig) { viewModel.remainingAiAllowanceToday() }

    // Cache verified subscription locally for safe offline entitlement preservation
    LaunchedEffect(subscription) {
        EntitlementManager.cacheVerifiedSubscription(context, subscription)
    }

    val tasks = (tasksState as? DataUiState.Success)?.data ?: emptyList()
    val inboxItems = (inboxState as? DataUiState.Success)?.data ?: emptyList()
    val focusSessions = (focusSessionsState as? DataUiState.Success)?.data ?: emptyList()
    val conflictAlerts = remember(tasks) { viewModel.detectSchedulingConflicts(tasks) }
    val totalFocusMinutes = remember(focusSessions, tasks) {
        focusSessions.sumOf { it.durationMinutes } + tasks.sumOf { it.actualMinutesSpent }
    }

    var currentTab by rememberSaveable { mutableStateOf("DASHBOARD") }

    // BackHandler on all secondary tabs to return cleanly to Dashboard
    BackHandler(enabled = currentTab != "DASHBOARD") {
        currentTab = "DASHBOARD"
    }

    // Handle Android Share Intents, App Shortcuts, and External AI Deep Links (mca://assistant/action)
    LaunchedEffect(launchIntent) {
        viewModel.setCalendarEvents(DeviceIntegrationHelper.readUpcomingCalendarEvents(context))
        val action = launchIntent?.action
        val quickAction = launchIntent?.getStringExtra(DeviceIntegrationHelper.EXTRA_QUICK_ACTION)
        if (quickAction == DeviceIntegrationHelper.ACTION_AI_ORGANISER ||
            quickAction == DeviceIntegrationHelper.ACTION_VOICE_CAPTURE
        ) {
            currentTab = "AI_INBOX"
        } else if (quickAction == DeviceIntegrationHelper.ACTION_QUICK_TASK) {
            currentTab = "TASKS"
        } else if (action == Intent.ACTION_SEND && launchIntent.type == "text/plain") {
            val sharedText = launchIntent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (sharedText.isNotBlank()) {
                viewModel.addInboxCapture(sharedText, "SHARE_INTENT")
                currentTab = "AI_INBOX"
            }
        } else if (action == Intent.ACTION_VIEW && launchIntent.data?.scheme == "mca") {
            val uri = launchIntent.data
            val title = uri?.getQueryParameter("title").orEmpty()
            if (title.isNotBlank()) {
                viewModel.createTask(
                    TaskItem(
                        title = title,
                        category = uri?.getQueryParameter("category") ?: "External AI",
                        priority = uri?.getQueryParameter("priority") ?: "HIGH"
                    )
                )
            }
        }
    }

    fun executeExternalAssistantPayload(rawJson: String) {
        try {
            val json = JSONObject(rawJson)
            val title = json.optString("title", "External Assistant List")
            val reminder = json.optString("reminder", "Saturday 09:00")
            val itemsArr = json.optJSONArray("items")
            val subtasks = buildString {
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        append("[ ] ").append(itemsArr.getString(i)).append("\n")
                    }
                }
            }.trim()
            viewModel.createTask(
                TaskItem(
                    title = title,
                    description = "Created via External AI Assistant Integration",
                    category = "Personal",
                    project = "Assistant Sync",
                    priority = "HIGH",
                    reminderOffsetsText = reminder,
                    subtasksText = subtasks
                )
            )
        } catch (e: Exception) {
            viewModel.showBanner("Invalid assistant JSON payload: ${e.localizedMessage}")
        }
    }

    val navItems = listOf(
        Triple("DASHBOARD", "Home", Icons.Default.Dashboard),
        Triple("TASKS", "Tasks", Icons.Default.CheckCircle),
        Triple("AI_INBOX", "MCA AI", Icons.Default.AutoAwesome),
        Triple("FOCUS", "Focus", Icons.Default.Timer),
        Triple("MEMBERSHIP", "Plans", Icons.Default.WorkspacePremium),
        Triple("ANALYTICS", "Hub", Icons.Default.Analytics)
    )

    if (upgradePromptFeature != null || aiQuotaExhaustedMessage != null) {
        McaEntitlementUpgradeDialog(
            feature = upgradePromptFeature,
            aiQuotaMessage = aiQuotaExhaustedMessage,
            currentTier = effectiveTier,
            config = monetizationConfig,
            onDismiss = { viewModel.dismissUpgradePrompt() },
            onNavigateToMembership = { currentTab = "MEMBERSHIP" }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            floatingActionButton = {
                if (currentTab != "AI_INBOX" && currentTab != "MEMBERSHIP") {
                    FloatingActionButton(
                        onClick = { currentTab = "AI_INBOX" },
                        shape = CircleShape,
                        containerColor = ElectricViolet,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("floating_ai_organiser_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Open MCA AI Organiser")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("MCA AI", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        navItems.forEach { (key, label, icon) ->
                            NavigationBarItem(
                                selected = currentTab == key,
                                onClick = { currentTab = key },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_${key.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            var showGlassShowcase by rememberSaveable { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .liquidGlassCanvasBackdrop(isDarkTheme = isDarkMode)
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        navItems.forEach { (key, label, icon) ->
                            NavigationRailItem(
                                selected = currentTab == key,
                                onClick = { currentTab = key },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Top Liquid Glass Floating App Header with Membership Tier Badge
                    LiquidGlassFloatingBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        accentColor = LiquidCyan
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = LiquidCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MCA TASK MANAGER",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = when (effectiveTier) {
                                    "ULTRA" -> ElectricViolet.copy(alpha = 0.25f)
                                    "PRO" -> LiquidCyan.copy(alpha = 0.25f)
                                    else -> IridescentMint.copy(alpha = 0.22f)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .clickable { currentTab = "MEMBERSHIP" }
                                    .testTag("header_membership_tier_badge")
                            ) {
                                Text(
                                    text = "MCA $effectiveTier",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (effectiveTier) {
                                        "ULTRA" -> ElectricViolet
                                        "PRO" -> LiquidCyan
                                        else -> IridescentMint
                                    },
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiquidGlassIconButton(
                                icon = Icons.Default.Layers,
                                contentDescription = "Inspect Liquid Glass Design System",
                                onClick = { showGlassShowcase = true },
                                accentColor = ElectricViolet,
                                modifier = Modifier.testTag("liquid_glass_showcase_button")
                            )
                            LiquidGlassIconButton(
                                icon = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark/Light Theme",
                                onClick = onToggleDarkMode,
                                accentColor = LiquidCyan,
                                modifier = Modifier.testTag("theme_toggle_button")
                            )
                        }
                    }

                    if (showGlassShowcase) {
                        LiquidGlassShowcaseDialog(
                            onDismiss = { showGlassShowcase = false }
                        )
                    }

                    // Transient status banner
                    AnimatedVisibility(visible = !statusBanner.isNullOrBlank()) {
                        Surface(
                            color = IridescentMint.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = statusBanner.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IridescentMint,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.clearBanner() }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }

                    // Active Tab Content
                    Box(modifier = Modifier.weight(1f)) {
                        when (currentTab) {
                            "DASHBOARD" -> DashboardTab(
                                tasks = tasks,
                                focusMinutesTotal = totalFocusMinutes,
                                calendarEvents = calendarEvents,
                                aiState = aiState,
                                onQuickAddTask = { title, priority ->
                                    viewModel.createTask(
                                        TaskItem(
                                            title = title,
                                            priority = priority,
                                            category = "Personal",
                                            project = "Quick Capture",
                                            scheduledTimeSlot = "10:00 - 10:45"
                                        )
                                    )
                                },
                                onToggleTask = { viewModel.toggleTaskCompletion(it) },
                                onEditTask = { currentTab = "TASKS" },
                                onStartFocusOnTask = { task ->
                                    viewModel.selectTaskForFocus(task)
                                    currentTab = "FOCUS"
                                },
                                onRequestBriefing = { mode ->
                                    viewModel.generateDailyBriefingOrReview(mode, tasks)
                                },
                                onOpenAiOrganiser = { currentTab = "AI_INBOX" },
                                onOpenVoiceCapture = { currentTab = "AI_INBOX" },
                                effectiveTier = effectiveTier,
                                adsEnabledInConfig = monetizationConfig.adsEnabledForFreeTier,
                                onOpenMembership = { currentTab = "MEMBERSHIP" }
                            )
                            "TASKS" -> TasksWorkspaceTab(
                                tasks = tasks,
                                onCreateTask = { task ->
                                    viewModel.createTask(task) { created ->
                                        DeviceIntegrationHelper.sendActionableTaskNotification(
                                            context,
                                            created,
                                            "New Task Scheduled"
                                        )
                                    }
                                },
                                onUpdateTask = { viewModel.updateTask(it) },
                                onToggleTask = { viewModel.toggleTaskCompletion(it) },
                                onDuplicateTask = { viewModel.duplicateTask(it) },
                                onArchiveTask = { viewModel.archiveTask(it) },
                                onPostponeTask = { viewModel.postponeTask(it, 24) },
                                onDeleteTask = { viewModel.deleteTask(it) },
                                onStartFocus = { task ->
                                    viewModel.selectTaskForFocus(task)
                                    currentTab = "FOCUS"
                                },
                                effectiveTier = effectiveTier,
                                onRequestFeatureUpgrade = { feature ->
                                    viewModel.requestUpgradeForFeature(feature)
                                }
                            )
                            "AI_INBOX" -> AiSchedulerAndInboxScreen(
                                tasks = tasks,
                                inboxItems = inboxItems,
                                aiState = aiState,
                                conflictAlerts = conflictAlerts,
                                onSubmitAiPrompt = { prompt, inboxItem ->
                                    viewModel.generateAiPlanFromPrompt(prompt, tasks, inboxItem)
                                },
                                onConfirmAiProposal = { proposal ->
                                    viewModel.confirmAndApplyAiProposal(proposal)
                                },
                                onDismissAiProposal = {
                                    viewModel.dismissAiProposal()
                                },
                                onAutoDistributeSchedule = {
                                    viewModel.autoDistributeSchedule(tasks)
                                },
                                onAddInboxItem = { content, source ->
                                    viewModel.addInboxCapture(content, source)
                                },
                                onConvertInboxToTask = { item ->
                                    viewModel.convertInboxItemToTask(item)
                                },
                                onDeleteInboxItem = { itemId ->
                                    viewModel.deleteInboxItem(itemId)
                                },
                                effectiveTier = effectiveTier,
                                remainingAiPair = remainingAiPair,
                                onOpenMembership = { currentTab = "MEMBERSHIP" }
                            )
                            "FOCUS" -> FocusModeScreen(
                                tasks = tasks,
                                selectedTask = selectedFocusTask,
                                remainingSeconds = focusRemainingSeconds,
                                isRunning = focusRunning,
                                focusSessions = focusSessions,
                                onSelectTask = { viewModel.selectTaskForFocus(it) },
                                onSetDurationMinutes = { viewModel.setFocusDurationMinutes(it) },
                                onToggleTimer = { viewModel.toggleFocusTimer() },
                                onCompleteSessionNow = { mins -> viewModel.completeFocusSession(mins) }
                            )
                            "MEMBERSHIP" -> McaMembershipScreen(
                                subscription = subscription,
                                config = monetizationConfig,
                                aiUsageLogs = aiUsageLogs,
                                remainingAiPair = remainingAiPair,
                                userEmail = userEmail,
                                onSelectPlayProduct = { product, isPlayInstalled ->
                                    viewModel.initiatePlayBillingPurchase(product, isPlayInstalled)
                                },
                                onRestorePurchases = {
                                    viewModel.restorePlayBillingPurchases()
                                },
                                onDowngradeToFree = {
                                    viewModel.downgradeOrCancelSubscription()
                                },
                                onAdminVerifyTier = { tier, cycle, status ->
                                    viewModel.adminVerifyOrGrantTier(tier, cycle, status)
                                },
                                onSaveAdminConfig = { updatedConfig ->
                                    viewModel.saveAdminMonetizationConfig(updatedConfig, userEmail)
                                }
                            )
                            "ANALYTICS" -> AnalyticsAndHubScreen(
                                tasks = tasks,
                                focusSessions = focusSessions,
                                userEmail = userEmail,
                                onRefreshCalendarEvents = {
                                    viewModel.setCalendarEvents(
                                        DeviceIntegrationHelper.readUpcomingCalendarEvents(context)
                                    )
                                },
                                onExecuteExternalAssistantCommand = { payload ->
                                    executeExternalAssistantPayload(payload)
                                },
                                onSignOut = {
                                    signOut(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onSignOutComplete = {},
                                        scope = scope
                                    )
                                },
                                effectiveTier = effectiveTier,
                                onRequestFeatureUpgrade = { feature ->
                                    viewModel.requestUpgradeForFeature(feature)
                                },
                                onOpenMembership = { currentTab = "MEMBERSHIP" }
                            )
                        }
                    }

                    // Non-Intrusive Google AdMob Test Banner strictly on eligible non-productivity-blocking views
                    // Never shown in Focus Mode, Task Editor, Voice Recording, or Calendar Interactions
                    if (currentTab == "DASHBOARD" || currentTab == "ANALYTICS") {
                        McaSafeAdMobBanner(
                            effectiveTier = effectiveTier,
                            adsEnabledInConfig = monetizationConfig.adsEnabledForFreeTier,
                            currentTab = currentTab,
                            isFocusModeActive = focusRunning || currentTab == "FOCUS",
                            onUpgradeClick = { currentTab = "MEMBERSHIP" },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
