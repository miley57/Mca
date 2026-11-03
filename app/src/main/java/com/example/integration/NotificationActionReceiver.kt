package com.example.integration

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {
            ACTION_COMPLETE_TASK -> {
                if (taskId.isNotBlank()) {
                    manager.cancel(taskId.hashCode())
                    Toast.makeText(context, "Completed: $taskTitle", Toast.LENGTH_SHORT).show()
                }
            }
            ACTION_POSTPONE_TASK -> {
                if (taskId.isNotBlank()) {
                    manager.cancel(taskId.hashCode())
                    Toast.makeText(context, "Postponed +1h: $taskTitle", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE_TASK = "com.example.action.COMPLETE_TASK"
        const val ACTION_POSTPONE_TASK = "com.example.action.POSTPONE_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
    }
}
