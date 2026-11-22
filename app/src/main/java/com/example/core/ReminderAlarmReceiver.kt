package com.example.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives scheduled AlarmManager reminder triggers even when the app is closed or backgrounded.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getStringExtra(NotificationReminderScheduler.EXTRA_NOTIFICATION_ID).orEmpty()
        val triggerMillis = intent.getLongExtra(NotificationReminderScheduler.EXTRA_TRIGGER_MILLIS, 0L)
        if (notifId.isBlank()) return
        NotificationReminderScheduler.handleAlarmTriggered(
            context = context.applicationContext,
            notifId = notifId,
            triggerMillis = triggerMillis
        )
    }
}
