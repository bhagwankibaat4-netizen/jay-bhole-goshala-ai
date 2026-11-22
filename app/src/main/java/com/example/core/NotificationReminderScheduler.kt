package com.example.core

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.NotificationItem
import com.example.data.model.NotificationPriority
import com.example.data.model.ReminderRecurrence
import com.example.data.model.UserSession
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ScheduledReminderRecord(
    val id: String,
    val title: String,
    val message: String,
    val category: String,
    val publishDate: String,
    val expiryDate: String,
    val reminderTime: String,
    val recurrence: String,
    val priority: String,
    val assigneeName: String,
    val targetRole: String,
    val targetModule: String
)

/**
 * Play-Policy-compliant Android Reminder Engine for Jay Bhole Gosala Samiti.
 *
 * Uses Android's built-in AlarmManager (`setAndAllowWhileIdle`, no restricted SCHEDULE_EXACT_ALARM
 * permission required), BroadcastReceivers, NotificationManagerCompat, and local SharedPreferences
 * persistence so scheduled reminders:
 * 1. Respect device TimeZone & DST for ONCE, DAILY, WEEKLY, and MONTHLY recurrences.
 * 2. Survive app closure, backgrounding, and device reboot (via ReminderBootReceiver).
 * 3. Never fire duplicate system notifications for the same occurrence (via occurrenceKey tracking).
 * 4. Deep-link directly to the specific Notification detail screen when tapped.
 */
object NotificationReminderScheduler {
    const val CHANNEL_REMINDERS = "gosala_reminders_channel"
    const val CHANNEL_NOTICES = "gosala_notices_channel"
    const val EXTRA_OPEN_ROUTE = "gosala_open_route"
    const val EXTRA_NOTIFICATION_ID = "gosala_notification_id"
    const val EXTRA_TRIGGER_MILLIS = "gosala_trigger_millis"
    const val ROUTE_NOTIFICATIONS = "notifications"

