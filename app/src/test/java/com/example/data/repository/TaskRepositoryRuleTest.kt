package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.TaskItem
import com.example.data.model.UserSubscription
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createTask_validPayload_createsDocumentAndReturnsId() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = McaRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTask(
                TaskItem(
                    title = "Prepare Quantum Physics Notes",
                    category = "Study",
                    project = "Final Exams",
                    priority = "HIGH",
                    estimatedMinutes = 60L
                )
            )
        }
        assertTrue(createResult.isSuccess)
        val taskId = createResult.getOrThrow()
        assertTrue(taskId.isNotEmpty())
    }

    @Test
    fun observeTasks_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = McaRepository(firestore)
        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTask(TaskItem(title = "Review Linear Algebra", category = "Study")).getOrThrow()
        }

        val emittedTasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks(aliceUid).first { list -> list.any { it.id == taskId } }
        }
        assertTrue(emittedTasks.map { it.id }.contains(taskId))
    }

    @Test
    fun getTaskById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = McaRepository(firestore)
        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createTask(TaskItem(title = "Private Strategy Doc", category = "Work")).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = McaRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getTaskById(aliceUid, taskId)
        }
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? FirebaseFirestoreException
        assertNotNull(exception)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, exception?.code)
    }

    @Test
    fun observeTasks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = McaRepository(firestore)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeTasks(aliceUid).first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull("Expected exception for unauthenticated listener", caughtException)
        val firestoreException = caughtException?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caughtException", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun subscriptionAndAiQuota_recordsUsageAndEnforcesZeroTrustVerification() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = McaRepository(firestore)
        val initialSub = UserSubscription(userId = aliceUid)

        // 1. Record AI usage request and log entry
        val usageResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordAiUsageAndIncrementQuota(
                currentSub = initialSub,
                actionType = "SCHEDULE_PLAN",
                promptSummary = "Plan Friday exam study blocks"
            )
        }
        assertTrue(usageResult.isSuccess)
        val updatedSub = usageResult.getOrThrow()
        assertEquals(1L, updatedSub.aiRequestsUsedToday)

        // 2. Submit Play Billing token in PENDING_VERIFICATION state (verifiedByBackend = false)
        val pendingPurchase = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.submitPlayBillingPurchaseForVerification(
                currentSub = updatedSub,
                requestedTier = "PRO",
                billingPeriod = "MONTHLY",
                productId = "mca_pro_monthly",
                purchaseToken = "play_token_test_123"
            )
        }
        assertTrue(pendingPurchase.isSuccess)

        // 3. Regular non-admin user attempting to call adminVerifySubscriptionTier MUST be denied by Firestore rules
        val forgedVerify = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.adminVerifySubscriptionTier(
                currentSub = updatedSub,
                tier = "PRO",
                billingPeriod = "MONTHLY",
                status = "ACTIVE"
            )
        }
        assertFalse("Non-admin must not be able to self-verify PRO subscription", forgedVerify.isSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
