package com.example.integration

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.DeviceCalendarEvent
import com.example.data.model.TaskItem
import java.util.TimeZone

object DeviceIntegrationHelper {
    const val CHANNEL_ID_REMINDERS = "mca_task_reminders"
    const val CHANNEL_ID_SUMMARIES = "mca_daily_summaries"

    const val EXTRA_QUICK_ACTION = "extra_quick_action"
    const val ACTION_QUICK_TASK = "quick_task"
    const val ACTION_VOICE_CAPTURE = "voice_capture"
    const val ACTION_AI_ORGANISER = "ai_organiser"

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val reminderChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Task & Location Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Actionable reminders for deadlines, recurring tasks, and location triggers"
            }
            val summaryChannel = NotificationChannel(
                CHANNEL_ID_SUMMARIES,
                "Daily Briefings & Productivity Reports",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily briefings and weekly MCA productivity insights"
            }
            manager.createNotificationChannel(reminderChannel)
            manager.createNotificationChannel(summaryChannel)
        }
    }

    fun sendActionableTaskNotification(context: Context, task: TaskItem, reason: String = "Upcoming Deadline") {
        ensureNotificationChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("taskId", task.id)
        }
        val openPending = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE_TASK
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, task.id)
            putExtra(NotificationActionReceiver.EXTRA_TASK_TITLE, task.title)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            task.id.hashCode() + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val postponeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_POSTPONE_TASK
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, task.id)
            putExtra(NotificationActionReceiver.EXTRA_TASK_TITLE, task.title)
        }
        val postponePending = PendingIntent.getBroadcast(
            context,
            task.id.hashCode() + 2,
            postponeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MCA Reminder • $reason")
            .setContentText("${task.title} (${task.priority} Priority • ${task.estimatedMinutes}m)")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append(task.title)
                        if (task.description.isNotBlank()) append("\n${task.description}")
                        if (task.scheduledTimeSlot.isNotBlank()) append("\nSlot: ${task.scheduledTimeSlot}")
                        if (task.locationLabel.isNotBlank()) append("\nLocation: ${task.locationLabel} (${task.locationTrigger})")
                    }
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "Complete", completePending)
            .addAction(android.R.drawable.ic_menu_recent_history, "Postpone +1h", postponePending)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(task.id.hashCode(), notification)
    }

    fun registerDynamicShortcuts(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

            val quickTaskShortcut = ShortcutInfo.Builder(context, "shortcut_quick_task")
                .setShortLabel("New Task")
                .setLongLabel("Quick Create MCA Task")
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(Intent.ACTION_VIEW, null, context, MainActivity::class.java).apply {
                        putExtra(EXTRA_QUICK_ACTION, ACTION_QUICK_TASK)
                    }
                )
                .build()

            val voiceCaptureShortcut = ShortcutInfo.Builder(context, "shortcut_voice_capture")
                .setShortLabel("Voice Task")
                .setLongLabel("Dictate Task with Voice")
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(Intent.ACTION_VIEW, null, context, MainActivity::class.java).apply {
                        putExtra(EXTRA_QUICK_ACTION, ACTION_VOICE_CAPTURE)
                    }
                )
                .build()

            val aiOrganiserShortcut = ShortcutInfo.Builder(context, "shortcut_ai_organiser")
                .setShortLabel("MCA AI")
                .setLongLabel("Ask MCA AI Organiser")
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(Intent.ACTION_VIEW, null, context, MainActivity::class.java).apply {
                        putExtra(EXTRA_QUICK_ACTION, ACTION_AI_ORGANISER)
                    }
                )
                .build()

            shortcutManager.dynamicShortcuts = listOf(quickTaskShortcut, voiceCaptureShortcut, aiOrganiserShortcut)
        }
    }

    fun readUpcomingCalendarEvents(context: Context, maxEvents: Int = 15): List<DeviceCalendarEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val events = mutableListOf<DeviceCalendarEvent>()
        val now = System.currentTimeMillis()
        val nextWeek = now + 7L * 24L * 3600L * 1000L
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(now.toString(), nextWeek.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)

                while (cursor.moveToNext() && events.size < maxEvents) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else "Calendar Event"
                    val start = if (startIdx >= 0) cursor.getLong(startIdx) else now
                    val end = if (endIdx >= 0) cursor.getLong(endIdx) else (start + 3600_000L)
                    val location = if (locIdx >= 0) cursor.getString(locIdx).orEmpty() else ""
                    events.add(
                        DeviceCalendarEvent(
                            id = id,
                            title = title.ifBlank { "Untitled Event" },
                            startMillis = start,
                            endMillis = end,
                            location = location
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return events
    }

    fun exportTaskToDeviceCalendar(context: Context, task: TaskItem): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            var calendarId: Long? = null
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    calendarId = cursor.getLong(0)
                }
            }
            val targetCalId = calendarId ?: return false
            val startMillis = if (task.dueDateEpochMillis > 0L) task.dueDateEpochMillis else System.currentTimeMillis() + 3600_000L
            val endMillis = startMillis + (task.estimatedMinutes.coerceAtLeast(15L) * 60_000L)

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, "[MCA] ${task.title}")
                put(CalendarContract.Events.DESCRIPTION, task.description.ifBlank { task.notes })
                put(CalendarContract.Events.CALENDAR_ID, targetCalId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                if (task.locationLabel.isNotBlank()) {
                    put(CalendarContract.Events.EVENT_LOCATION, task.locationLabel)
                }
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri != null
        } catch (_: Exception) {
            false
        }
    }
}
