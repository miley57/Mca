package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.Task
import com.example.data.local.TaskDao
import com.example.data.local.toRoomTask
import com.example.data.model.AiUsageLog
import com.example.data.model.FocusSession
import com.example.data.model.InboxItem
import com.example.data.model.MonetizationConfig
import com.example.data.model.TaskItem
import com.example.data.model.UserSubscription
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import java.util.UUID

class McaRepository(
    private val db: FirebaseFirestore,
    private val taskDao: TaskDao? = null
) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        AppDatabase.getInstance(context.applicationContext).taskDao()
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    /**
     * Exposes reactive Room local SQLite Flow of [Task] entities for offline-ready access.
     */
    fun observeLocalRoomTasks(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<Task>> {
        val dao = taskDao ?: return emptyFlow()
        return dao.observeTasksForUser(userId)
    }

    fun observeTasks(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<TaskItem>> = flow {
        val resolvedUid = userId.ifBlank { auth.currentUser?.uid ?: "unauthenticated_user" }
        val path = "users/$resolvedUid/tasks"
        emitAll(
            db.collection("users").document(resolvedUid).collection("tasks")
                .whereEqualTo("userId", resolvedUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TaskItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .onEach { tasks ->
                    taskDao?.upsertTasks(tasks.map { it.toRoomTask() })
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeInboxItems(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<InboxItem>> = flow {
        val resolvedUid = userId.ifBlank { auth.currentUser?.uid ?: "unauthenticated_user" }
        val path = "users/$resolvedUid/inbox_items"
        emitAll(
            db.collection("users").document(resolvedUid).collection("inbox_items")
                .whereEqualTo("userId", resolvedUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(InboxItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeFocusSessions(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<FocusSession>> = flow {
        val resolvedUid = userId.ifBlank { auth.currentUser?.uid ?: "unauthenticated_user" }
        val path = "users/$resolvedUid/focus_sessions"
        emitAll(
            db.collection("users").document(resolvedUid).collection("focus_sessions")
                .whereEqualTo("userId", resolvedUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FocusSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeUserSubscription(userId: String = auth.currentUser?.uid.orEmpty()): Flow<UserSubscription> = flow {
        val resolvedUid = userId.ifBlank { auth.currentUser?.uid ?: "unauthenticated_user" }
        val path = "users/$resolvedUid/subscription/current"
        emitAll(
            db.collection("users").document(resolvedUid).collection("subscription").document("current")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserSubscription::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?: UserSubscription(userId = resolvedUid)
                    } else {
                        UserSubscription(userId = resolvedUid)
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }

    fun observeAiUsageLogs(userId: String = auth.currentUser?.uid.orEmpty()): Flow<List<AiUsageLog>> = flow {
        val resolvedUid = userId.ifBlank { auth.currentUser?.uid ?: "unauthenticated_user" }
        val path = "users/$resolvedUid/ai_usage_logs"
        emitAll(
            db.collection("users").document(resolvedUid).collection("ai_usage_logs")
                .whereEqualTo("userId", resolvedUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AiUsageLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeMonetizationConfig(): Flow<MonetizationConfig> = flow {
        val path = "app_config/monetization"
        emitAll(
            db.collection("app_config").document("monetization")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(MonetizationConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?: MonetizationConfig()
                    } else {
                        MonetizationConfig()
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }

    suspend fun createTask(task: TaskItem): Result<String> {
        val uid = requireUserId()
        val taskId = task.id.ifBlank { "task_${UUID.randomUUID().toString().replace("-", "").take(20)}" }
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        val payload = task.copy(
            id = taskId,
            userId = uid,
            title = task.title.trim().take(200).ifEmpty { "Untitled Task" },
            category = task.category.trim().take(60).ifEmpty { "General" },
            estimatedMinutes = task.estimatedMinutes.coerceIn(1L, 1440L),
            actualMinutesSpent = task.actualMinutesSpent.coerceIn(0L, 100000L),
            createdAt = null,
            updatedAt = null
        )
        return try {
            taskDao?.upsertTask(payload.toRoomTask())
            docRef.set(payload).await()
            Result.success(taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserTasks(): Result<List<TaskItem>> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("tasks")
        return try {
            val snapshot = colRef.whereEqualTo("userId", uid).get().await()
            val items = snapshot.toObjects(TaskItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            taskDao?.upsertTasks(items.map { it.toRoomTask() })
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun getTaskById(ownerUid: String, taskId: String): Result<TaskItem?> {
        val docRef = db.collection("users").document(ownerUid).collection("tasks").document(taskId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                snap.toObject(TaskItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateTask(task: TaskItem): Result<Unit> {
        val uid = requireUserId()
        require(task.id.isNotBlank()) { "Task ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
        return try {
            val existingCreatedAt = task.createdAt ?: docRef.get().await().getTimestamp("createdAt")
            requireNotNull(existingCreatedAt) { "Task createdAt must not be null for update" }
            val updated = task.copy(
                userId = uid,
                title = task.title.trim().take(200).ifEmpty { "Untitled Task" },
                category = task.category.trim().take(60).ifEmpty { "General" },
                estimatedMinutes = task.estimatedMinutes.coerceIn(1L, 1440L),
                actualMinutesSpent = task.actualMinutesSpent.coerceIn(0L, 100000L),
                createdAt = existingCreatedAt,
                updatedAt = null
            )
            taskDao?.upsertTask(updated.toRoomTask())
            docRef.set(updated).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        return try {
            taskDao?.deleteTaskById(taskId)
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createInboxItem(content: String, source: String): Result<String> {
        val uid = requireUserId()
        val itemId = "inbox_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("users").document(uid).collection("inbox_items").document(itemId)
        val item = InboxItem(
            id = itemId,
            userId = uid,
            content = content.trim().take(2000).ifEmpty { "Quick note" },
            source = source,
            processedByAi = false,
            createdAt = null,
            updatedAt = null
        )
        return try {
            docRef.set(item).await()
            Result.success(itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun markInboxItemProcessed(item: InboxItem): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("inbox_items").document(item.id)
        return try {
            val existingCreatedAt = item.createdAt ?: docRef.get().await().getTimestamp("createdAt")
            requireNotNull(existingCreatedAt) { "InboxItem createdAt must not be null" }
            docRef.set(
                item.copy(
                    userId = uid,
                    processedByAi = true,
                    createdAt = existingCreatedAt,
                    updatedAt = null
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteInboxItem(itemId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("inbox_items").document(itemId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun logFocusSession(
        taskId: String,
        taskTitle: String,
        durationMinutes: Long,
        sessionType: String
    ): Result<String> {
        val uid = requireUserId()
        val sessionId = "focus_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("users").document(uid).collection("focus_sessions").document(sessionId)
        val session = FocusSession(
            id = sessionId,
            userId = uid,
            taskId = taskId.take(128),
            taskTitle = taskTitle.trim().take(200).ifEmpty { "Deep Focus" },
            durationMinutes = durationMinutes.coerceIn(1L, 240L),
            sessionType = sessionType,
            completed = true,
            createdAt = null,
            updatedAt = null
        )
        return try {
            docRef.set(session).await()
            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- MONETIZATION, SUBSCRIPTION & AI QUOTA PERSISTENCE ---

    suspend fun recordAiUsageAndIncrementQuota(
        currentSub: UserSubscription,
        actionType: String,
        promptSummary: String
    ): Result<UserSubscription> {
        val uid = requireUserId()
        val subRef = db.collection("users").document(uid).collection("subscription").document("current")
        val logId = "ailog_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val logRef = db.collection("users").document(uid).collection("ai_usage_logs").document(logId)

        return try {
            val now = System.currentTimeMillis()
            val existingCreatedAt = currentSub.createdAt ?: runCatching {
                val snap = subRef.get().await()
                if (snap.exists()) snap.getTimestamp("createdAt") else null
            }.getOrNull()
            val needsReset = currentSub.aiQuotaResetEpochMillis <= now
            val nextResetMillis = if (needsReset) now + 86_400_000L else currentSub.aiQuotaResetEpochMillis
            val newUsedToday = if (needsReset) 1L else (currentSub.aiRequestsUsedToday + 1L)
            val newTotal = currentSub.aiRequestsTotalCount + 1L

            val updatedSub = currentSub.copy(
                id = "current",
                userId = uid,
                aiRequestsUsedToday = newUsedToday.coerceIn(0L, 100000L),
                aiRequestsTotalCount = newTotal.coerceIn(0L, 10000000L),
                aiQuotaResetEpochMillis = nextResetMillis,
                lastAiActionLabel = promptSummary.trim().take(200).ifEmpty { actionType },
                createdAt = existingCreatedAt,
                updatedAt = null
            )

            subRef.set(updatedSub).await()
            val savedSub = runCatching {
                subRef.get().await().toObject(UserSubscription::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.getOrNull() ?: updatedSub

            val usageLog = AiUsageLog(
                id = logId,
                userId = uid,
                actionType = actionType,
                promptSummary = promptSummary.trim().take(300).ifEmpty { "AI Organiser Request" },
                tierAtRequest = currentSub.effectiveEntitledTier(),
                createdAt = null
            )
            logRef.set(usageLog).await()

            Result.success(savedSub)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, subRef.path)
            Result.failure(e)
        }
    }

    /**
     * Submits a Google Play Billing purchase token for backend verification.
     * Never trusts client-side payment claims alone: sets verifiedByBackend = false and status = PENDING_VERIFICATION.
     */
    suspend fun submitPlayBillingPurchaseForVerification(
        currentSub: UserSubscription,
        requestedTier: String,
        billingPeriod: String,
        productId: String,
        purchaseToken: String
    ): Result<Unit> {
        val uid = requireUserId()
        val subRef = db.collection("users").document(uid).collection("subscription").document("current")
        return try {
            val existingCreatedAt = currentSub.createdAt ?: runCatching {
                val snap = subRef.get().await()
                if (snap.exists()) snap.getTimestamp("createdAt") else null
            }.getOrNull()
            val now = System.currentTimeMillis()
            val cycleDurationMillis = when (billingPeriod) {
                "ANNUAL" -> 365L * 86_400_000L
                "ONE_TIME" -> 3650L * 86_400_000L
                else -> 30L * 86_400_000L
            }
            val payload = currentSub.copy(
                id = "current",
                userId = uid,
                tier = requestedTier,
                billingPeriod = billingPeriod,
                status = "PENDING_VERIFICATION",
                productId = productId.take(120),
                purchaseToken = purchaseToken.take(500),
                verifiedByBackend = false,
                renewalDateEpochMillis = now + cycleDurationMillis,
                aiQuotaResetEpochMillis = if (currentSub.aiQuotaResetEpochMillis > 0L) currentSub.aiQuotaResetEpochMillis else (now + 86_400_000L),
                createdAt = existingCreatedAt,
                updatedAt = null
            )
            subRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, subRef.path)
            Result.failure(e)
        }
    }

    /**
     * Allows the user to downgrade back to MCA FREE or cancel a pending unverified purchase.
     */
    suspend fun revertOrDowngradeToFreeTier(currentSub: UserSubscription): Result<Unit> {
        val uid = requireUserId()
        val subRef = db.collection("users").document(uid).collection("subscription").document("current")
        return try {
            val existingCreatedAt = currentSub.createdAt ?: runCatching {
                val snap = subRef.get().await()
                if (snap.exists()) snap.getTimestamp("createdAt") else null
            }.getOrNull()
            val now = System.currentTimeMillis()
            val payload = currentSub.copy(
                id = "current",
                userId = uid,
                tier = "FREE",
                billingPeriod = "NONE",
                status = "ACTIVE",
                productId = "mca_free_default",
                purchaseToken = "",
                verifiedByBackend = true,
                renewalDateEpochMillis = 0L,
                aiQuotaResetEpochMillis = if (currentSub.aiQuotaResetEpochMillis > 0L) currentSub.aiQuotaResetEpochMillis else (now + 86_400_000L),
                createdAt = existingCreatedAt,
                updatedAt = null
            )
            subRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, subRef.path)
            Result.failure(e)
        }
    }

    /**
     * Strictly restricted by Firestore rules to verified admins (`isAdmin()`).
     * Allows the application owner to verify a pending Play Billing token or test tier entitlements.
     */
    suspend fun adminVerifySubscriptionTier(
        currentSub: UserSubscription,
        tier: String,
        billingPeriod: String,
        status: String = "ACTIVE"
    ): Result<Unit> {
        val uid = requireUserId()
        val subRef = db.collection("users").document(uid).collection("subscription").document("current")
        return try {
            val existingCreatedAt = currentSub.createdAt ?: runCatching {
                val snap = subRef.get().await()
                if (snap.exists()) snap.getTimestamp("createdAt") else null
            }.getOrNull()
            val now = System.currentTimeMillis()
            val renewal = if (tier == "FREE") 0L else (now + 30L * 86_400_000L)
            val payload = currentSub.copy(
                id = "current",
                userId = uid,
                tier = tier,
                billingPeriod = if (tier == "FREE") "NONE" else billingPeriod,
                status = status,
                productId = if (tier == "FREE") "mca_free_default" else "admin_verified_${tier.lowercase()}",
                purchaseToken = if (tier == "FREE") "" else "rtdn_verified_${UUID.randomUUID().toString().take(12)}",
                verifiedByBackend = true,
                renewalDateEpochMillis = renewal,
                aiQuotaResetEpochMillis = if (currentSub.aiQuotaResetEpochMillis > 0L) currentSub.aiQuotaResetEpochMillis else (now + 86_400_000L),
                createdAt = existingCreatedAt,
                updatedAt = null
            )
            subRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, subRef.path)
            Result.failure(e)
        }
    }

    /**
     * Strictly restricted by Firestore security rules to verified admins (`isAdmin()`).
     * Updates global backend monetization parameters at `/app_config/monetization`.
     */
    suspend fun saveAdminMonetizationConfig(config: MonetizationConfig, adminEmail: String): Result<Unit> {
        val docRef = db.collection("app_config").document("monetization")
        return try {
            val existingCreatedAt = config.createdAt ?: runCatching {
                val snap = docRef.get().await()
                if (snap.exists()) snap.getTimestamp("createdAt") else null
            }.getOrNull()
            val payload = config.copy(
                id = "monetization",
                freeAiDailyLimit = config.freeAiDailyLimit.coerceIn(1L, 1000L),
                proAiDailyLimit = config.proAiDailyLimit.coerceIn(1L, 10000L),
                ultraAiDailyLimit = config.ultraAiDailyLimit.coerceIn(1L, 100000L),
                updatedByEmail = adminEmail.trim().take(200).ifEmpty { "milesanjola57@gmail.com" },
                createdAt = existingCreatedAt,
                updatedAt = null
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }
}
