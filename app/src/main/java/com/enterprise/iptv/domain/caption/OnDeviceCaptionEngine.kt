package com.enterprise.iptv.domain.caption

import android.content.Context
import android.content.Intent
import android.os.Build
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
 * محرك الترجمة والتفريغ الصوتي الحي أوفلاين على معالج الجهاز (On-Device Speech-to-Text)
 * يقوم بتحويل الكلام المباشر في القنوات أو التسجيلات إلى نصوص فورية
 * بدون إرسال أي بيانات أو صوت إلى خوادم خارجية (Zero Internet / Offline-First)
 */
@Singleton
class OnDeviceCaptionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _liveCaptionsText = MutableStateFlow("")
    val liveCaptionsText = _liveCaptionsText.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive = _isActive.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var consecutiveErrors = 0

    val isRecognitionSupported: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isRecognitionSupported) return
        stopListening()
        consecutiveErrors = 0

        try {
            speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (_isActive.value) {
                        restartListening()
                    }
                }

                override fun onError(error: Int) {
                    consecutiveErrors++
                    if (_isActive.value && consecutiveErrors <= 3) {
                        restartListening()
                    } else if (consecutiveErrors > 3) {
                        _liveCaptionsText.value = "الترجمة التوضيحية الحية غير متوفرة محلياً أو تتطلب إذناً"
                        _isActive.value = false
                    }
                }

                override fun onResults(results: Bundle?) {
                    consecutiveErrors = 0
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        _liveCaptionsText.value = matches[0]
                    }
                    if (_isActive.value) {
                        restartListening()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        _liveCaptionsText.value = matches[0]
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
            _isActive.value = true
        } catch (e: Exception) {
            _isActive.value = false
            _liveCaptionsText.value = ""
        }
    }

    private fun restartListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {}
    }

    fun stopListening() {
        _isActive.value = false
        _liveCaptionsText.value = ""
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun toggleListening(): Boolean {
        return if (_isActive.value) {
            stopListening()
            false
        } else {
            startListening()
            true
        }
    }
}
