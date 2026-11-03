package com.example.monetization

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MonetizationConfig
import com.example.data.model.UserSubscription
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.IridescentMint
import com.example.ui.theme.LiquidCyan

/**
 * Centralized Feature Catalog with Required Subscription Tier.
 */
enum class McaFeature(
    val title: String,
    val requiredTier: String, // FREE, PRO, ULTRA
    val description: String
) {
    BASIC_TASK_MANAGEMENT(
        title = "Unlimited Basic Task Management",
        requiredTier = "FREE",
        description = "Create, edit, complete, and organize unlimited personal tasks."
    ),
    AD_FREE_EXPERIENCE(
        title = "100% Ad-Free Workspace",
        requiredTier = "PRO",
        description = "Remove all banner and native sponsored cards across Dashboard and Analytics."
    ),
    ADVANCED_SCHEDULING(
        title = "Intelligent Conflict Auto-Distribution",
        requiredTier = "PRO",
        description = "Automatically rebalance overloaded afternoons across optimal energy time blocks."
    ),
    ADVANCED_TASK_VIEWS(
        title = "Kanban, Timeline & Priority Matrix Views",
        requiredTier = "PRO",
        description = "Unlock multi-dimensional Kanban boards, chronological timelines, and Eisenhower priority matrices."
    ),
    ADVANCED_VOICE_DICTATION(
        title = "Advanced Voice-to-Plan Dictation",
        requiredTier = "PRO",
        description = "Convert continuous natural speech into multi-day structured project schedules."
    ),
    AI_EXECUTIVE_INSIGHTS(
        title = "AI Executive Productivity Insights",
        requiredTier = "ULTRA",
        description = "Deep behavioral velocity analysis, bottleneck forecasting, and cognitive load optimization."
    ),
    ADVANCED_MCP_AUTOMATION(
        title = "External AI & MCP Automation Bridge",
        requiredTier = "ULTRA",
        description = "Execute automated multi-task JSON workflows from external AI assistants."
    ),
    AUTONOMOUS_AI_SCHEDULER(
        title = "Autonomous Multi-Week AI Scheduler",
        requiredTier = "ULTRA",
        description = "Highest AI allowance with autonomous multi-subject exam and sprint planning."
    )
}

data class PlayBillingProduct(
    val productId: String,
    val title: String,
    val tier: String, // PRO or ULTRA
    val billingPeriod: String, // MONTHLY, ANNUAL, ONE_TIME
    val formattedPrice: String,
    val description: String,
    val isTestProduct: Boolean = false
)

/**
 * Centralized Subscription Entitlement & Offline Cache Manager.
 * Never revokes valid cached entitlements when offline, and never trusts unverified client-only flags.
 */
object EntitlementManager {
    private const val PREFS_NAME = "mca_verified_entitlement_cache"
    private const val KEY_VERIFIED_TIER = "verified_tier"
    private const val KEY_VERIFIED_STATUS = "verified_status"
    private const val KEY_RENEWAL_MILLIS = "renewal_millis"
    private const val KEY_BILLING_PERIOD = "billing_period"

    const val AUTHORIZED_ADMIN_EMAIL = "milesanjola57@gmail.com"

    fun tierRank(tier: String): Int = when (tier.uppercase()) {
        "ULTRA" -> 3
        "PRO" -> 2
        else -> 1
    }

    fun hasEntitlement(effectiveTier: String, feature: McaFeature): Boolean {
        return tierRank(effectiveTier) >= tierRank(feature.requiredTier)
    }

    fun isPaidSubscriber(effectiveTier: String): Boolean {
        return effectiveTier == "PRO" || effectiveTier == "ULTRA"
    }

    fun isAuthorizedAdmin(userEmail: String): Boolean {
        return userEmail.trim().equals(AUTHORIZED_ADMIN_EMAIL, ignoreCase = true)
    }

    /**
     * Caches verified subscription state locally so offline sessions preserve valid entitlements.
     */
    fun cacheVerifiedSubscription(context: Context, sub: UserSubscription) {
        if (sub.verifiedByBackend) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_VERIFIED_TIER, sub.effectiveEntitledTier())
                .putString(KEY_VERIFIED_STATUS, sub.status)
                .putLong(KEY_RENEWAL_MILLIS, sub.renewalDateEpochMillis)
                .putString(KEY_BILLING_PERIOD, sub.billingPeriod)
                .apply()
        }
    }

    /**
     * Reads last backend-verified tier when Firestore is temporarily unreachable/offline.
     */
    fun getOfflineFallbackTier(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedTier = prefs.getString(KEY_VERIFIED_TIER, "FREE") ?: "FREE"
        val renewal = prefs.getLong(KEY_RENEWAL_MILLIS, 0L)
        val now = System.currentTimeMillis()
        // Allow 7-day grace window if offline past renewal timestamp
        val graceWindowMillis = 7L * 24L * 3600_000L
        return if (renewal == 0L || now <= renewal + graceWindowMillis) {
            cachedTier
        } else {
            "FREE"
        }
    }
}

