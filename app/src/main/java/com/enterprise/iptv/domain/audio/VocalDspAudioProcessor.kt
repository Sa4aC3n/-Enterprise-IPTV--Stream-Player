package com.enterprise.iptv.domain.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * أنماط معالجة الصوت الترددية اللحظية (Real-Time DSP Modes)
 */
enum class VocalDspMode(val titleAr: String, val titleEn: String) {
    OFF("الصوت الأصلي", "Original Audio"),
    ENHANCE_VOCALS("توضيح الكلام (عزل الموسيقى)", "Enhance Speech (Subdue Music)"),
    REMOVE_VOCALS("عازل الصوت (موسيقى / كاريوكي فقط)", "Vocal Remover (Music Only)")
}

/**
 * معالج صوت رقمي فوري (Real-Time DSP AudioProcessor) مدمج في مسار ExoPlayer
 * يقوم بتعديل إشارات الـ PCM الصوتية لحظياً:
 * 1. وضع توضيح الكلام (Speech Enhancer): يعزل ويرفع ترددات الصوت البشري ويخمد الموسيقى والضوضاء الخلفية.
 * 2. وضع عازل الصوت (Vocal Remover / Karaoke): يلغي الصوت البشري المتمركز في المنتصف (Center Channel Cancellation)
 *    مع الحفاظ على الموسيقى المحيطية والترددات المنخفضة (Bass Preservation).
 *
 * يعمل دون استهلاك أي بيانات إنترنت، ودون أي تأخير صوتي (Zero Latency)، ودون الحاجة لتنزيل أي نماذج خارجية.
 */
@OptIn(UnstableApi::class)
@Singleton
class VocalDspAudioProcessor @Inject constructor() : AudioProcessor {

    private val _currentMode = MutableStateFlow(VocalDspMode.OFF)
    val currentMode = _currentMode.asStateFlow()

    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat: AudioFormat = AudioFormat.NOT_SET

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    // مرشح تمرير الترددات المنخفضة للحفاظ على إيقاع الموسيقى والـ Bass في وضع عزل الصوت
    private var prevBassSample = 0.0
    private var sampleRate = 44100

    fun setMode(mode: VocalDspMode) {
        _currentMode.value = mode
    }

    fun cycleMode(): VocalDspMode {
        val next = when (_currentMode.value) {
            VocalDspMode.OFF -> VocalDspMode.ENHANCE_VOCALS
            VocalDspMode.ENHANCE_VOCALS -> VocalDspMode.REMOVE_VOCALS
            VocalDspMode.REMOVE_VOCALS -> VocalDspMode.OFF
        }
        _currentMode.value = next
        return next
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            this.inputAudioFormat = AudioFormat.NOT_SET
            this.outputAudioFormat = AudioFormat.NOT_SET
            return AudioFormat.NOT_SET
        }

        this.inputAudioFormat = inputAudioFormat
        this.sampleRate = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 44100

        // يعمل المعالج على إشارات الستيريو (قناتان)
        this.outputAudioFormat = if (inputAudioFormat.channelCount == 2) {
            inputAudioFormat
        } else {
            // إذا كان البث أحادي (Mono)، يبقى كستيريو أو يمرر كما هو
            inputAudioFormat
        }
        return this.outputAudioFormat
    }

    override fun isActive(): Boolean {
        return outputAudioFormat != AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val mode = _currentMode.value

        // إذا كان المعالج غير مفعل (وضع الصوت الأصلي) أو القنوات غير ثنائية: نمرر الدفق كما هو
        if (mode == VocalDspMode.OFF || inputAudioFormat.channelCount != 2) {
            if (buffer.capacity() < remaining) {
                buffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
            } else {
                buffer.clear()
            }
            buffer.put(inputBuffer)
            buffer.flip()
            outputBuffer = buffer
            return
        }

        if (buffer.capacity() < remaining) {
            buffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        val shortInput = inputBuffer.asShortBuffer()
        val shortOutput = buffer.asShortBuffer()
        val numStereoPairs = shortInput.remaining() / 2

        // معامل مرشح الترددات المنخفضة (Cutoff ~ 140 Hz)
        val alpha = (2.0 * Math.PI * 140.0 / sampleRate).coerceIn(0.005, 0.05)

        for (i in 0 until numStereoPairs) {
            val left = shortInput.get().toDouble()
            val right = shortInput.get().toDouble()

            when (mode) {
                VocalDspMode.REMOVE_VOCALS -> {
                    // 1. خوارزمية إلغاء الطور الصوتي للمركز المحسنة (Phase-Decorrelated Vocal Remover)
                    // في التسجيلات الستيريو، الغناء يكون في منتصف الإشارة (L = R)، بينما الموسيقى لها اتساع ستيريو
                    val diff = (left - right) * 0.85

                    // استخلاص ترددات البيس العميقة من المنتصف لمنع فقدان إيقاع الطبول والجهير
                    val center = (left + right) * 0.5
                    prevBassSample += alpha * (center - prevBassSample)
                    val bass = prevBassSample * 0.9

                    // تصحيح الطور لمنع انعدام الصوت كلياً عند تشغيله على مكبرات الهواتف الأحادية (Mono Summing)
                    val outLeft = clampShort(diff * 0.95 + bass)
                    val outRight = clampShort(-diff * 0.45 + bass + (right * 0.15))

                    shortOutput.put(outLeft)
                    shortOutput.put(outRight)
                }

                VocalDspMode.ENHANCE_VOCALS -> {
                    // 2. خوارزمية تعزيز الصوت البشري وتخميد الموسيقى الخلفية (Dialogue Boost)
                    // رفع طاقة الإشارة المتمركزة (الصوت البشري) وخفض فارق الستيريو (الموسيقى والمؤثرات)
                    val center = (left + right) * 0.5
                    val diff = (left - right) * 0.5

                    // تعزيز صوت المذيع / المعلق وتخميد الموسيقى
                    val boostedCenter = center * 1.35
                    val attenuatedDiff = diff * 0.40

                    val outLeft = clampShort(boostedCenter + attenuatedDiff)
                    val outRight = clampShort(boostedCenter - attenuatedDiff)

                    shortOutput.put(outLeft)
                    shortOutput.put(outRight)
                }

                VocalDspMode.OFF -> {
                    shortOutput.put(left.toInt().toShort())
                    shortOutput.put(right.toInt().toShort())
                }
            }
        }

        inputBuffer.position(inputBuffer.position() + remaining)
        buffer.position(0)
        buffer.limit(remaining)
        outputBuffer = buffer
    }

    private fun clampShort(value: Double): Short {
        return when {
            value > Short.MAX_VALUE -> Short.MAX_VALUE
            value < Short.MIN_VALUE -> Short.MIN_VALUE
            else -> value.toInt().toShort()
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer == EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        prevBassSample = 0.0
    }

    override fun reset() {
        flush()
        buffer = EMPTY_BUFFER
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
    }
}
