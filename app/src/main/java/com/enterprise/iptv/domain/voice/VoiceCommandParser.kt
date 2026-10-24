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

        // 1. فحص أوامر تشغيل القنوات المباشرة والبحث الصريح أولاً لضمان عدم اعتراض أسماء القنوات كأوامر
        val playPrefixes = listOf("شغل قناة", "شغل لي", "شغل", "افتح قناة", "افتح لي", "افتح", "هات قناة", "هات", "play channel", "play", "open")
        for (prefix in playPrefixes) {
            if (text.startsWith(prefix)) {
                val channelQuery = cleanChannelName(text.removePrefix(prefix))
                if (channelQuery.isNotBlank()) return VoiceCommand.PlayChannel(channelQuery)
            }
        }

        val searchPrefixes = listOf("ابحث عن", "ابحث عن قناة", "بحث عن", "بحث", "دور على", "search for", "find")
        for (prefix in searchPrefixes) {
            if (text.startsWith(prefix)) {
                val query = text.removePrefix(prefix).trim()
                if (query.isNotBlank()) return VoiceCommand.Search(query)
            }
        }

        // إذا قال "قناة الجزيرة" أو "قناة كرتون" مباشرة
        if (text.startsWith("قناة ")) {
            val query = text.removePrefix("قناة ").trim()
            if (query.isNotBlank()) return VoiceCommand.PlayChannel(query)
        }

        // 2. أوامر فضاء الأطفال الصريحة فقط
        if (text == "أطفال" || text == "اطفال" || text.contains("وضع الأطفال") || text.contains("وضع الاطفال") || text.contains("فضاء الأطفال") || text.contains("kids mode")) {
            return VoiceCommand.OpenKidsMode
        }

        // 3. أوامر مؤقت النوم التلقائي
        if (text.contains("مؤقت") || text.contains("نوم") || text.contains("تايمر") || text.contains("sleep")) {
            return VoiceCommand.OpenSleepTimer
        }

        // 4. أوامر تسجيل الفيديو (DVR)
        if (text.contains("تسجيل") || text.contains("سجل") || text.contains("record")) {
            return VoiceCommand.ToggleRecording
        }

        // 5. أوامر البث للشاشات (Cast / DLNA)
        if (text.contains("بث للشاشة") || text.contains("بث للتلفزيون") || text.contains("شاشة ذكية") || text.contains("cast")) {
            return VoiceCommand.OpenCast
        }

        // 6. أوامر معالج الصوت اللحظي (عازل الصوت وموضح الكلام)
        if (text.contains("عزل الصوت") || text.contains("عازل الصوت") || text.contains("كاريوكي") || text.contains("توضيح الصوت") || text.contains("توضيح الكلام") || text.contains("vocal")) {
            return VoiceCommand.ToggleVocalDsp
        }

        // 7. أوامر الترجمة التوضيحية
        if (text.contains("ترجمة") || text.contains("ترجمه") || text.contains("كابشن") || text.contains("subtitles") || text.contains("caption")) {
            return VoiceCommand.ToggleCaptions
        }

        // 8. أوامر تكبير وملء الشاشة
        if (text.contains("ملء الشاشة") || text.contains("ملء الشاشه") || text.contains("تكبير") || text.contains("fullscreen")) {
            return VoiceCommand.ToggleFullscreen
        }

        // 9. أوامر جودة البث وعداد البيانات
        if (text.contains("جودة") || text.contains("استهلاك") || text.contains("عداد") || text.contains("سرعة") || text.contains("quality")) {
            return VoiceCommand.OpenQualitySettings
        }

        // 10. أوامر النسخ الاحتياطي واستعادة المفضلة
        if (text.contains("نسخ احتياطي") || text.contains("نسخه احتياطيه") || text.contains("استعادة") || text.contains("استرجاع") || text.contains("backup") || text.contains("restore")) {
            return VoiceCommand.OpenBackupRestore
        }

        // 11. أوامر دليل البرامج والتذكيرات
        if (text.contains("جدول") || text.contains("دليل") || text.contains("مواعيد") || text.contains("تذكير") || text.contains("epg") || text.contains("schedule")) {
            return VoiceCommand.OpenSchedule
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
