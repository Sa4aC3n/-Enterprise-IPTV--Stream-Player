package com.enterprise.iptv.domain.caption

/**
 * يمثل مسار ترجمة نصية متوفر داخل البث أو ملف الفيديو
 * (WebVTT, CEA-608, SubRip, SRT, etc.)
 */
data class SubtitleTrack(
    val id: String,
    val language: String?,
    val label: String,
    val groupIndex: Int = 0,
    val trackIndex: Int = 0,
    val isSelected: Boolean = false
) {
    val displayTitle: String
        get() = when {
            label.isNotBlank() -> label
            !language.isNullOrBlank() -> getLanguageDisplayName(language)
            else -> "ترجمة افتراضية"
        }

    companion object {
        fun getLanguageDisplayName(langCode: String): String {
            return when (langCode.lowercase()) {
                "ar", "ara" -> "العربية (Arabic)"
                "en", "eng" -> "الإنجليزية (English)"
                "fr", "fra" -> "الفرنسية (French)"
                "es", "spa" -> "الإسبانية (Spanish)"
                "de", "deu" -> "الألمانية (German)"
                "it", "ita" -> "الإيطالية (Italian)"
                "tr", "tur" -> "التركية (Turkish)"
                "ru", "rus" -> "الروسية (Russian)"
                else -> langCode.uppercase()
            }
        }
    }
}
