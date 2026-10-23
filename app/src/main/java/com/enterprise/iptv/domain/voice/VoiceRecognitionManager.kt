package com.enterprise.iptv.domain.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير التعرف على الصوت والأوامر الصوتية (Voice Recognition Engine)
 * يستمع لنطق المستخدم ويحوله إلى أوامر تطبيق تنفيذية فورية
 */
@Singleton
class VoiceRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: VoiceCommandParser
) {
    private val _voiceState = MutableStateFlow<VoiceRecognitionState>(VoiceRecognitionState.Idle)
    val voiceState = _voiceState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isRecognitionAvailable) {
            _voiceState.value = VoiceRecognitionState.Error("خدمة التعرف على الصوت غير مدعومة على هذا الجهاز")
            return
        }

        stopListening()
        _voiceState.value = VoiceRecognitionState.Listening

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceState.value = VoiceRecognitionState.Listening
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "لم يتم التعرف على الصوت، يرجى المحاولة ثانية"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "انتهى وقت الاستماع دون صوت"
                            SpeechRecognizer.ERROR_AUDIO -> "خطأ في تسجيل الصوت من الميكروفون"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "يرجى منح إذن استخدام الميكروفون"
                            else -> "حدث خطأ أثناء الاستماع، حاول مرة أخرى"
                        }
                        _voiceState.value = VoiceRecognitionState.Error(message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            val command = parser.parse(recognizedText)
                            val feedback = getFeedbackMessage(command, recognizedText)
                            _voiceState.value = VoiceRecognitionState.Success(command, feedback)
                        } else {
                            _voiceState.value = VoiceRecognitionState.Error("لم يتم التقاط أي صوت واضح")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _voiceState.value = VoiceRecognitionState.Recognizing(matches[0])
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // دعم التقاط الصوت بالعربية والإنجليزية تلقائياً
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _voiceState.value = VoiceRecognitionState.Error("تعذر بدء الميكروفون: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        _voiceState.value = VoiceRecognitionState.Idle
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun resetState() {
        stopListening()
    }

    private fun getFeedbackMessage(command: VoiceCommand, rawText: String): String {
        return when (command) {
            is VoiceCommand.PlayChannel -> "جاري تشغيل: ${command.channelQuery}"
            is VoiceCommand.Search -> "جاري البحث عن: ${command.query}"
            VoiceCommand.OpenKidsMode -> "فتح فضاء الأطفال الآمن"
            VoiceCommand.OpenSleepTimer -> "فتح مؤقت النوم التلقائي"
            VoiceCommand.ToggleRecording -> "بدء / إيقاف التسجيل"
            VoiceCommand.OpenCast -> "فتح البث للشاشات الذكية"
            VoiceCommand.ToggleVocalDsp -> "تبديل وضع عازل الصوت DSP"
            VoiceCommand.ToggleCaptions -> "تبديل الترجمة التوضيحية"
            VoiceCommand.ToggleFullscreen -> "تبديل ملء الشاشة"
            is VoiceCommand.Unrecognized -> "تم سماع: $rawText"
        }
    }
}
