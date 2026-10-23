package com.enterprise.iptv.domain.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.enterprise.iptv.MainActivity
import com.enterprise.iptv.R

/**
 * مستقبل تنبيهات وتذكيرات مواعيد البرامج والمباريات
 * يقوم بإنشاء إشعار تفاعلي عند حلول موعد البرنامج وينقل المستخدم مباشرة إلى القناة
 */
class EpgReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_EPG_REMINDER -> {
                val programId = intent.getStringExtra(EXTRA_PROGRAM_ID) ?: return
                val programTitle = intent.getStringExtra(EXTRA_PROGRAM_TITLE) ?: "برنامجك المفضل"
                val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "القناة"
                val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: ""

                showNotification(context, programId, programTitle, channelName, streamUrl)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // إعادة جدولة التذكيرات المعلقة عند إعادة تشغيل الهاتف
                val reminderManager = EpgReminderManager(context)
                reminderManager.rescheduleAllPendingReminders()
            }
        }
    }

    private fun showNotification(
        context: Context,
        programId: String,
        programTitle: String,
        channelName: String,
        streamUrl: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // إنشاء قناة الإشعارات لنظام أندرويد 8.0 فما فوق
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تذكيرات مواعيد البرامج والمباريات",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات تذكيرية قبل بدء البرامج والمباريات المجدولة"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // إنشاء PendingIntent لفتح التطبيق وتشغيل القناة مباشرة
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_STREAM_URL, streamUrl)
            putExtra(EXTRA_CHANNEL_NAME, channelName)
            putExtra(EXTRA_PROGRAM_TITLE, programTitle)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            programId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("📺 تذكير: $programTitle")
            .setContentText("يبدأ الآن على قناة $channelName • اضغط للمشاهدة فوراً")
            .setStyle(NotificationCompat.BigTextStyle().bigText("حان الآن موعد برنامج \"$programTitle\" على قناة $channelName. اضغط هنا للانتقال للبث المباشر فوراً."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(programId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "epg_program_reminders"
        const val ACTION_EPG_REMINDER = "com.enterprise.iptv.ACTION_EPG_REMINDER"

        const val EXTRA_PROGRAM_ID = "EXTRA_PROGRAM_ID"
        const val EXTRA_PROGRAM_TITLE = "EXTRA_PROGRAM_TITLE"
        const val EXTRA_CHANNEL_NAME = "EXTRA_CHANNEL_NAME"
        const val EXTRA_STREAM_URL = "EXTRA_STREAM_URL"
    }
}
