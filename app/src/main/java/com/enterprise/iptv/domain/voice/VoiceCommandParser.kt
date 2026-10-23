package com.enterprise.iptv.domain.voice

import javax.inject.Inject
import javax.inject.Singleton

/**
 * معالج ومحلل الأوامر الصوتية الذكي (Voice Command NLP Parser)
 * يدعم اللغتين العربية والإنجليزية ويحلل المقاصد والأوامر بدقة
 */
@Singleton
class VoiceCommandParser @Inject constructor() {

    fun parse(rawInput: String): VoiceCommand {
        val text = rawInput.trim().lowercase()
        if (text.isBlank()) return VoiceCommand.Unrecognized("")

        // 1. أوامر فضاء الأطفال
        if (text.contains("أطفال") || text.contains("اطفال") || text.contains("كرتون") || text.contains("kids")) {
            return VoiceCommand.OpenKidsMode
        }

        // 2. أوامر مؤقت النوم التلقائي
        if (text.contains("مؤقت") || text.contains("نوم") || text.contains("تايمر") || text.contains("sleep")) {
            return VoiceCommand.OpenSleepTimer
        }

        // 3. أوامر تسجيل الفيديو (DVR)
        if (text.contains("تسجيل") || text.contains("سجل") || text.contains("record")) {
            return VoiceCommand.ToggleRecording
        }

        // 4. أوامر البث للشاشات (Cast / DLNA)
        if (text.contains("بث للشاشة") || text.contains("بث للتلفزيون") || text.contains("شاشة ذكية") || text.contains("cast")) {
            return VoiceCommand.OpenCast
        }

        // 5. أوامر معالج الصوت اللحظي (عازل الصوت وموضح الكلام)
        if (text.contains("عزل الصوت") || text.contains("عازل الصوت") || text.contains("كاريوكي") || text.contains("توضيح الصوت") || text.contains("توضيح الكلام") || text.contains("vocal")) {
            return VoiceCommand.ToggleVocalDsp
        }

        // 6. أوامر الترجمة التوضيحية
        if (text.contains("ترجمة") || text.contains("ترجمه") || text.contains("كابشن") || text.contains("subtitles") || text.contains("caption")) {
            return VoiceCommand.ToggleCaptions
        }

        // 7. أوامر تكبير وملء الشاشة
        if (text.contains("ملء الشاشة") || text.contains("ملء الشاشه") || text.contains("تكبير") || text.contains("fullscreen")) {
            return VoiceCommand.ToggleFullscreen
        }

        // 7.1 أوامر جودة البث وعداد البيانات
        if (text.contains("جودة") || text.contains("استهلاك") || text.contains("عداد") || text.contains("سرعة") || text.contains("quality")) {
            return VoiceCommand.OpenQualitySettings
        }

        // 7.2 أوامر النسخ الاحتياطي واستعادة المفضلة
        if (text.contains("نسخ احتياطي") || text.contains("نسخه احتياطيه") || text.contains("استعادة") || text.contains("استرجاع") || text.contains("backup") || text.contains("restore")) {
            return VoiceCommand.OpenBackupRestore
        }

        // 8. أوامر البحث الصريحة
        val searchPrefixes = listOf("ابحث عن", "ابحث عن قناة", "بحث عن", "بحث", "دور على", "search for", "find")
        for (prefix in searchPrefixes) {
            if (text.startsWith(prefix)) {
                val query = text.removePrefix(prefix).trim()
                if (query.isNotBlank()) return VoiceCommand.Search(query)
            }
        }

        // 9. أوامر تشغيل القنوات المباشرة
        val playPrefixes = listOf("شغل قناة", "شغل لي", "شغل", "افتح قناة", "افتح لي", "افتح", "هات قناة", "هات", "play channel", "play", "open")
        for (prefix in playPrefixes) {
            if (text.startsWith(prefix)) {
                val channelQuery = cleanChannelName(text.removePrefix(prefix))
                if (channelQuery.isNotBlank()) return VoiceCommand.PlayChannel(channelQuery)
            }
        }

        // إذا قال "قناة الجزيرة" مباشرة
        if (text.startsWith("قناة ")) {
            val query = text.removePrefix("قناة ").trim()
            if (query.isNotBlank()) return VoiceCommand.PlayChannel(query)
        }

        // في حال عدم وجود كلمة أمر صريحة، نعتبرها بحثاً أو تشغيلاً إذا كانت تطابق اسماً
        val cleaned = cleanChannelName(text)
        return if (cleaned.length >= 2) {
            VoiceCommand.PlayChannel(cleaned)
        } else {
            VoiceCommand.Search(cleaned)
        }
    }

    private fun cleanChannelName(name: String): String {
        return name.replace(Regex("[.,!؟?:]"), "").trim()
    }
}