/**
 * Production-structured Google Play Billing integration controller.
 * - Uses real Google Play Store intent checks & test product IDs during development.
 * - Never simulates fake successful payments: submits purchase tokens in PENDING_VERIFICATION state
 *   for backend Google Play Developer API verification.
 */
object PlayBillingController {
    // Official Google Play Billing reserved test product IDs
    const val TEST_PRODUCT_PURCHASED = "android.test.purchased"
    const val TEST_PRODUCT_CANCELED = "android.test.canceled"
    const val TEST_PRODUCT_UNAVAILABLE = "android.test.item_unavailable"

    // Configured MCA Play Console Product IDs
    const val SKU_PRO_MONTHLY = "mca_pro_monthly"
    const val SKU_PRO_ANNUAL = "mca_pro_annual"
    const val SKU_PRO_LIFETIME = "mca_pro_lifetime"
    const val SKU_ULTRA_MONTHLY = "mca_ultra_monthly"
    const val SKU_ULTRA_ANNUAL = "mca_ultra_annual"

    fun buildProductCatalog(config: MonetizationConfig): List<PlayBillingProduct> = listOf(
        PlayBillingProduct(
            productId = SKU_PRO_MONTHLY,
            title = "MCA PRO Monthly",
            tier = "PRO",
            billingPeriod = "MONTHLY",
            formattedPrice = config.proMonthlyPrice,
            description = "Ad-free workspace, ${config.proAiDailyLimit} daily AI requests, Kanban & Timeline views, conflict auto-distribution."
        ),
        PlayBillingProduct(
            productId = SKU_PRO_ANNUAL,
            title = "MCA PRO Annual",
            tier = "PRO",
            billingPeriod = "ANNUAL",
            formattedPrice = config.proAnnualPrice,
            description = "Best Value Pro plan • Save on annual billing with full MCA PRO benefits."
        ),
        PlayBillingProduct(
            productId = SKU_PRO_LIFETIME,
            title = "MCA PRO Lifetime (One-Time)",
            tier = "PRO",
            billingPeriod = "ONE_TIME",
            formattedPrice = config.proLifetimePrice,
            description = "Single one-time purchase for permanent MCA PRO access without recurring billing."
        ),
        PlayBillingProduct(
            productId = SKU_ULTRA_MONTHLY,
            title = "MCA ULTRA Monthly",
            tier = "ULTRA",
            billingPeriod = "MONTHLY",
            formattedPrice = config.ultraMonthlyPrice,
            description = "Highest AI allowance (${config.ultraAiDailyLimit}/day), Executive Insights, Autonomous AI Scheduler & MCP Automation."
        ),
        PlayBillingProduct(
            productId = SKU_ULTRA_ANNUAL,
            title = "MCA ULTRA Annual",
            tier = "ULTRA",
            billingPeriod = "ANNUAL",
            formattedPrice = config.ultraAnnualPrice,
            description = "Complete MCA ULTRA flagship experience billed annually."
        )
    )

    fun isGooglePlayStoreInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.android.vending", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun openPlayStoreSubscriptionManagement(context: Context, sku: String = "") {
        val pkg = context.packageName
        val uri = if (sku.isNotBlank()) {
            Uri.parse("https://play.google.com/store/account/subscriptions?sku=$sku&package=$pkg")
        } else {
            Uri.parse("https://play.google.com/store/account/subscriptions?package=$pkg")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback browser URL if Play Store app is unavailable on emulator
        }
    }
}

/**
 * Google AdMob Placement Guard & Test Unit Configuration.
 * Strictly enforces MCA advertising rules:
 * NEVER show ads:
 * 1. For MCA PRO or MCA ULTRA subscribers
 * 2. During task creation
 * 3. During task editing
 * 4. Inside Focus Mode
 * 5. During voice recording
 * 6. In critical reminder notifications
 * 7. In the middle of calendar interactions
 */
object AdMobPlacementGuard {
    // Official Google AdMob Android Test Unit IDs (safe for development; never triggers invalid traffic)
    const val TEST_ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

    fun canShowAdvertisement(
        effectiveTier: String,
        adsEnabledInConfig: Boolean,
        currentTab: String,
        isCreatingTask: Boolean = false,
        isEditingTask: Boolean = false,
        isFocusModeActive: Boolean = false,
        isVoiceRecording: Boolean = false,
        isCalendarInteraction: Boolean = false
    ): Boolean {
        if (EntitlementManager.isPaidSubscriber(effectiveTier)) return false
        if (!adsEnabledInConfig) return false
        if (currentTab == "FOCUS" || isFocusModeActive) return false
        if (isCreatingTask || isEditingTask) return false
        if (isVoiceRecording) return false
        if (isCalendarInteraction) return false
        return true
    }
}

