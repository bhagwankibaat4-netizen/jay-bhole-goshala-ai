package com.example.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restores persisted active Goshala reminders cleanly after device restart, app update,
 * or TimeZone / system clock changes.
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                NotificationReminderScheduler.reschedulePersistedReminders(context.applicationContext)
            }
        }
    }
}