    private const val PREFS_NAME = "gosala_reminder_store"
    private const val KEY_REMINDERS_JSON = "persisted_reminders_json"
    private const val KEY_TRIGGERED_KEYS = "triggered_occurrence_keys"
    private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
    private const val KEY_ACTIVE_UID = "active_session_uid"
    private const val MAX_TRIGGERED_HISTORY = 150

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            "गौशाला रिमाइंडर अलर्ट / Goshala Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "गौशाला कार्य, टीकाकरण, चारा और गतिविधियों के निर्धारित रिमाइंडर।"
            enableVibration(true)
        }
        val noticeChannel = NotificationChannel(
            CHANNEL_NOTICES,
            "गौशाला सूचनाएँ / Goshala Notices",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "गौशाला के नए उत्पाद, गतिविधियाँ, दान अभियान और घोषणाएँ।"
        }
        nm.createNotificationChannel(reminderChannel)
        nm.createNotificationChannel(noticeChannel)
    }

    /**
     * Computes the exact next trigger epoch milliseconds in [timeZone] strictly after [nowMillis].
     * Returns null when the item is unpublished, not a reminder, invalid date/time, already passed
     * (for ONCE), or beyond [NotificationItem.expiryDate].
     */
    fun computeNextTriggerMillis(
        item: NotificationItem,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? {
        if (!item.published || !item.isReminderItem()) return null
        return computeNextTriggerMillis(
            publishDate = item.publishDate,
            expiryDate = item.expiryDate,
            reminderTime = item.reminderTime,
            recurrence = item.resolvedRecurrence(),
            nowMillis = nowMillis,
            timeZone = timeZone
        )
    }

    fun computeNextTriggerMillis(
        publishDate: String,
        expiryDate: String,
        reminderTime: String,
        recurrence: ReminderRecurrence,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? {
        val startYmd = parseIsoDate(publishDate) ?: return null
        val hm = parseTime24(reminderTime.ifBlank { "08:00" }) ?: return null
        val expiryEndMillis = if (expiryDate.isNotBlank()) {
            val expYmd = parseIsoDate(expiryDate) ?: return null
            Calendar.getInstance(timeZone).apply {
                set(expYmd.first, expYmd.second - 1, expYmd.third, 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
        } else {
            Long.MAX_VALUE
        }
        if (nowMillis > expiryEndMillis) return null

        val candidate = Calendar.getInstance(timeZone).apply {
            set(startYmd.first, startYmd.second - 1, startYmd.third, hm.first, hm.second, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (recurrence) {
            ReminderRecurrence.ONCE -> {
                val ts = candidate.timeInMillis
                return if (ts > nowMillis && ts <= expiryEndMillis) ts else null
            }
            ReminderRecurrence.DAILY -> {
                if (candidate.timeInMillis <= nowMillis) {
                    val todayCal = Calendar.getInstance(timeZone).apply {
                        timeInMillis = nowMillis
                        set(Calendar.HOUR_OF_DAY, hm.first)
                        set(Calendar.MINUTE, hm.second)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (todayCal.timeInMillis <= nowMillis) {
                        todayCal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    candidate.timeInMillis = maxOf(candidate.timeInMillis, todayCal.timeInMillis)
                }
                val ts = candidate.timeInMillis
                return if (ts > nowMillis && ts <= expiryEndMillis) ts else null
            }
            ReminderRecurrence.WEEKLY -> {
                var guard = 0
                while (candidate.timeInMillis <= nowMillis && guard < 1000) {
                    candidate.add(Calendar.WEEK_OF_YEAR, 1)
                    guard++
                }
                val ts = candidate.timeInMillis
                return if (ts > nowMillis && ts <= expiryEndMillis) ts else null
            }
            ReminderRecurrence.MONTHLY -> {
                val anchorDay = startYmd.third
                var year = startYmd.first
                var monthZero = startYmd.second - 1
                var guard = 0
                while (candidate.timeInMillis <= nowMillis && guard < 240) {
                    monthZero += 1
                    if (monthZero >= 12) {
                        monthZero = 0
                        year += 1
                    }
                    val temp = Calendar.getInstance(timeZone).apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, monthZero)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                    val clampedDay = minOf(anchorDay, temp.getActualMaximum(Calendar.DAY_OF_MONTH))
                    candidate.set(year, monthZero, clampedDay, hm.first, hm.second, 0)
                    candidate.set(Calendar.MILLISECOND, 0)
                    guard++
                }
                val ts = candidate.timeInMillis
                return if (ts > nowMillis && ts <= expiryEndMillis) ts else null
            }
        }
    }

    fun buildOccurrenceKey(
        notifId: String,
        triggerMillis: Long
    ): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd|HH:mm", Locale.US).apply {
            this.timeZone = TimeZone.getTimeZone("UTC")
        }
        return "${notifId.trim()}|${fmt.format(Date(triggerMillis))}"
    }

    fun requestCodeFor(notifId: String): Int = (notifId.hashCode() and 0x7FFFFFFF).coerceAtLeast(1)

    /**
     * Synchronizes all visible published reminders for [session] into local persistence & AlarmManager.
     * Cancels stale/removed alarms and cancels everything if [remindersEnabled] is false.
     */
    fun syncRemindersForSession(
        context: Context,
        session: UserSession,
        notifications: List<NotificationItem>,
        remindersEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        ensureNotificationChannels(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousRecords = loadPersistedReminders(context)

        prefs.edit()
            .putBoolean(KEY_REMINDERS_ENABLED, remindersEnabled)
            .putString(KEY_ACTIVE_UID, session.uid)
            .apply()

        if (!remindersEnabled) {
            previousRecords.forEach { cancelAlarm(context, it.id) }
            savePersistedReminders(context, emptyList())
            return 0
        }

        val activeRecords = notifications
            .filter { item ->
                item.id.isNotBlank() &&
                    item.published &&
                    item.isReminderItem() &&
                    item.isVisibleToSession(session) &&
                    computeNextTriggerMillis(item, nowMillis, timeZone) != null
            }
            .distinctBy { it.id }
            .map { item ->
                ScheduledReminderRecord(
                    id = item.id,
                    title = item.title,
                    message = item.message,
                    category = item.category,
                    publishDate = item.publishDate,
                    expiryDate = item.expiryDate,
                    reminderTime = item.reminderTime.ifBlank { "08:00" },
                    recurrence = item.resolvedRecurrence().name,
                    priority = item.resolvedPriority().name,
                    assigneeName = item.assigneeName,
                    targetRole = item.targetRole,
                    targetModule = item.targetModule
                )
            }

        val activeIds = activeRecords.map { it.id }.toSet()
        // Cancel any previously scheduled reminders that were deleted, unpublished, or expired
        previousRecords
            .filter { it.id !in activeIds }
            .forEach { cancelAlarm(context, it.id) }

        savePersistedReminders(context, activeRecords)

        var scheduledCount = 0
        activeRecords.forEach { record ->
            val nextTrigger = computeNextTriggerMillis(
                publishDate = record.publishDate,
                expiryDate = record.expiryDate,
                reminderTime = record.reminderTime,
                recurrence = ReminderRecurrence.fromRaw(record.recurrence),
                nowMillis = nowMillis,
                timeZone = timeZone
            )
            if (nextTrigger != null) {
                scheduleSingleAlarm(context, record.id, nextTrigger)
                scheduledCount++
            }
        }
        return scheduledCount
    }

    /**
     * Re-arms all locally persisted reminders after device reboot, app update, or timezone change.
     */
    fun reschedulePersistedReminders(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        ensureNotificationChannels(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_REMINDERS_ENABLED, true)) return 0

        val records = loadPersistedReminders(context)
        val stillValid = mutableListOf<ScheduledReminderRecord>()
        var scheduledCount = 0

        records.forEach { record ->
            val nextTrigger = computeNextTriggerMillis(
                publishDate = record.publishDate,
                expiryDate = record.expiryDate,
                reminderTime = record.reminderTime,
                recurrence = ReminderRecurrence.fromRaw(record.recurrence),
                nowMillis = nowMillis,
                timeZone = timeZone
            )
            if (nextTrigger != null) {
                stillValid += record
                scheduleSingleAlarm(context, record.id, nextTrigger)
                scheduledCount++
            } else {
                cancelAlarm(context, record.id)
            }
        }
        savePersistedReminders(context, stillValid)
        return scheduledCount
    }

    fun cancelAllScheduledReminders(context: Context) {
        val previous = loadPersistedReminders(context)
        previous.forEach { cancelAlarm(context, it.id) }
        savePersistedReminders(context, emptyList())
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACTIVE_UID)
            .apply()
    }

    /**
     * Handles an incoming AlarmManager trigger from [ReminderAlarmReceiver].
     * Guarantees anti-duplicate execution per occurrence, posts the Android system notification
     * with deep-link Intent, and schedules the next recurring occurrence automatically.
     *
     * @return true if a fresh notification was triggered; false if suppressed (disabled, missing, or duplicate).
     */
    fun handleAlarmTriggered(
        context: Context,
        notifId: String,
        triggerMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        if (notifId.isBlank()) return false
        ensureNotificationChannels(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_REMINDERS_ENABLED, true)) return false

        val record = loadPersistedReminders(context).firstOrNull { it.id == notifId } ?: return false

        // Anti-duplicate guard: never fire the exact same reminder occurrence more than once
        val effectiveTime = if (triggerMillis > 0L) triggerMillis else nowMillis
        val occKey = buildOccurrenceKey(record.id, effectiveTime)
        if (hasOccurrenceTriggered(context, occKey)) {
            return false
        }
        markOccurrenceTriggered(context, occKey)

        // Post real Android system notification with deep-link Intent to Routes.NOTIFICATIONS
        postSystemReminderNotification(context, record)

        // Schedule next occurrence for recurring reminders (DAILY, WEEKLY, MONTHLY)
        val recurrence = ReminderRecurrence.fromRaw(record.recurrence)
        if (recurrence != ReminderRecurrence.ONCE) {
            val nextBase = maxOf(nowMillis, effectiveTime) + 1_000L
            val nextTrigger = computeNextTriggerMillis(
                publishDate = record.publishDate,
                expiryDate = record.expiryDate,
                reminderTime = record.reminderTime,
                recurrence = recurrence,
                nowMillis = nextBase,
                timeZone = timeZone
            )
            if (nextTrigger != null) {
                scheduleSingleAlarm(context, record.id, nextTrigger)
            }
        }
        return true
    }

    fun buildOpenNotificationIntent(context: Context, notifId: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, ROUTE_NOTIFICATIONS)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }

    private fun postSystemReminderNotification(context: Context, record: ScheduledReminderRecord) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val launchIntent = buildOpenNotificationIntent(context, record.id)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            requestCodeFor(record.id),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = NotificationPriority.fromRaw(record.priority)
        val compatPriority = when (priority) {
            NotificationPriority.URGENT -> NotificationCompat.PRIORITY_MAX
            NotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
            NotificationPriority.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
        }
        val subtitle = buildString {
            if (record.assigneeName.isNotBlank()) {
                append("जिम्मेदार: ${record.assigneeName} • ")
            }
            append(record.message)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🔔 ${record.title}")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setPriority(compatPriority)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(requestCodeFor(record.id), notification)
    }

    private fun scheduleSingleAlarm(context: Context, notifId: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = "com.example.ACTION_GOSALA_REMINDER"
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
            putExtra(EXTRA_TRIGGER_MILLIS, triggerAtMillis)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(notifId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Play-Policy compliant: setAndAllowWhileIdle works in Doze/background without restricted SCHEDULE_EXACT_ALARM
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun cancelAlarm(context: Context, notifId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = "com.example.ACTION_GOSALA_REMINDER"
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(notifId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun loadPersistedReminders(context: Context): List<ScheduledReminderRecord> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_REMINDERS_JSON, null)
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        ScheduledReminderRecord(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            message = obj.optString("message"),
                            category = obj.optString("category"),
                            publishDate = obj.optString("publishDate"),
                            expiryDate = obj.optString("expiryDate"),
                            reminderTime = obj.optString("reminderTime"),
                            recurrence = obj.optString("recurrence"),
                            priority = obj.optString("priority"),
                            assigneeName = obj.optString("assigneeName"),
                            targetRole = obj.optString("targetRole"),
                            targetModule = obj.optString("targetModule")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun savePersistedReminders(context: Context, records: List<ScheduledReminderRecord>) {
        val arr = JSONArray()
        records.forEach { rec ->
            arr.put(
                JSONObject().apply {
                    put("id", rec.id)
                    put("title", rec.title)
                    put("message", rec.message)
                    put("category", rec.category)
                    put("publishDate", rec.publishDate)
                    put("expiryDate", rec.expiryDate)
                    put("reminderTime", rec.reminderTime)
                    put("recurrence", rec.recurrence)
                    put("priority", rec.priority)
                    put("assigneeName", rec.assigneeName)
                    put("targetRole", rec.targetRole)
                    put("targetModule", rec.targetModule)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REMINDERS_JSON, arr.toString())
            .apply()
    }

    fun hasOccurrenceTriggered(context: Context, occurrenceKey: String): Boolean {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TRIGGERED_KEYS, null)
            .orEmpty()
        if (raw.isBlank()) return false
        return raw.split('\n').contains(occurrenceKey)
    }

    private fun markOccurrenceTriggered(context: Context, occurrenceKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_TRIGGERED_KEYS, null)
            .orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
            .toMutableList()
        if (!existing.contains(occurrenceKey)) {
            existing.add(occurrenceKey)
        }
        val trimmed = if (existing.size > MAX_TRIGGERED_HISTORY) {
            existing.takeLast(MAX_TRIGGERED_HISTORY)
        } else {
            existing
        }
        prefs.edit().putString(KEY_TRIGGERED_KEYS, trimmed.joinToString("\n")).apply()
    }

    private fun parseIsoDate(raw: String): Triple<Int, Int, Int>? {
        val parts = raw.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        return Triple(y, m, d)
    }

    private fun parseTime24(raw: String): Pair<Int, Int>? {
        val parts = raw.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h to m
    }
}
