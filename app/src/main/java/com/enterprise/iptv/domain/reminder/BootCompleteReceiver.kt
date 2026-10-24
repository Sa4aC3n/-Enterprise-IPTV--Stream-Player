package com.enterprise.iptv.domain.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * مستقبل رسمي لإعادة جدولة تذكيرات البرامج عند إعادة تشغيل الهاتف
 * محمي بصلاحية RECEIVE_BOOT_COMPLETED الرسمية من نظام أندرويد
 */
class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val reminderManager = EpgReminderManager(context)
            reminderManager.rescheduleAllPendingReminders()
        }
    }
}
