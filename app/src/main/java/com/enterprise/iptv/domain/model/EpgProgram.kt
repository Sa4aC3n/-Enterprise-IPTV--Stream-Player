package com.enterprise.iptv.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EpgProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String = "",
    val startEpochMs: Long,
    val endEpochMs: Long,
    val category: String? = null
) {
    val isNowPlaying: Boolean
        get() {
            val now = System.currentTimeMillis()
            return now in startEpochMs until endEpochMs
        }

    val progressPercent: Float
        get() {
            val now = System.currentTimeMillis()
            if (now < startEpochMs) return 0f
            if (now >= endEpochMs) return 1f
            val total = (endEpochMs - startEpochMs).toFloat()
            if (total <= 0f) return 0f
            return ((now - startEpochMs) / total).coerceIn(0f, 1f)
        }

    val formattedStartTime: String
        get() = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(startEpochMs))

    val formattedEndTime: String
        get() = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(endEpochMs))

    val formattedTimeRange: String
        get() = "$formattedStartTime - $formattedEndTime"
}
