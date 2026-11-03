package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TaskItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val notes: String = "",
    val category: String = "Work",
    val project: String = "General",
    val priority: String = "NORMAL", // LOW, NORMAL, HIGH, CRITICAL
    val status: String = "TODO", // TODO, IN_PROGRESS, COMPLETED, ARCHIVED, POSTPONED
    val completed: Boolean = false,
    val archived: Boolean = false,
    val startDateEpochMillis: Long = 0L,
    val dueDateEpochMillis: Long = 0L,
    val scheduledTimeSlot: String = "",
    val estimatedMinutes: Long = 30L,
    val actualMinutesSpent: Long = 0L,
    val recurrence: String = "NONE", // NONE, DAILY, WEEKLY, MONTHLY, MONDAYS
    val reminderOffsetsText: String = "30m before",
    val locationLabel: String = "",
    val locationTrigger: String = "NONE", // NONE, ARRIVE, LEAVE
    val subtasksText: String = "",
    val tagsText: String = "",
    val attachmentUri: String = "",
    val attachmentName: String = "",
    val dependsOnTaskId: String = "",
    val dependsOnTaskTitle: String = "",
    val calendarSynced: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun priorityRank(): Int = when (priority) {
        "CRITICAL" -> 4
        "HIGH" -> 3
        "NORMAL" -> 2
        else -> 1
    }

    fun parsedSubtasks(): List<Pair<Boolean, String>> {
        if (subtasksText.isBlank()) return emptyList()
        return subtasksText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                when {
                    line.startsWith("[x]", ignoreCase = true) -> true to line.drop(3).trim()
                    line.startsWith("[ ]") -> false to line.drop(3).trim()
                    else -> false to line
                }
            }
    }

    fun parsedTags(): List<String> {
        if (tagsText.isBlank()) return emptyList()
        return tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

@IgnoreExtraProperties
data class InboxItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val content: String = "",
    val source: String = "QUICK_CAPTURE", // QUICK_CAPTURE, VOICE, BRAIN_DUMP, EXTERNAL_AI, SHARE_INTENT
    val processedByAi: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FocusSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val taskId: String = "",
    val taskTitle: String = "",
    val durationMinutes: Long = 25L,
    val sessionType: String = "POMODORO", // POMODORO, DEEP_WORK, BREAK
    val completed: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserSubscription(
    @DocumentId val id: String = "current",
    val userId: String = "",
    val tier: String = "FREE", // FREE, PRO, ULTRA
    val billingPeriod: String = "NONE", // NONE, MONTHLY, ANNUAL, ONE_TIME
    val status: String = "ACTIVE", // ACTIVE, GRACE_PERIOD, EXPIRED, CANCELLED, PENDING_VERIFICATION
    val productId: String = "mca_free_default",
    val purchaseToken: String = "",
    val verifiedByBackend: Boolean = true,
    val renewalDateEpochMillis: Long = 0L,
    val aiRequestsUsedToday: Long = 0L,
    val aiRequestsTotalCount: Long = 0L,
    val aiQuotaResetEpochMillis: Long = 0L,
    val lastAiActionLabel: String = "None",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    /**
     * Never trust unverified client-side paid tiers alone:
     * Only grant PRO or ULTRA entitlements when verifiedByBackend == true and status is ACTIVE or GRACE_PERIOD.
     */
    fun effectiveEntitledTier(): String {
        val validStatus = status == "ACTIVE" || status == "GRACE_PERIOD"
        return if (verifiedByBackend && validStatus && (tier == "PRO" || tier == "ULTRA")) {
            tier
        } else {
            "FREE"
        }
    }
}

@IgnoreExtraProperties
data class AiUsageLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actionType: String = "SCHEDULE_PLAN", // SCHEDULE_PLAN, MORNING_BRIEFING, EOD_REVIEW, INBOX_BREAKDOWN, INSIGHTS
    val promptSummary: String = "",
    val tierAtRequest: String = "FREE",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MonetizationConfig(
    @DocumentId val id: String = "monetization",
    val freeAiDailyLimit: Long = 5L,
    val proAiDailyLimit: Long = 50L,
    val ultraAiDailyLimit: Long = 500L,
    val proMonthlyPrice: String = "$6.99/mo",
    val proAnnualPrice: String = "$59.99/yr",
    val proLifetimePrice: String = "$149.99",
    val ultraMonthlyPrice: String = "$14.99/mo",
    val ultraAnnualPrice: String = "$129.99/yr",
    val freeBenefitsSummary: String = "Unlimited basic task management • Standard reminders • Basic calendar & analytics • 5 daily AI Organiser requests • Standard themes • Advertising-supported",
    val proBenefitsSummary: String = "Everything in Free • 100% Ad-Free experience • 50 daily AI Organiser requests • Advanced scheduling & conflict auto-distribution • Advanced analytics • Full dashboard customization & premium themes • Advanced voice & cloud sync",
    val ultraBenefitsSummary: String = "Everything in Pro • 500 daily AI Organiser requests • Advanced AI autonomous scheduling • AI-powered executive productivity insights • Advanced MCP automation • Priority access to upcoming AI capabilities",
    val adsEnabledForFreeTier: Boolean = true,
    val subscriptionsAvailable: Boolean = true,
    val promoBannerText: String = "Annual Founders Offer: Save up to 28% on MCA PRO & MCA ULTRA",
    val updatedByEmail: String = "system@mcataskmanager.app",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun dailyLimitForTier(tier: String): Long = when (tier) {
        "ULTRA" -> ultraAiDailyLimit
        "PRO" -> proAiDailyLimit
        else -> freeAiDailyLimit
    }
}

data class ProposedTaskPlan(
    val title: String,
    val description: String,
    val category: String,
    val project: String,
    val priority: String,
    val estimatedMinutes: Long,
    val scheduledTimeSlot: String,
    val dayOffset: Int,
    val reminderOffsetsText: String,
    val subtasksText: String
)

data class AiOrganizerResponse(
    val summaryExplanation: String,
    val conflictWarning: String,
    val suggestedProjectName: String,
    val breakRecommendation: String,
    val proposedTasks: List<ProposedTaskPlan>
)

data class DeviceCalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String
)
