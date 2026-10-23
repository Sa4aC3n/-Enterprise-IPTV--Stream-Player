package com.enterprise.iptv.domain.voice

/**
 * الأوامر الصوتية المعترف بها داخل التطبيق
 */
sealed interface VoiceCommand {
    /**
     * تشغيل قناة محددة بالاسم أو الرقم
     */
    data class PlayChannel(val channelQuery: String) : VoiceCommand

    /**
     * البحث عن كلمة مفتاحية في قائمة القنوات
     */
    data class Search(val query: String) : VoiceCommand

    /**
     * فتح فضاء الأطفال الآمن المقفل برمز PIN
     */
    data object OpenKidsMode : VoiceCommand

    /**
     * فتح وضبط مؤقت النوم التلقائي
     */
    data object OpenSleepTimer : VoiceCommand

    /**
     * بدء أو إيقاف تسجيل البث الجاري (DVR)
     */
    data object ToggleRecording : VoiceCommand

    /**
     * فتح نافذة البث للشاشات الذكية (DLNA / Cast)
     */
    data object OpenCast : VoiceCommand

    /**
     * تبديل نمط عازل الصوت وتوضيح الكلام (Vocal DSP)
     */
    data object ToggleVocalDsp : VoiceCommand

    /**
     * تشغيل أو إيقاف الترجمة التوضيحية الفورية (Captions)
     */
    data object ToggleCaptions : VoiceCommand

    /**
     * تكبير أو تصغير الشاشة (Fullscreen)
     */
    data object ToggleFullscreen : VoiceCommand

    /**
     * فتح إعدادات الجودة وعداد استهلاك البيانات (Quality & Data Meter)
     */
    data object OpenQualitySettings : VoiceCommand

    /**
     * أمر غير معروف أو عام
     */
    data class Unrecognized(val rawText: String) : VoiceCommand
}

/**
 * حالة التفاعل الصوتي الحي
 */
sealed interface VoiceRecognitionState {
    data object Idle : VoiceRecognitionState
    data object Listening : VoiceRecognitionState
    data class Recognizing(val partialText: String) : VoiceRecognitionState
    data class Success(val command: VoiceCommand, val feedbackMessage: String) : VoiceRecognitionState
    data class Error(val errorMessage: String) : VoiceRecognitionState
}
