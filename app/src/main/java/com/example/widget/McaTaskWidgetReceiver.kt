package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.integration.DeviceIntegrationHelper

class McaTaskWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val quickTaskIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(DeviceIntegrationHelper.EXTRA_QUICK_ACTION, DeviceIntegrationHelper.ACTION_QUICK_TASK)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val quickPending = PendingIntent.getActivity(
                context,
                101,
                quickTaskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val aiIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(DeviceIntegrationHelper.EXTRA_QUICK_ACTION, DeviceIntegrationHelper.ACTION_AI_ORGANISER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val aiPending = PendingIntent.getActivity(
                context,
                102,
                aiIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.mca_widget_layout).apply {
                setOnClickPendingIntent(R.id.widget_btn_new_task, quickPending)
                setOnClickPendingIntent(R.id.widget_btn_ai, aiPending)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
