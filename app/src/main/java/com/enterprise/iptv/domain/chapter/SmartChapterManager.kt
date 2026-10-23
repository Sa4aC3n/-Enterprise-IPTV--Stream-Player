package com.enterprise.iptv.domain.chapter

import javax.inject.Inject
import javax.inject.Singleton

/**
 * معالج الفصول الذكي (Smart Chapter Engine)
 * يقوم بفحص مدة الفيديو وتقسيمه تلقائياً وبدقة إلى فصول متوازنة ومنطقية
 * يتيح القفز السريع والتنقل الذكي (Next/Previous Chapter) بين مقاطع التسجيلات
 */
@Singleton
class SmartChapterManager @Inject constructor() {

    /**
     * توليد الفصول الذكية لتسجيل الفيديو بناءً على المدة الكلية
     * تُقسم المحتويات حسب طولها:
     * - أقل من دقيقتين: فصل كامل واحد
     * - حتى 10 دقائق: فصول كل دقيقتين ونصف
     * - حتى 30 دقيقة: فصول كل 5 دقائق
     * - حتى ساعة: فصول كل 10 دقائق
     * - أكثر من ساعة: فصول كل 15 دقيقة
     */
    fun generateChapters(durationMs: Long, customTitlePrefix: String? = null): List<SmartChapter> {
        if (durationMs <= 0L) return emptyList()

        val chapters = mutableListOf<SmartChapter>()
        val prefix = customTitlePrefix ?: "الفصل"

        // إذا كان المقطع قصيراً جداً (أقل من دقيقتين)
        if (durationMs < 120_000L) {
            chapters.add(
                SmartChapter(
                    index = 1,
                    title = "التسجيل الكامل",
                    startMs = 0L,
                    endMs = durationMs
                )
            )
            return chapters
        }

        val chapterIntervalMs = when {
            durationMs < 600_000L -> 150_000L      // 2.5 دقيقة
            durationMs < 1_800_000L -> 300_000L    // 5 دقائق
            durationMs < 3_600_000L -> 600_000L    // 10 دقائق
            else -> 900_000L                       // 15 دقيقة
        }

        var currentStart = 0L
        var chapterIndex = 1

        while (currentStart < durationMs) {
            val currentEnd = (currentStart + chapterIntervalMs).coerceAtMost(durationMs)
            val remaining = durationMs - currentEnd

            // دمج الفتات الأخير إذا كان أقل من 40 ثانية مع الفصل الحالي
            val actualEnd = if (remaining < 40_000L) durationMs else currentEnd

            val title = when (chapterIndex) {
                1 -> "$prefix 1: البداية والمقدمة"
                else -> {
                    if (actualEnd == durationMs) {
                        "$prefix $chapterIndex: الخاتمة والنهاية"
                    } else {
                        "$prefix $chapterIndex: الجزء $chapterIndex"
                    }
                }
            }

            chapters.add(
                SmartChapter(
                    index = chapterIndex,
                    title = title,
                    startMs = currentStart,
                    endMs = actualEnd
                )
            )

            currentStart = actualEnd
            chapterIndex++
            if (actualEnd >= durationMs) break
        }

        return chapters
    }

    /**
     * العثور على الفصل الحالي الذي يحتوي على توقيت التشغيل
     */
    fun findCurrentChapter(positionMs: Long, chapters: List<SmartChapter>): SmartChapter? {
        if (chapters.isEmpty()) return null
        return chapters.firstOrNull { positionMs in it.startMs until it.endMs }
            ?: chapters.lastOrNull { positionMs >= it.endMs }
            ?: chapters.firstOrNull()
    }

    /**
     * الحصول على الفصل التالي للقفز السريع إليه
     */
    fun getNextChapter(positionMs: Long, chapters: List<SmartChapter>): SmartChapter? {
        if (chapters.isEmpty()) return null
        return chapters.firstOrNull { it.startMs > positionMs + 1000L }
    }

    /**
     * الحصول على الفصل السابق للقفز السريع إليه
     */
    fun getPreviousChapter(positionMs: Long, chapters: List<SmartChapter>): SmartChapter? {
        if (chapters.isEmpty()) return null
        val current = findCurrentChapter(positionMs, chapters)
        // إذا كان المستخدم تجاوز بداية الفصل بأكثر من ثانيتين، يعود لبداية نفس الفصل
        return if (current != null && (positionMs - current.startMs) > 2000L) {
            current
        } else {
            chapters.lastOrNull { it.endMs <= (current?.startMs ?: positionMs) } ?: chapters.firstOrNull()
        }
    }
}
