package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AiUsageLog
import com.example.data.model.MonetizationConfig
import com.example.data.model.UserSubscription
import com.example.monetization.AdMobPlacementGuard
import com.example.monetization.EntitlementManager
import com.example.monetization.McaFeature
import com.example.monetization.PlayBillingController
import com.example.monetization.PlayBillingProduct
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.IridescentMint
import com.example.ui.theme.LiquidCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun McaMembershipScreen(
    subscription: UserSubscription,
    config: MonetizationConfig,
    aiUsageLogs: List<AiUsageLog>,
    remainingAiPair: Pair<Long, Long>,
    userEmail: String,
    onSelectPlayProduct: (PlayBillingProduct, Boolean) -> Unit,
    onRestorePurchases: () -> Unit,
    onDowngradeToFree: () -> Unit,
    onAdminVerifyTier: (String, String, String) -> Unit,
    onSaveAdminConfig: (MonetizationConfig) -> Unit
) {
    val context = LocalContext.current
    val effectiveTier = subscription.effectiveEntitledTier()
    val isPlayStoreInstalled = remember { PlayBillingController.isGooglePlayStoreInstalled(context) }
    val products = remember(config) { PlayBillingController.buildProductCatalog(config) }
    val isAdmin = remember(userEmail) { EntitlementManager.isAuthorizedAdmin(userEmail) }
    var selectedBillingFilter by remember { mutableStateOf("MONTHLY") } // MONTHLY, ANNUAL, ONE_TIME
    var showUsageHistory by remember { mutableStateOf(false) }
    var showSetupDocs by remember { mutableStateOf(false) }

    val (remainingAi, dailyLimit) = remainingAiPair
    val usedToday = (dailyLimit - remainingAi).coerceAtLeast(0L)
    val usageProgress = if (dailyLimit > 0L) (usedToday.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("mca_membership_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Promotional Banner (Backend-Configurable)
        if (config.promoBannerText.isNotBlank()) {
            item {
                Surface(
                    color = ElectricViolet.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, LiquidCyan.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = LiquidCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = config.promoBannerText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 2. Current Subscription & Entitlement Overview Card
        item {
            LiquidGlassCard(
                accentGlow = when (effectiveTier) {
                    "ULTRA" -> ElectricViolet
                    "PRO" -> LiquidCyan
                    else -> IridescentMint
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = LiquidCyan,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MCA MEMBERSHIP",
                                style = MaterialTheme.typography.labelSmall,
                                color = LiquidCyan
                            )
                            Text(
                                text = "Current Plan: MCA $effectiveTier",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Surface(
                        color = when (subscription.status) {
                            "ACTIVE" -> IridescentMint.copy(alpha = 0.2f)
                            "PENDING_VERIFICATION" -> AmberWarning.copy(alpha = 0.2f)
                            else -> CoralAlert.copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = subscription.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (subscription.status) {
                                "ACTIVE" -> IridescentMint
                                "PENDING_VERIFICATION" -> AmberWarning
                                else -> CoralAlert
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val renewalText = if (subscription.renewalDateEpochMillis > 0L) {
                    dateFmt.format(Date(subscription.renewalDateEpochMillis))
                } else {
                    "No recurring charge (MCA FREE)"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MembershipStatItem("Billing Period", subscription.billingPeriod)
                    MembershipStatItem("Renewal / Expiry", renewalText)
                    MembershipStatItem(
                        "Backend Verified",
                        if (subscription.verifiedByBackend) "Verified" else "Pending RTDN"
                    )
                }

                if (subscription.status == "PENDING_VERIFICATION") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = AmberWarning.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Pending Backend Verification (${subscription.productId})",
                                style = MaterialTheme.typography.titleMedium,
                                color = AmberWarning
                            )
                            Text(
                                text = "MCA Task Manager never trusts client-side payment flags alone. Your requested ${subscription.tier} (${subscription.billingPeriod}) purchase token has been saved to Firestore and awaits Google Play Developer API backend verification.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestorePurchases,
                        modifier = Modifier.testTag("restore_purchases_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Purchases")
                    }
                    OutlinedButton(
                        onClick = {
                            PlayBillingController.openPlayStoreSubscriptionManagement(
                                context,
                                subscription.productId
                            )
                        }
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manage on Google Play")
                    }
                    if (effectiveTier != "FREE" || subscription.status == "PENDING_VERIFICATION") {
                        TextButton(onClick = onDowngradeToFree) {
                            Text("Downgrade to MCA FREE", color = CoralAlert)
                        }
                    }
                }
            }
        }

        // 3. AI Usage Management & Daily Allowance Meter
        item {
            LiquidGlassCard(accentGlow = ElectricViolet) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ElectricViolet)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("AI Organiser Usage & Allowance", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Tier: MCA $effectiveTier • Daily Limit: $dailyLimit requests/day",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = { showUsageHistory = !showUsageHistory }) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showUsageHistory) "Hide Log" else "History (${aiUsageLogs.size})")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { usageProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = if (remainingAi == 0L) CoralAlert else LiquidCyan
                )
                Spacer(modifier = Modifier.height(8.dp))

                val resetFormatted = if (subscription.aiQuotaResetEpochMillis > 0L) {
                    dateFmt.format(Date(subscription.aiQuotaResetEpochMillis))
                } else {
                    "Rolling 24h window"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Used Today: $usedToday / $dailyLimit",
                        style = MaterialTheme.typography.labelLarge,
                        color = LiquidCyan
                    )
                    Text(
                        text = "Remaining: $remainingAi",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (remainingAi > 0L) IridescentMint else CoralAlert
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Next Allowance Reset: $resetFormatted • Lifetime Requests: ${subscription.aiRequestsTotalCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedVisibility(visible = showUsageHistory) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Recent AI Organiser Requests", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (aiUsageLogs.isEmpty()) {
                            Text(
                                text = "No AI requests logged yet. Generate a schedule or daily briefing to see usage history.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            aiUsageLogs.take(8).forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${log.actionType}: ${log.promptSummary}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = log.tierAtRequest,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LiquidCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Subscription Tiers Comparison (MCA FREE, MCA PRO, MCA ULTRA)
        item {
            LiquidGlassCard(accentGlow = LiquidCyan) {
                Text(
                    text = "Available MCA Membership Plans",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select your preferred billing cadence. Upgrade or downgrade anytime without losing data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "MONTHLY" to "Monthly Billing",
                        "ANNUAL" to "Annual (Save 28%)",
                        "ONE_TIME" to "One-Time Lifetime"
                    ).forEach { (cycleKey, label) ->
                        FilterChip(
                            selected = selectedBillingFilter == cycleKey,
                            onClick = { selectedBillingFilter = cycleKey },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // Tier 1: MCA FREE Card
        item {
            TierPlanCard(
                tierTitle = "MCA FREE",
                priceLabel = "$0 • Forever",
                billingSubtitle = "Standard Advertising-Supported Experience",
                aiAllowanceText = "${config.freeAiDailyLimit} AI Organiser requests / day",
                benefitsSummary = config.freeBenefitsSummary,
                bulletPoints = listOf(
                    "Unlimited basic task management & subtasks",
                    "Standard reminders & basic calendar view",
                    "Basic dashboard customization & productivity analytics",
                    "Limited AI Organiser usage (${config.freeAiDailyLimit}/day)",
                    "Standard themes • Non-intrusive ad-supported experience"
                ),
                accentColor = IridescentMint,
                isCurrentTier = effectiveTier == "FREE",
                actionButtonLabel = if (effectiveTier == "FREE") "Current Active Plan" else "Downgrade to MCA FREE",
                onActionClick = {
                    if (effectiveTier != "FREE") onDowngradeToFree()
                }
            )
        }

        // Tier 2: MCA PRO Card
        item {
            val proProduct = products.firstOrNull {
                it.tier == "PRO" && it.billingPeriod == selectedBillingFilter
            } ?: products.first { it.tier == "PRO" }

            TierPlanCard(
                tierTitle = "MCA PRO",
                priceLabel = proProduct.formattedPrice,
                billingSubtitle = "Billing Cycle: ${proProduct.billingPeriod} • SKU: ${proProduct.productId}",
                aiAllowanceText = "${config.proAiDailyLimit} AI Organiser requests / day",
                benefitsSummary = config.proBenefitsSummary,
                bulletPoints = listOf(
                    "Everything in MCA FREE + 100% Ad-Free Experience",
                    "Expanded AI Organiser allowance (${config.proAiDailyLimit}/day)",
                    "Advanced scheduling & conflict auto-distribution",
                    "Kanban Board, Timeline & Priority Matrix views",
                    "Full dashboard customization, premium themes & advanced voice",
                    "Multi-device real-time cloud synchronization"
                ),
                accentColor = LiquidCyan,
                isCurrentTier = effectiveTier == "PRO",
                actionButtonLabel = when {
                    effectiveTier == "PRO" -> "Current Active Plan (${subscription.billingPeriod})"
                    effectiveTier == "ULTRA" -> "Downgrade to ${proProduct.title}"
                    else -> "Upgrade to ${proProduct.title} (${proProduct.formattedPrice})"
                },
                onActionClick = {
                    onSelectPlayProduct(proProduct, isPlayStoreInstalled)
                }
            )
        }

        // Tier 3: MCA ULTRA Card
        item {
            val ultraProduct = products.firstOrNull {
                it.tier == "ULTRA" && (it.billingPeriod == selectedBillingFilter || selectedBillingFilter == "ONE_TIME")
            } ?: products.first { it.tier == "ULTRA" }

            TierPlanCard(
                tierTitle = "MCA ULTRA",
                priceLabel = ultraProduct.formattedPrice,
                billingSubtitle = "Billing Cycle: ${ultraProduct.billingPeriod} • SKU: ${ultraProduct.productId}",
                aiAllowanceText = "${config.ultraAiDailyLimit} AI Organiser requests / day",
                benefitsSummary = config.ultraBenefitsSummary,
                bulletPoints = listOf(
                    "Everything in MCA PRO + Highest AI Allowance (${config.ultraAiDailyLimit}/day)",
                    "Autonomous multi-week AI scheduling & conflict resolution",
                    "AI-powered executive productivity insights & velocity forecasting",
                    "External AI Assistant & MCP JSON workflow automation",
                    "Premium personalization & early access to new AI models"
                ),
                accentColor = ElectricViolet,
                isCurrentTier = effectiveTier == "ULTRA",
                actionButtonLabel = if (effectiveTier == "ULTRA") {
                    "Current Active Plan (${subscription.billingPeriod})"
                } else {
                    "Upgrade to ${ultraProduct.title} (${ultraProduct.formattedPrice})"
                },
                onActionClick = {
                    onSelectPlayProduct(ultraProduct, isPlayStoreInstalled)
                }
            )
        }

        // 5. Cancellation, Grace Period & Google Play Billing Architecture Notice
        item {
            LiquidGlassCard(accentGlow = AmberWarning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = AmberWarning)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Billing Security, Cancellation & Development Safety",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Zero-Trust Verification: Client-side purchases are submitted with status PENDING_VERIFICATION and verified against Google Play Developer API / RTDN webhooks.\n" +
                        "• Grace Periods & Offline Safety: Valid entitlements are cached locally with a 7-day offline grace window so temporary connectivity loss never revokes your Pro/Ultra features.\n" +
                        "• Easy Cancellation: Cancel or change plans anytime in Google Play Store > Subscriptions. You retain access through the end of your current billing period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onSelectPlayProduct(
                                PlayBillingProduct(
                                    productId = PlayBillingController.TEST_PRODUCT_PURCHASED,
                                    title = "Google Play Test Product (android.test.purchased)",
                                    tier = "PRO",
                                    billingPeriod = "MONTHLY",
                                    formattedPrice = "$0.00 (Test)",
                                    description = "Google Play static test response SKU",
                                    isTestProduct = true
                                ),
                                isPlayStoreInstalled
                            )
                        }
                    ) {
                        Text("Test SKU: android.test.purchased")
                    }
                    TextButton(onClick = { showSetupDocs = !showSetupDocs }) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showSetupDocs) "Hide Setup Guide" else "External Setup Guide")
                    }
                }

                AnimatedVisibility(visible = showSetupDocs) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Production Play Console & AdMob Checklist",
                                style = MaterialTheme.typography.titleMedium,
                                color = LiquidCyan
                            )
                            Text(
                                text = "1. Google Play Billing SKUs: Create subscriptions `mca_pro_monthly`, `mca_pro_annual`, `mca_ultra_monthly`, `mca_ultra_annual` and in-app product `mca_pro_lifetime` in Google Play Console.\n" +
                                    "2. RTDN Backend Verification: Connect Google Cloud Pub/Sub Real-Time Developer Notifications to verify `purchaseToken` server-side and set `verifiedByBackend = true` on `/users/{uid}/subscription/current`.\n" +
                                    "3. Google AdMob Units: Development builds use official Google test IDs (`${AdMobPlacementGuard.TEST_BANNER_AD_UNIT_ID}` and `${AdMobPlacementGuard.TEST_NATIVE_AD_UNIT_ID}`). Replace with production AdMob unit IDs only before Play Store release.",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // 6. SECURE ADMIN CONFIGURATION SYSTEM (Strictly visible ONLY to verified admin)
        if (isAdmin) {
            item {
                AdminMonetizationConfigCard(
                    config = config,
                    userEmail = userEmail,
                    onSaveConfig = onSaveAdminConfig,
                    onAdminVerifyTier = onAdminVerifyTier
                )
            }
        }
    }
}

@Composable
private fun TierPlanCard(
    tierTitle: String,
    priceLabel: String,
    billingSubtitle: String,
    aiAllowanceText: String,
    benefitsSummary: String,
    bulletPoints: List<String>,
    accentColor: Color,
    isCurrentTier: Boolean,
    actionButtonLabel: String,
    onActionClick: () -> Unit
) {
    LiquidGlassCard(accentGlow = accentColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = tierTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = billingSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = priceLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = accentColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = aiAllowanceText,
                style = MaterialTheme.typography.labelLarge,
                color = accentColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = benefitsSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))
        bulletPoints.forEach { point ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = point, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onActionClick,
            enabled = !isCurrentTier,
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color(0xFF001F26)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("plan_action_${tierTitle.lowercase().replace(" ", "_")}")
        ) {
            Text(text = actionButtonLabel, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminMonetizationConfigCard(
    config: MonetizationConfig,
    userEmail: String,
    onSaveConfig: (MonetizationConfig) -> Unit,
    onAdminVerifyTier: (String, String, String) -> Unit
) {
    var freeLimitText by remember(config) { mutableStateOf(config.freeAiDailyLimit.toString()) }
    var proLimitText by remember(config) { mutableStateOf(config.proAiDailyLimit.toString()) }
    var ultraLimitText by remember(config) { mutableStateOf(config.ultraAiDailyLimit.toString()) }
    var proMonthlyPrice by remember(config) { mutableStateOf(config.proMonthlyPrice) }
    var proAnnualPrice by remember(config) { mutableStateOf(config.proAnnualPrice) }
    var proLifetimePrice by remember(config) { mutableStateOf(config.proLifetimePrice) }
    var ultraMonthlyPrice by remember(config) { mutableStateOf(config.ultraMonthlyPrice) }
    var ultraAnnualPrice by remember(config) { mutableStateOf(config.ultraAnnualPrice) }
    var promoBannerText by remember(config) { mutableStateOf(config.promoBannerText) }
    var adsEnabled by remember(config) { mutableStateOf(config.adsEnabledForFreeTier) }
    var subsAvailable by remember(config) { mutableStateOf(config.subscriptionsAvailable) }

    LiquidGlassCard(accentGlow = CoralAlert) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = CoralAlert)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "ADMIN MONETIZATION CONTROL CENTER",
                    style = MaterialTheme.typography.titleLarge,
                    color = CoralAlert,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Authorized Owner: $userEmail • Protected by Firestore isAdmin() Rules",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "1. Backend Subscription Verification Simulator (RTDN / Admin Override)",
            style = MaterialTheme.typography.titleMedium,
            color = LiquidCyan
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { onAdminVerifyTier("FREE", "NONE", "ACTIVE") }) {
                Text("Verify MCA FREE")
            }
            OutlinedButton(onClick = { onAdminVerifyTier("PRO", "MONTHLY", "ACTIVE") }) {
                Text("Verify MCA PRO (Active)")
            }
            OutlinedButton(onClick = { onAdminVerifyTier("ULTRA", "ANNUAL", "ACTIVE") }) {
                Text("Verify MCA ULTRA (Active)")
            }
            OutlinedButton(onClick = { onAdminVerifyTier("PRO", "MONTHLY", "GRACE_PERIOD") }) {
                Text("Test PRO Grace Period")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "2. Configure Tier AI Allowances & Plan Pricing (/app_config/monetization)",
            style = MaterialTheme.typography.titleMedium,
            color = LiquidCyan
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = freeLimitText,
                onValueChange = { freeLimitText = it },
                label = { Text("Free AI/Day") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = proLimitText,
                onValueChange = { proLimitText = it },
                label = { Text("Pro AI/Day") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = ultraLimitText,
                onValueChange = { ultraLimitText = it },
                label = { Text("Ultra AI/Day") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = proMonthlyPrice,
                onValueChange = { proMonthlyPrice = it },
                label = { Text("Pro Monthly") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = proAnnualPrice,
                onValueChange = { proAnnualPrice = it },
                label = { Text("Pro Annual") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = proLifetimePrice,
                onValueChange = { proLifetimePrice = it },
                label = { Text("Pro Lifetime") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ultraMonthlyPrice,
                onValueChange = { ultraMonthlyPrice = it },
                label = { Text("Ultra Monthly") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = ultraAnnualPrice,
                onValueChange = { ultraAnnualPrice = it },
                label = { Text("Ultra Annual") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = promoBannerText,
            onValueChange = { promoBannerText = it },
            label = { Text("Promotional Offer Banner Text") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable AdMob Test Ads for MCA FREE Tier", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = adsEnabled, onCheckedChange = { adsEnabled = it })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Allow New Subscription Upgrades", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = subsAvailable, onCheckedChange = { subsAvailable = it })
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                val updated = config.copy(
                    freeAiDailyLimit = freeLimitText.toLongOrNull()?.coerceIn(1L, 1000L) ?: config.freeAiDailyLimit,
                    proAiDailyLimit = proLimitText.toLongOrNull()?.coerceIn(1L, 10000L) ?: config.proAiDailyLimit,
                    ultraAiDailyLimit = ultraLimitText.toLongOrNull()?.coerceIn(1L, 100000L) ?: config.ultraAiDailyLimit,
                    proMonthlyPrice = proMonthlyPrice.trim().ifEmpty { "$6.99/mo" },
                    proAnnualPrice = proAnnualPrice.trim().ifEmpty { "$59.99/yr" },
                    proLifetimePrice = proLifetimePrice.trim().ifEmpty { "$149.99" },
                    ultraMonthlyPrice = ultraMonthlyPrice.trim().ifEmpty { "$14.99/mo" },
                    ultraAnnualPrice = ultraAnnualPrice.trim().ifEmpty { "$129.99/yr" },
                    promoBannerText = promoBannerText.trim(),
                    adsEnabledForFreeTier = adsEnabled,
                    subscriptionsAvailable = subsAvailable
                )
                onSaveConfig(updated)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = IridescentMint,
                contentColor = Color(0xFF00210E)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_admin_config_button")
        ) {
            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Publish Monetization Config to Cloud", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MembershipStatItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}