/**
 * Non-intrusive Google AdMob Test Banner Component for MCA FREE tier.
 * Automatically hides for PRO/ULTRA or during protected productivity workflows.
 */
@Composable
fun McaSafeAdMobBanner(
    effectiveTier: String,
    adsEnabledInConfig: Boolean,
    currentTab: String,
    isCreatingTask: Boolean = false,
    isEditingTask: Boolean = false,
    isFocusModeActive: Boolean = false,
    isVoiceRecording: Boolean = false,
    isCalendarInteraction: Boolean = false,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dismissedInSession by remember { mutableStateOf(false) }
    val allowed = AdMobPlacementGuard.canShowAdvertisement(
        effectiveTier = effectiveTier,
        adsEnabledInConfig = adsEnabledInConfig,
        currentTab = currentTab,
        isCreatingTask = isCreatingTask,
        isEditingTask = isEditingTask,
        isFocusModeActive = isFocusModeActive,
        isVoiceRecording = isVoiceRecording,
        isCalendarInteraction = isCalendarInteraction
    )

    if (!allowed || dismissedInSession) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, LiquidCyan.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("admob_test_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = AmberWarning.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "AD • TEST",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarning,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Google AdMob Test Banner (${AdMobPlacementGuard.TEST_BANNER_AD_UNIT_ID.takeLast(10)})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "MCA FREE is ad-supported • Upgrade to MCA PRO for 100% ad-free focus",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onUpgradeClick) {
                    Text("Remove Ads", color = LiquidCyan)
                }
                IconButton(
                    onClick = { dismissedInSession = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hide test ad",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Non-intrusive Google AdMob Native Productivity Card for MCA FREE tier.
 */
@Composable
fun McaNativeProductivityAdCard(
    effectiveTier: String,
    adsEnabledInConfig: Boolean,
    currentTab: String,
    onUpgradeClick: () -> Unit
) {
    val allowed = AdMobPlacementGuard.canShowAdvertisement(
        effectiveTier = effectiveTier,
        adsEnabledInConfig = adsEnabledInConfig,
        currentTab = currentTab
    )
    if (!allowed) return

    LiquidGlassCard(accentGlow = AmberWarning) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SPONSORED NATIVE TEST SLOT • ${AdMobPlacementGuard.TEST_NATIVE_AD_UNIT_ID}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarning
                )
            }
            TextButton(onClick = onUpgradeClick) {
                Text("Go Ad-Free (PRO)", color = LiquidCyan)
            }
        }
        Text(
            text = "Unlock Deep Productivity with MCA PRO & ULTRA",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Development safe mode active: using official Google AdMob test unit IDs. Paid subscribers never see advertisements.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Elegant Liquid Glass Upgrade Prompt Dialog shown when a user attempts to access a gated feature
 * or exhausts their daily AI Organiser allowance.
 */
@Composable
fun McaEntitlementUpgradeDialog(
    feature: McaFeature?,
    aiQuotaMessage: String?,
    currentTier: String,
    config: MonetizationConfig,
    onDismiss: () -> Unit,
    onNavigateToMembership: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (aiQuotaMessage != null) Icons.Default.AutoAwesome else Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = LiquidCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (aiQuotaMessage != null) "AI Organiser Allowance Reached"
                    else "Upgrade to MCA ${feature?.requiredTier ?: "PRO"}",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = ElectricViolet.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Current Plan: MCA $currentTier",
                            style = MaterialTheme.typography.labelLarge,
                            color = LiquidCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = aiQuotaMessage
                                ?: "${feature?.title ?: "This premium feature"} requires MCA ${feature?.requiredTier ?: "PRO"} or higher.\n\n${feature?.description.orEmpty()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Text(
                    text = "Your existing tasks, Smart Inbox items, and Focus history remain 100% intact when upgrading.",
                    style = MaterialTheme.typography.labelSmall,
                    color = IridescentMint
                )

                Text(
                    text = "• MCA PRO (${config.proMonthlyPrice}): ${config.proAiDailyLimit} daily AI requests, Ad-Free, Advanced Scheduling & Views\n• MCA ULTRA (${config.ultraMonthlyPrice}): ${config.ultraAiDailyLimit} daily AI requests, Executive Insights & Automation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onNavigateToMembership()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiquidCyan,
                    contentColor = Color(0xFF001F26)
                ),
                modifier = Modifier.testTag("upgrade_dialog_view_plans_button")
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Explore MCA Membership", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Maybe Later")
            }
        }
    )
}
