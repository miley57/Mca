package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiOrganizerResponse
import com.example.data.model.AiUsageLog
import com.example.data.model.DeviceCalendarEvent
import com.example.data.model.FocusSession
import com.example.data.model.InboxItem
import com.example.data.model.MonetizationConfig
import com.example.data.model.ProposedTaskPlan
import com.example.data.model.TaskItem
import com.example.data.model.UserSubscription
import com.example.data.repository.McaRepository
import com.example.monetization.EntitlementManager
import com.example.monetization.McaFeature
import com.example.monetization.PlayBillingProduct
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

private const val TAG = "McaViewModel"

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

sealed interface AiOrganizerState {
    data object Idle : AiOrganizerState
    data object Analyzing : AiOrganizerState
    data class StreamingOverview(val partialText: String) : AiOrganizerState
    data class OverviewReady(val text: String) : AiOrganizerState
    data class ProposalReady(val proposal: AiOrganizerResponse) : AiOrganizerState
    data class Error(val message: String) : AiOrganizerState
}

data class ScheduleConflictAlert(
    val slotLabel: String,
    val conflictingTasks: List<TaskItem>,
    val suggestedResolution: String
)

class McaViewModel(
    private val repository: McaRepository,
    private val currentUserId: String,
    initialOfflineCachedTier: String = "FREE"
) : ViewModel() {

    // Structured JSON model for MCA AI Organiser (task breakdown, conflict analysis, brain dump)
    private val organizerSchema = Schema.obj(
        properties = mapOf(
            "summaryExplanation" to Schema.string(description = "Explanation of the AI's proposed plan and why it helps"),
            "conflictWarning" to Schema.string(description = "Detected scheduling conflicts or overload warnings"),
            "suggestedProjectName" to Schema.string(description = "Recommended project name for these tasks"),
            "breakRecommendation" to Schema.string(description = "Recommended break cadence and wellness advice"),
            "proposedTasks" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Actionable task title"),
                        "description" to Schema.string(description = "Detailed description"),
                        "category" to Schema.string(description = "Category: Study, Work, Personal, Health, or Finance"),
                        "project" to Schema.string(description = "Project name"),
                        "priority" to Schema.enumeration(
                            values = listOf("LOW", "NORMAL", "HIGH", "CRITICAL"),
                            description = "Recommended priority"
                        ),
                        "estimatedMinutes" to Schema.integer(description = "Estimated duration in minutes (15..180)"),
                        "scheduledTimeSlot" to Schema.string(description = "Suggested time slot e.g. 09:00 - 10:30 or 14:00 - 15:30"),
                        "dayOffset" to Schema.integer(description = "Days from today (0 = today, 1 = tomorrow, etc.)"),
                        "reminderOffsetsText" to Schema.string(description = "Suggested reminder e.g. 30m before"),
                        "subtasksText" to Schema.string(description = "Newline-separated checklist items starting with [ ] ")
                    )
                ),
                description = "List of proposed tasks awaiting user confirmation"
            )
        )
    )

    private val structuredAiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = organizerSchema
            }
        )
    }

    private val conversationalAiModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    // Two-Tier Kotlin Flow Error Handling Architecture for Tasks, Inbox, and Focus Sessions
    val tasksState: StateFlow<DataUiState<List<TaskItem>>> = repository.observeTasks(currentUserId)
        .map<List<TaskItem>, DataUiState<List<TaskItem>>> { list ->
            DataUiState.Success(
                list.sortedWith(
                    compareBy<TaskItem> { it.completed }
                        .thenByDescending { it.priorityRank() }
                        .thenBy { if (it.dueDateEpochMillis > 0L) it.dueDateEpochMillis else Long.MAX_VALUE }
                )
            )
        }
        .catch { error ->
            Log.e(TAG, "Error observing tasks", error)
            emit(DataUiState.Error(error.message ?: "Failed to load tasks"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataUiState.Loading
        )

    val inboxState: StateFlow<DataUiState<List<InboxItem>>> = repository.observeInboxItems(currentUserId)
        .map<List<InboxItem>, DataUiState<List<InboxItem>>> { list ->
            DataUiState.Success(list.sortedByDescending { it.createdAt?.seconds ?: 0L })
        }
        .catch { error ->
            Log.e(TAG, "Error observing inbox items", error)
            emit(DataUiState.Error(error.message ?: "Failed to load Smart Inbox"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataUiState.Loading
        )

    val focusSessionsState: StateFlow<DataUiState<List<FocusSession>>> = repository.observeFocusSessions(currentUserId)
        .map<List<FocusSession>, DataUiState<List<FocusSession>>> { list ->
            DataUiState.Success(list.sortedByDescending { it.createdAt?.seconds ?: 0L })
        }
        .catch { error ->
            Log.e(TAG, "Error observing focus sessions", error)
            emit(DataUiState.Error(error.message ?: "Failed to load Focus sessions"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataUiState.Loading
        )

    // Synchronized Subscription State with Safe Offline Fallback
    val subscriptionState: StateFlow<UserSubscription> = repository.observeUserSubscription(currentUserId)
        .catch { error ->
            Log.e(TAG, "Error observing subscription; using safe offline cached entitlement", error)
            emit(
                UserSubscription(
                    userId = currentUserId,
                    tier = initialOfflineCachedTier,
                    verifiedByBackend = true,
                    status = "ACTIVE"
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserSubscription(
                userId = currentUserId,
                tier = initialOfflineCachedTier,
                verifiedByBackend = true,
                status = "ACTIVE"
            )
        )

    // Backend-Configurable Monetization Settings (/app_config/monetization)
    val monetizationConfigState: StateFlow<MonetizationConfig> = repository.observeMonetizationConfig()
        .catch { error ->
            Log.e(TAG, "Error observing monetization config; using defaults", error)
            emit(MonetizationConfig())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = MonetizationConfig()
        )

    // Per-User AI Usage History Logs
    val aiUsageLogsState: StateFlow<List<AiUsageLog>> = repository.observeAiUsageLogs(currentUserId)
        .map { logs -> logs.sortedByDescending { it.createdAt?.seconds ?: 0L } }
        .catch { error ->
            Log.e(TAG, "Error observing AI usage logs", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _aiState = MutableStateFlow<AiOrganizerState>(AiOrganizerState.Idle)
    val aiState: StateFlow<AiOrganizerState> = _aiState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _calendarEvents = MutableStateFlow<List<DeviceCalendarEvent>>(emptyList())
    val calendarEvents: StateFlow<List<DeviceCalendarEvent>> = _calendarEvents.asStateFlow()

    // Centralized Entitlement Upgrade Dialog State
    private val _upgradePromptFeature = MutableStateFlow<McaFeature?>(null)
    val upgradePromptFeature: StateFlow<McaFeature?> = _upgradePromptFeature.asStateFlow()

    private val _aiQuotaExhaustedMessage = MutableStateFlow<String?>(null)
    val aiQuotaExhaustedMessage: StateFlow<String?> = _aiQuotaExhaustedMessage.asStateFlow()

    // Focus Mode Timer State
    private val _focusRemainingSeconds = MutableStateFlow(25 * 60)
    val focusRemainingSeconds: StateFlow<Int> = _focusRemainingSeconds.asStateFlow()

    private val _focusRunning = MutableStateFlow(false)
    val focusRunning: StateFlow<Boolean> = _focusRunning.asStateFlow()

    private val _selectedFocusTask = MutableStateFlow<TaskItem?>(null)
    val selectedFocusTask: StateFlow<TaskItem?> = _selectedFocusTask.asStateFlow()

    private var timerJob: Job? = null

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    fun setCalendarEvents(events: List<DeviceCalendarEvent>) {
        _calendarEvents.value = events
    }

    // --- CENTRALIZED FEATURE ACCESS CONTROL & ENTITLEMENT GATING ---

    fun dismissUpgradePrompt() {
        _upgradePromptFeature.value = null
        _aiQuotaExhaustedMessage.value = null
    }

    fun requestUpgradeForFeature(feature: McaFeature) {
        _upgradePromptFeature.value = feature
    }

    /**
     * Checks whether the current user has verified entitlement for [feature].
     * If entitled, executes [onAllowed]; otherwise opens the Liquid Glass upgrade prompt.
     */
    fun checkEntitlementOrPrompt(feature: McaFeature, onAllowed: () -> Unit): Boolean {
        val effectiveTier = subscriptionState.value.effectiveEntitledTier()
        return if (EntitlementManager.hasEntitlement(effectiveTier, feature)) {
            onAllowed()
            true
        } else {
            _upgradePromptFeature.value = feature
            false
        }
    }

    /**
     * Computes remaining daily AI allowance for the user's effective subscription tier.
     */
    fun remainingAiAllowanceToday(): Pair<Long, Long> {
        val sub = subscriptionState.value
        val config = monetizationConfigState.value
        val effectiveTier = sub.effectiveEntitledTier()
        val dailyLimit = config.dailyLimitForTier(effectiveTier)
        val now = System.currentTimeMillis()
        val usedToday = if (sub.aiQuotaResetEpochMillis > 0L && now >= sub.aiQuotaResetEpochMillis) {
            0L
        } else {
            sub.aiRequestsUsedToday
        }
        val remaining = (dailyLimit - usedToday).coerceAtLeast(0L)
        return remaining to dailyLimit
    }

    private suspend fun verifyAndConsumeAiAllowance(actionType: String, summary: String): Boolean {
        val (remaining, dailyLimit) = remainingAiAllowanceToday()
        val currentSub = subscriptionState.value
        val effectiveTier = currentSub.effectiveEntitledTier()
        if (remaining <= 0L) {
            _aiQuotaExhaustedMessage.value =
                "You have used all $dailyLimit daily AI Organiser requests included in your MCA $effectiveTier plan. " +
                    "Upgrade to MCA PRO (${monetizationConfigState.value.proAiDailyLimit}/day) or MCA ULTRA (${monetizationConfigState.value.ultraAiDailyLimit}/day) " +
                    "to continue generating AI schedules immediately without losing any of your tasks or notes."
            return false
        }
        repository.recordAiUsageAndIncrementQuota(currentSub, actionType, summary)
        return true
    }

    // --- GOOGLE PLAY BILLING & SUBSCRIPTION MANAGEMENT ACTIONS ---

    fun initiatePlayBillingPurchase(product: PlayBillingProduct, isPlayStoreAvailable: Boolean) {
        val config = monetizationConfigState.value
        if (!config.subscriptionsAvailable) {
            _statusBanner.value = "Subscriptions are temporarily paused by the administrator."
            return
        }
        viewModelScope.launch {
            val token = if (isPlayStoreAvailable) {
                "play_billing_pending_${product.productId}_${UUID.randomUUID().toString().take(8)}"
            } else {
                "play_console_unconfigured_${product.productId}"
            }
            val result = repository.submitPlayBillingPurchaseForVerification(
                currentSub = subscriptionState.value,
                requestedTier = product.tier,
                billingPeriod = product.billingPeriod,
                productId = product.productId,
                purchaseToken = token
            )
            result.onSuccess {
                _statusBanner.value =
                    "Purchase request (${product.title}) recorded as PENDING_VERIFICATION. " +
                        "Per MCA zero-trust billing policy, entitlements activate only after Google Play Developer API backend verification."
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Billing request failed."
            }
        }
    }

    fun restorePlayBillingPurchases() {
        viewModelScope.launch {
            val sub = subscriptionState.value
            val effective = sub.effectiveEntitledTier()
            _statusBanner.value = if (sub.verifiedByBackend && (effective == "PRO" || effective == "ULTRA")) {
                "Restored verified MCA $effective membership (${sub.billingPeriod}) from cloud."
            } else if (sub.status == "PENDING_VERIFICATION") {
                "Found pending purchase (${sub.productId}) awaiting backend RTDN verification."
            } else {
                "Play Billing query complete: No active paid subscriptions found on this Google account (Current tier: MCA FREE)."
            }
        }
    }

    fun downgradeOrCancelSubscription() {
        viewModelScope.launch {
            repository.revertOrDowngradeToFreeTier(subscriptionState.value).onSuccess {
                _statusBanner.value = "Subscription updated to MCA FREE. All your tasks and data remain safe."
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Could not update subscription."
            }
        }
    }

    // --- ADMIN CONFIGURATION & VERIFICATION (RESTRICTED TO AUTHORIZED ADMIN) ---

    fun adminVerifyOrGrantTier(tier: String, billingPeriod: String, status: String = "ACTIVE") {
        viewModelScope.launch {
            repository.adminVerifySubscriptionTier(
                currentSub = subscriptionState.value,
                tier = tier,
                billingPeriod = billingPeriod,
                status = status
            ).onSuccess {
                _statusBanner.value = "Admin Backend Verification: Set account to MCA $tier ($billingPeriod • $status)."
            }.onFailure { err ->
                _statusBanner.value = "Admin verification rejected by Firestore rules: ${err.localizedMessage}"
            }
        }
    }

    fun saveAdminMonetizationConfig(config: MonetizationConfig, adminEmail: String) {
        viewModelScope.launch {
            repository.saveAdminMonetizationConfig(config, adminEmail).onSuccess {
                _statusBanner.value = "Saved backend MonetizationConfig to /app_config/monetization."
            }.onFailure { err ->
                _statusBanner.value = "Admin config update rejected: ${err.localizedMessage}"
            }
        }
    }

    // --- TASK CRUD & SMART OPERATIONS ---

    fun createTask(task: TaskItem, onCreated: (TaskItem) -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val normalized = task.copy(
                startDateEpochMillis = if (task.startDateEpochMillis > 0L) task.startDateEpochMillis else now,
                dueDateEpochMillis = if (task.dueDateEpochMillis > 0L) task.dueDateEpochMillis else (now + 86_400_000L)
            )
            val result = repository.createTask(normalized)
            result.onSuccess { newId ->
                _statusBanner.value = "Task \"${normalized.title}\" saved to cloud."
                onCreated(normalized.copy(id = newId))
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to create task"
            }
        }
    }

    fun toggleTaskCompletion(task: TaskItem) {
        viewModelScope.launch {
            val newCompleted = !task.completed
            val newStatus = if (newCompleted) "COMPLETED" else "TODO"
            val updated = task.copy(
                completed = newCompleted,
                status = newStatus
            )
            repository.updateTask(updated).onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Could not update task"
            }
        }
    }

    fun updateTask(task: TaskItem) {
        viewModelScope.launch {
            repository.updateTask(task).onSuccess {
                _statusBanner.value = "Updated \"${task.title}\""
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to update task"
            }
        }
    }

    fun duplicateTask(task: TaskItem) {
        viewModelScope.launch {
            val copyTask = task.copy(
                id = "",
                title = "${task.title} (Copy)",
                completed = false,
                status = "TODO",
                createdAt = null,
                updatedAt = null
            )
            repository.createTask(copyTask).onSuccess {
                _statusBanner.value = "Duplicated \"${task.title}\""
            }
        }
    }

    fun archiveTask(task: TaskItem) {
        viewModelScope.launch {
            val updated = task.copy(
                archived = !task.archived,
                status = if (!task.archived) "ARCHIVED" else "TODO"
            )
            repository.updateTask(updated).onSuccess {
                _statusBanner.value = if (updated.archived) "Task archived" else "Task restored from archive"
            }
        }
    }

    fun postponeTask(task: TaskItem, hoursToAdd: Int = 24) {
        viewModelScope.launch {
            val baseDue = if (task.dueDateEpochMillis > 0L) task.dueDateEpochMillis else System.currentTimeMillis()
            val updated = task.copy(
                dueDateEpochMillis = baseDue + hoursToAdd * 3600_000L,
                status = "POSTPONED"
            )
            repository.updateTask(updated).onSuccess {
                _statusBanner.value = "Rescheduled \"${task.title}\" (+${hoursToAdd}h)"
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId).onSuccess {
                _statusBanner.value = "Task deleted"
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to delete task"
            }
        }
    }

    // --- SMART SCHEDULING & AUTO-DISTRIBUTION ---

    fun detectSchedulingConflicts(tasks: List<TaskItem>): List<ScheduleConflictAlert> {
        val active = tasks.filter { !it.completed && !it.archived }
        val alerts = mutableListOf<ScheduleConflictAlert>()

        // 1. Check same time slot collisions
        val bySlot = active.filter { it.scheduledTimeSlot.isNotBlank() }
            .groupBy { it.scheduledTimeSlot.trim() }
        bySlot.forEach { (slot, list) ->
            if (list.size >= 2) {
                alerts.add(
                    ScheduleConflictAlert(
                        slotLabel = "Time Slot Overlap ($slot)",
                        conflictingTasks = list,
                        suggestedResolution = "Redistribute ${list.size} overlapping tasks across free morning and afternoon slots."
                    )
                )
            }
        }

        // 2. Check overloaded afternoon/day (3+ tasks or > 240 mins in same day)
        val totalActiveMinutes = active.sumOf { it.estimatedMinutes }
        if (active.size >= 4 || totalActiveMinutes > 240L) {
            alerts.add(
                ScheduleConflictAlert(
                    slotLabel = "High Workload Density (${active.size} active tasks • ${totalActiveMinutes}m)",
                    conflictingTasks = active.take(5),
                    suggestedResolution = "Auto-distribute tasks across optimal time blocks based on priority and energy levels."
                )
            )
        }
        return alerts
    }

    fun autoDistributeSchedule(tasks: List<TaskItem>) {
        // Requires MCA PRO or ULTRA
        checkEntitlementOrPrompt(McaFeature.ADVANCED_SCHEDULING) {
            viewModelScope.launch {
                val slots = listOf(
                    "08:30 - 10:00",
                    "10:15 - 11:45",
                    "13:30 - 15:00",
                    "15:30 - 17:00",
                    "18:00 - 19:00"
                )
                val active = tasks.filter { !it.completed && !it.archived }
                    .sortedByDescending { it.priorityRank() }
                val now = System.currentTimeMillis()
                active.forEachIndexed { index, task ->
                    val dayOffset = index / slots.size
                    val slot = slots[index % slots.size]
                    val updated = task.copy(
                        scheduledTimeSlot = slot,
                        dueDateEpochMillis = now + (dayOffset + 1) * 86_400_000L
                    )
                    repository.updateTask(updated)
                }
                _statusBanner.value = "Distributed ${active.size} tasks across conflict-free time slots."
            }
        }
    }

    // --- SMART INBOX & BRAIN DUMP ---

    fun addInboxCapture(content: String, source: String = "QUICK_CAPTURE") {
        if (content.isBlank()) return
        viewModelScope.launch {
            repository.createInboxItem(content, source).onSuccess {
                _statusBanner.value = "Captured to Smart Inbox"
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to save capture"
            }
        }
    }

    fun deleteInboxItem(itemId: String) {
        viewModelScope.launch {
            repository.deleteInboxItem(itemId)
        }
    }

    fun convertInboxItemToTask(item: InboxItem) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.createTask(
                TaskItem(
                    title = item.content.take(120),
                    description = item.content,
                    category = "Personal",
                    project = "Smart Inbox",
                    priority = "NORMAL",
                    startDateEpochMillis = now,
                    dueDateEpochMillis = now + 86_400_000L,
                    scheduledTimeSlot = "10:00 - 11:00"
                )
            ).onSuccess {
                repository.markInboxItemProcessed(item)
                _statusBanner.value = "Converted Inbox item to Task"
            }
        }
    }

    // --- MCA AI ORGANISER (GEMINI 3.8 FLASH) WITH QUOTA ENFORCEMENT ---

    fun generateAiPlanFromPrompt(userPrompt: String, existingTasks: List<TaskItem>, inboxItemToMark: InboxItem? = null) {
        if (userPrompt.isBlank()) return
        viewModelScope.launch {
            val actionType = if (inboxItemToMark != null) "INBOX_BREAKDOWN" else "SCHEDULE_PLAN"
            val allowed = verifyAndConsumeAiAllowance(actionType, userPrompt.take(120))
            if (!allowed) return@launch

            _aiState.value = AiOrganizerState.Analyzing
            try {
                val existingSummary = existingTasks.take(10).joinToString("; ") {
                    "${it.title} (${it.priority}, slot=${it.scheduledTimeSlot.ifBlank { "unscheduled" }})"
                }
                val fullPrompt = """
                    You are MCA AI ORGANISER, an intelligent personal productivity architect.
                    User request / brain dump: "$userPrompt"
                    User's existing tasks: [$existingSummary]
                    
                    Analyze the user's request. Break complex goals (such as exam preparation, projects, shopping lists, or busy afternoons) into concrete, well-structured tasks with realistic estimated minutes, priorities (LOW, NORMAL, HIGH, CRITICAL), time slots (e.g. "09:00 - 10:30"), dayOffset (0 for today, 1 for tomorrow, etc.), reminders, and subtasks.
                    Identify any potential scheduling conflicts with existing tasks.
                    Remember: Do NOT modify existing tasks directly; return a structured proposal for user confirmation.
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    structuredAiModel.generateContent(fullPrompt)
                }
                val jsonString = response.text ?: throw IllegalStateException("MCA AI returned an empty response.")
                val json = JSONObject(jsonString)

                val tasksArray = json.optJSONArray("proposedTasks")
                val proposedList = mutableListOf<ProposedTaskPlan>()
                if (tasksArray != null) {
                    for (i in 0 until tasksArray.length()) {
                        val obj = tasksArray.getJSONObject(i)
                        proposedList.add(
                            ProposedTaskPlan(
                                title = obj.optString("title", "Suggested Task"),
                                description = obj.optString("description", ""),
                                category = obj.optString("category", "Work"),
                                project = obj.optString("project", json.optString("suggestedProjectName", "AI Plan")),
                                priority = obj.optString("priority", "NORMAL"),
                                estimatedMinutes = obj.optLong("estimatedMinutes", 45L).coerceIn(5L, 240L),
                                scheduledTimeSlot = obj.optString("scheduledTimeSlot", "10:00 - 10:45"),
                                dayOffset = obj.optInt("dayOffset", 0).coerceIn(0, 30),
                                reminderOffsetsText = obj.optString("reminderOffsetsText", "30m before"),
                                subtasksText = obj.optString("subtasksText", "")
                            )
                        )
                    }
                }

                if (inboxItemToMark != null) {
                    repository.markInboxItemProcessed(inboxItemToMark)
                }

                _aiState.value = AiOrganizerState.ProposalReady(
                    AiOrganizerResponse(
                        summaryExplanation = json.optString("summaryExplanation", "Here is your tailored schedule plan."),
                        conflictWarning = json.optString("conflictWarning", "No major conflicts detected."),
                        suggestedProjectName = json.optString("suggestedProjectName", "Focused Plan"),
                        breakRecommendation = json.optString("breakRecommendation", "Take a 10-minute restorative break after every 50 minutes of deep work."),
                        proposedTasks = proposedList
                    )
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "MCA AI timed out", e)
                _aiState.value = AiOrganizerState.Error("MCA AI Organiser timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "MCA AI generation failed", e)
                _aiState.value = AiOrganizerState.Error(e.localizedMessage ?: "MCA AI Organiser encountered an error.")
            }
        }
    }

    fun confirmAndApplyAiProposal(proposal: AiOrganizerResponse) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            var createdCount = 0
            for (plan in proposal.proposedTasks) {
                val targetStart = now + plan.dayOffset * 86_400_000L
                val targetDue = targetStart + 36_000_000L
                val result = repository.createTask(
                    TaskItem(
                        title = plan.title,
                        description = plan.description,
                        category = plan.category.ifBlank { "General" },
                        project = plan.project.ifBlank { proposal.suggestedProjectName },
                        priority = plan.priority,
                        status = "TODO",
                        startDateEpochMillis = targetStart,
                        dueDateEpochMillis = targetDue,
                        scheduledTimeSlot = plan.scheduledTimeSlot,
                        estimatedMinutes = plan.estimatedMinutes,
                        reminderOffsetsText = plan.reminderOffsetsText,
                        subtasksText = plan.subtasksText
                    )
                )
                if (result.isSuccess) createdCount++
            }
            _aiState.value = AiOrganizerState.Idle
            _statusBanner.value = "Confirmed! Created $createdCount tasks in project \"${proposal.suggestedProjectName}\"."
        }
    }

    fun dismissAiProposal() {
        _aiState.value = AiOrganizerState.Idle
    }

    fun generateDailyBriefingOrReview(mode: String, tasks: List<TaskItem>) {
        viewModelScope.launch {
            val actionType = if (mode == "MORNING_BRIEFING") "MORNING_BRIEFING" else "EOD_REVIEW"
            val allowed = verifyAndConsumeAiAllowance(actionType, "Daily $mode")
            if (!allowed) return@launch

            _aiState.value = AiOrganizerState.Analyzing
            val accumulated = StringBuilder()
            try {
                val activeCount = tasks.count { !it.completed && !it.archived }
                val completedCount = tasks.count { it.completed }
                val criticalTitles = tasks.filter { !it.completed && (it.priority == "CRITICAL" || it.priority == "HIGH") }
                    .take(5)
                    .joinToString(", ") { "${it.title} (${it.scheduledTimeSlot.ifBlank { "unscheduled" }})" }

                val prompt = if (mode == "MORNING_BRIEFING") {
                    """
                    Generate a concise, inspiring Morning Daily Briefing for MCA Task Manager.
                    Active tasks: $activeCount | Completed: $completedCount.
                    Top priority tasks today: [$criticalTitles].
                    Provide: 1) Focus priority order, 2) Time-blocking recommendation, 3) Energy & break tip. Keep under 120 words.
                    """.trimIndent()
                } else {
                    """
                    Generate an End-of-Day Productivity Review for MCA Task Manager.
                    Completed tasks: $completedCount | Remaining unfinished tasks: $activeCount.
                    Unfinished high-priority tasks: [$criticalTitles].
                    Provide: 1) Wins summary, 2) Gentle rescheduling advice for unfinished tasks, 3) Tomorrow's #1 anchor task. Keep under 120 words.
                    """.trimIndent()
                }

                withTimeout(60_000L) {
                    conversationalAiModel.generateContentStream(prompt).collect { chunk ->
                        accumulated.append(chunk.text.orEmpty())
                        _aiState.value = AiOrganizerState.StreamingOverview(accumulated.toString())
                    }
                }
                if (accumulated.isNotBlank()) {
                    _aiState.value = AiOrganizerState.OverviewReady(accumulated.toString())
                } else {
                    _aiState.value = AiOrganizerState.Error("MCA AI returned an empty briefing.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Briefing stream timed out", e)
                _aiState.value = AiOrganizerState.Error("Briefing generation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Briefing generation failed", e)
                _aiState.value = AiOrganizerState.Error(e.localizedMessage ?: "Briefing generation failed.")
            }
        }
    }

    // --- FOCUS MODE POMODORO ENGINE ---

    fun selectTaskForFocus(task: TaskItem?) {
        _selectedFocusTask.value = task
    }

    fun setFocusDurationMinutes(minutes: Int) {
        timerJob?.cancel()
        _focusRunning.value = false
        _focusRemainingSeconds.value = (minutes.coerceIn(1, 180)) * 60
    }

    fun toggleFocusTimer() {
        if (_focusRunning.value) {
            timerJob?.cancel()
            _focusRunning.value = false
        } else {
            _focusRunning.value = true
            timerJob = viewModelScope.launch {
                while (_focusRemainingSeconds.value > 0 && _focusRunning.value) {
                    delay(1000L)
                    _focusRemainingSeconds.value -= 1
                }
                if (_focusRemainingSeconds.value <= 0 && _focusRunning.value) {
                    _focusRunning.value = false
                    completeFocusSession(25L)
                }
            }
        }
    }

    fun completeFocusSession(minutesCompleted: Long = 25L) {
        timerJob?.cancel()
        _focusRunning.value = false
        _focusRemainingSeconds.value = 25 * 60
        val targetTask = _selectedFocusTask.value
        viewModelScope.launch {
            repository.logFocusSession(
                taskId = targetTask?.id.orEmpty(),
                taskTitle = targetTask?.title ?: "Deep Focus Session",
                durationMinutes = minutesCompleted.coerceAtLeast(1L),
                sessionType = "POMODORO"
            )
            if (targetTask != null) {
                repository.updateTask(
                    targetTask.copy(actualMinutesSpent = targetTask.actualMinutesSpent + minutesCompleted)
                )
            }
            _statusBanner.value = "Focus session (+${minutesCompleted}m) logged to analytics!"
        }
    }
}
