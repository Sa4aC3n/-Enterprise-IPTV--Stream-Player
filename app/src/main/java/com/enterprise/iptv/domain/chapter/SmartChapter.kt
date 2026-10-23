package com.enterprise.iptv.domain.chapter

/**
 * يمثل فصلاً ذكياً واحداً داخل تسجيل الفيديو
 * يحتوي على التوقيت والاسم والمدة الزمنية
 */
data class SmartChapter(
    val index: Int,            // رقم الفصل (1، 2، 3...)
    val title: String,         // عنوان الفصل المعبر (مثل: "الفصل 1: البداية والمقدمة")
    val startMs: Long,         // توقيت بداية الفصل بالمللي ثانية
    val endMs: Long            // توقيت نهاية الفصل بالمللي ثانية
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)

    val formattedTimeRange: String
        get() = "${formatTime(startMs)} - ${formatTime(endMs)}"

    val formattedDuration: String
        get() = formatTime(durationMs)

    companion object {
        fun formatTime(ms: Long): String {
            val totalSeconds = (ms / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%02d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }
    }
}
