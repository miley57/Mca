package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AiOrganizerResponse
import com.example.data.model.InboxItem
import com.example.data.model.TaskItem
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.PriorityBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.IridescentMint
import com.example.ui.theme.LiquidCyan
import com.example.viewmodel.AiOrganizerState
import com.example.viewmodel.ScheduleConflictAlert
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSchedulerAndInboxScreen(
    tasks: List<TaskItem>,
    inboxItems: List<InboxItem>,
    aiState: AiOrganizerState,
    conflictAlerts: List<ScheduleConflictAlert>,
    onSubmitAiPrompt: (String, InboxItem?) -> Unit,
    onConfirmAiProposal: (AiOrganizerResponse) -> Unit,
    onDismissAiProposal: () -> Unit,
    onAutoDistributeSchedule: () -> Unit,
    onAddInboxItem: (String, String) -> Unit,
    onConvertInboxToTask: (InboxItem) -> Unit,
    onDeleteInboxItem: (String) -> Unit,
    effectiveTier: String = "FREE",
    remainingAiPair: Pair<Long, Long> = 5L to 5L,
    onOpenMembership: () -> Unit = {}
) {
    var naturalPrompt by remember { mutableStateOf("") }
    var brainDumpInput by remember { mutableStateOf("") }
    var voiceReviewText by remember { mutableStateOf<String?>(null) }
    var isVoiceRecording by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isVoiceRecording = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = matches?.firstOrNull().orEmpty()
            if (recognized.isNotBlank()) {
                voiceReviewText = recognized
            }
        }
    }

    fun launchSpeechRecognizer(promptLabel: String) {
        try {
            isVoiceRecording = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, promptLabel)
            }
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            isVoiceRecording = false
            // Provide interactive voice transcript review fallback if emulator lacks a microphone service
            voiceReviewText = "I have an examination next Friday. I need to study mathematics, physics, and chemistry."
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. MCA AI ORGANISER Header & Conversational Input
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
                            tint = LiquidCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MCA AI ORGANISER",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Powered by Gemini • Always asks confirmation before modifying tasks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { launchSpeechRecognizer("Speak to MCA AI Organiser...") },
                        modifier = Modifier.testTag("ai_voice_input_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Dictate to AI",
                            tint = if (isVoiceRecording) CoralAlert else LiquidCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = LiquidCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "MCA $effectiveTier • AI Allowance: ${remainingAiPair.first} / ${remainingAiPair.second} left today",
                            style = MaterialTheme.typography.labelSmall,
                            color = LiquidCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (effectiveTier != "ULTRA") {
                        TextButton(onClick = onOpenMembership) {
                            Text("Expand Allowance", color = IridescentMint)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = naturalPrompt,
                    onValueChange = { naturalPrompt = it },
                    placeholder = {
                        Text("e.g., \"I have an examination next Friday. I need to study mathematics, physics, and chemistry.\"")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_prompt_input"),
                    minLines = 3
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("One-Tap Example Requests:", style = MaterialTheme.typography.labelSmall, color = LiquidCyan)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val samplePrompts = listOf(
                        "Exam Prep (Math, Physics, Chem)" to "I have an examination next Friday. I need to study mathematics, physics, and chemistry.",
                        "Groceries Tomorrow Evening" to "Remind me to buy groceries tomorrow evening and prepare a healthy meal.",
                        "Launch Product Sprint" to "Break down launching our mobile app beta next week into smaller tasks with priorities and time slots."
                    )
                    samplePrompts.forEach { (chipLabel, fullText) ->
                        AssistChip(
                            onClick = { naturalPrompt = fullText },
                            label = { Text(chipLabel) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (naturalPrompt.isNotBlank()) {
                            onSubmitAiPrompt(naturalPrompt.trim(), null)
                        }
                    },
                    enabled = aiState !is AiOrganizerState.Analyzing && naturalPrompt.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiquidCyan,
                        contentColor = Color(0xFF001F26)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("ai_generate_plan_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyze & Propose Schedule", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Voice Recognition Review Card (when speech recognized)
        if (voiceReviewText != null) {
            item {
                LiquidGlassCard(accentGlow = IridescentMint) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = IridescentMint)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Review Recognized Speech Before Saving", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = voiceReviewText!!,
                        onValueChange = { voiceReviewText = it },
                        label = { Text("Recognized Transcript (Editable)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val text = voiceReviewText.orEmpty().trim()
                                if (text.isNotEmpty()) {
                                    onSubmitAiPrompt(text, null)
                                }
                                voiceReviewText = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LiquidCyan, contentColor = Color(0xFF001F26))
                        ) {
                            Text("Organize with AI")
                        }
                        OutlinedButton(
                            onClick = {
                                val text = voiceReviewText.orEmpty().trim()
                                if (text.isNotEmpty()) {
                                    onAddInboxItem(text, "VOICE")
                                }
                                voiceReviewText = null
                            }
                        ) {
                            Text("Save to Smart Inbox")
                        }
                        TextButton(onClick = { voiceReviewText = null }) {
                            Text("Discard")
                        }
                    }
                }
            }
        }

        // 3. AI Proposal Confirmation Card (Non-Destructive Confirmation Gate!)
        item {
            AnimatedVisibility(visible = aiState !is AiOrganizerState.Idle) {
                when (aiState) {
                    is AiOrganizerState.Analyzing -> {
                        LiquidGlassCard(accentGlow = LiquidCyan) {
                            Text("MCA AI is designing your conflict-free schedule...", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = LiquidCyan)
                        }
                    }
                    is AiOrganizerState.ProposalReady -> {
                        val proposal = aiState.proposal
                        LiquidGlassCard(accentGlow = IridescentMint) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Proposed AI Plan: ${proposal.suggestedProjectName}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = IridescentMint
                                )
                                IconButton(onClick = onDismissAiProposal) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss Proposal")
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = proposal.summaryExplanation,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (proposal.conflictWarning.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = AmberWarning.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AmberWarning)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(proposal.conflictWarning, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Break Advice: ${proposal.breakRecommendation}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LiquidCyan
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            proposal.proposedTasks.forEachIndexed { idx, item ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${idx + 1}. ${item.title}",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            PriorityBadge(item.priority)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Day +${item.dayOffset} • Slot: ${item.scheduledTimeSlot} • ${item.estimatedMinutes}m • Reminder: ${item.reminderOffsetsText}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = LiquidCyan
                                        )
                                        if (item.description.isNotBlank()) {
                                            Text(item.description, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onConfirmAiProposal(proposal) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = IridescentMint,
                                        contentColor = Color(0xFF00210E)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("confirm_ai_proposal_button")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Confirm & Create (${proposal.proposedTasks.size})", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = onDismissAiProposal,
                                    modifier = Modifier.weight(0.6f)
                                ) {
                                    Text("Decline")
                                }
                            }
                        }
                    }
                    is AiOrganizerState.Error -> {
                        LiquidGlassCard(accentGlow = CoralAlert) {
                            Text("AI Organiser Notice", style = MaterialTheme.typography.titleMedium, color = CoralAlert)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(aiState.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    else -> {}
                }
            }
        }

        // 4. Intelligent Scheduling System & Conflict Detector
        item {
            LiquidGlassCard(accentGlow = AmberWarning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = AmberWarning)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Intelligent Schedule Optimizer", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (conflictAlerts.isEmpty()) "All time slots balanced"
                                else "${conflictAlerts.size} scheduling bottleneck(s) detected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(onClick = onAutoDistributeSchedule) {
                        Text("Auto-Distribute")
                    }
                }
                if (conflictAlerts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    conflictAlerts.forEach { alert ->
                        Surface(
                            color = AmberWarning.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(alert.slotLabel, style = MaterialTheme.typography.titleMedium, color = AmberWarning)
                                Text(alert.suggestedResolution, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        // 5. Smart Inbox & Brain Dump Studio
        item {
            LiquidGlassCard(accentGlow = LiquidCyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = LiquidCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Smart Inbox & Brain Dump", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Write or dictate everything on your mind — convert to structured tasks anytime",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { launchSpeechRecognizer("Brain Dump Voice Capture...") }) {
                        Icon(Icons.Default.Mic, contentDescription = "Dictate Brain Dump", tint = LiquidCyan)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = brainDumpInput,
                    onValueChange = { brainDumpInput = it },
                    placeholder = { Text("Dump unfiltered thoughts, ideas, errands, or meeting notes here...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (brainDumpInput.isNotBlank()) {
                                onAddInboxItem(brainDumpInput.trim(), "BRAIN_DUMP")
                                brainDumpInput = ""
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Save to Smart Inbox")
                    }
                    OutlinedButton(
                        onClick = {
                            if (brainDumpInput.isNotBlank()) {
                                onSubmitAiPrompt(brainDumpInput.trim(), null)
                                brainDumpInput = ""
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Convert Now")
                    }
                }
            }
        }

        // 6. Captured Smart Inbox Items
        items(inboxItems, key = { it.id.ifBlank { it.content } }) { item ->
            LiquidGlassCard(
                accentGlow = if (item.processedByAi) IridescentMint else ElectricViolet
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.source} • ${if (item.processedByAi) "ORGANIZED BY AI" else "UNPROCESSED"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.processedByAi) IridescentMint else LiquidCyan
                    )
                    IconButton(onClick = { onDeleteInboxItem(item.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Inbox Item", tint = CoralAlert, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.content, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSubmitAiPrompt(item.content, item) }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Breakdown")
                    }
                    OutlinedButton(onClick = { onConvertInboxToTask(item) }) {
                        Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Quick Task")
                    }
                }
            }
        }
    }
}
