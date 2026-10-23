package com.enterprise.iptv.domain.reminder

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * نموذج بيانات تذكير بموعد مباراة أو برنامج (EPG Reminder)
 */
data class EpgReminder(
    val programId: String,
    val channelId: String,
    val channelName: String,
    val streamUrl: String,
    val programTitle: String,
    val startEpochMs: Long,
    val scheduledTimeMs: Long
) {
    val formattedStartTime: String
        get() = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(startEpochMs))

    val isPending: Boolean
        get() = startEpochMs > System.currentTimeMillis()

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("programId", programId)
        obj.put("channelId", channelId)
        obj.put("channelName", channelName)
        obj.put("streamUrl", streamUrl)
        obj.put("programTitle", programTitle)
        obj.put("startEpochMs", startEpochMs)
        obj.put("scheduledTimeMs", scheduledTimeMs)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): EpgReminder = EpgReminder(
            programId = obj.getString("programId"),
            channelId = obj.getString("channelId"),
            channelName = obj.getString("channelName"),
            streamUrl = obj.getString("streamUrl"),
            programTitle = obj.getString("programTitle"),
            startEpochMs = obj.getLong("startEpochMs"),
            scheduledTimeMs = obj.getLong("scheduledTimeMs")
        )
    }
}
