package com.enterprise.iptv.domain.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.enterprise.iptv.domain.model.Channel
import com.enterprise.iptv.domain.model.EpgProgram
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير تنبيهات وتذكيرات دليل البرامج (EPG Reminder Manager)
 * يدير جدولة وإلغاء ومتابعة تنبيهات البرامج والمباريات بدقة عبر AlarmManager ونظام أندرويد
 */
@Singleton
class EpgReminderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("epg_reminders_storage", Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private val _scheduledReminders = MutableStateFlow<Map<String, EpgReminder>>(loadReminders())
    val scheduledReminders = _scheduledReminders.asStateFlow()

    private fun loadReminders(): Map<String, EpgReminder> {
        val jsonStr = prefs.getString("reminders_json", null) ?: return emptyMap()
        val result = mutableMapOf<String, EpgReminder>()
        try {
            val array = JSONArray(jsonStr)
            val now = System.currentTimeMillis()
            for (i in 0 until array.length()) {
                val reminder = EpgReminder.fromJson(array.getJSONObject(i))
                // حفظ التذكيرات التي لم ينته موعدها بعد
                if (reminder.startEpochMs > now - TimeUnit.HOURS.toMillis(1)) {
                    result[reminder.programId] = reminder
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return result
    }

    private fun saveReminders(reminders: Map<String, EpgReminder>) {
        val array = JSONArray()
        reminders.values.forEach { array.put(it.toJson()) }
        prefs.edit().putString("reminders_json", array.toString()).apply()
        _scheduledReminders.value = reminders
    }

    /**
     * هل يوجد تذكير مجدول لهذا البرنامج؟
     */
    fun isReminderSet(programId: String): Boolean {
        return _scheduledReminders.value.containsKey(programId)
    }

    /**
     * جدولة تذكير قبل موعد البرنامج بـ (leadTimeMinutes) دقائق
     */
    fun scheduleReminder(
        channel: Channel,
        program: EpgProgram,
        leadTimeMinutes: Int = 5
    ): Boolean {
        val now = System.currentTimeMillis()
        val leadTimeMs = TimeUnit.MINUTES.toMillis(leadTimeMinutes.toLong())
        val triggerTime = (program.startEpochMs - leadTimeMs).coerceAtLeast(now + 2000)

        // إذا كان البرنامج قد انتهى بالفعل لا نجدول له
        if (program.endEpochMs <= now) return false

        val reminder = EpgReminder(
            programId = program.id,
            channelId = channel.id,
            channelName = channel.name,
            streamUrl = channel.streamUrl,
            programTitle = program.title,
            startEpochMs = program.startEpochMs,
            scheduledTimeMs = triggerTime
        )

        val pendingIntent = createPendingIntent(reminder)

        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setWindow(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            60_000L,
                            pendingIntent
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }

        val updated = _scheduledReminders.value.toMutableMap()
        updated[program.id] = reminder
        saveReminders(updated)
        return true
    }

    /**
     * إلغاء تذكير مجدول مسبقاً
     */
    fun cancelReminder(programId: String): Boolean {
        val current = _scheduledReminders.value[programId] ?: return false
        val pendingIntent = createPendingIntent(current)
        alarmManager?.cancel(pendingIntent)
        pendingIntent.cancel()

        val updated = _scheduledReminders.value.toMutableMap()
        updated.remove(programId)
        saveReminders(updated)
        return true
    }

    /**
     * التبديل بين جدولة وإلغاء التذكير
     */
    fun toggleReminder(channel: Channel, program: EpgProgram): Boolean {
        return if (isReminderSet(program.id)) {
            cancelReminder(program.id)
            false
        } else {
            scheduleReminder(channel, program)
            true
        }
    }

    /**
     * إعادة جدولة كافة التذكيرات المعلقة بعد إعادة تشغيل الهاتف (Boot Completed)
     */
    fun rescheduleAllPendingReminders() {
        val now = System.currentTimeMillis()
        val currentReminders = loadReminders()
        val validReminders = mutableMapOf<String, EpgReminder>()

        currentReminders.values.forEach { reminder ->
            if (reminder.startEpochMs > now) {
                val pendingIntent = createPendingIntent(reminder)
                try {
                    val trigger = reminder.scheduledTimeMs.coerceAtLeast(now + 1000)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager?.canScheduleExactAlarms() == true) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
                        } else {
                            alarmManager?.setWindow(AlarmManager.RTC_WAKEUP, trigger, 60_000L, pendingIntent)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
                    } else {
                        alarmManager?.set(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
                    }
                    validReminders[reminder.programId] = reminder
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        saveReminders(validReminders)
    }

    private fun createPendingIntent(reminder: EpgReminder): PendingIntent {
        val intent = Intent(context, EpgReminderReceiver::class.java).apply {
            action = EpgReminderReceiver.ACTION_EPG_REMINDER
            putExtra(EpgReminderReceiver.EXTRA_PROGRAM_ID, reminder.programId)
            putExtra(EpgReminderReceiver.EXTRA_PROGRAM_TITLE, reminder.programTitle)
            putExtra(EpgReminderReceiver.EXTRA_CHANNEL_NAME, reminder.channelName)
            putExtra(EpgReminderReceiver.EXTRA_STREAM_URL, reminder.streamUrl)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, reminder.programId.hashCode(), intent, flags)
    }
}
