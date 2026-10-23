package com.enterprise.iptv.presentation.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.TrackSelectionOverride
import com.enterprise.iptv.domain.audio.VocalDspAudioProcessor
import com.enterprise.iptv.domain.audio.VocalDspMode
import com.enterprise.iptv.domain.caption.OnDeviceCaptionEngine
import com.enterprise.iptv.domain.caption.SubtitleTrack
import com.enterprise.iptv.domain.chapter.SmartChapter
import com.enterprise.iptv.domain.chapter.SmartChapterManager
import com.enterprise.iptv.domain.meter.DataUsageMeter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlaybackStatus {
    data object Idle : PlaybackStatus
    data object Buffering : PlaybackStatus
    data object Playing : PlaybackStatus
    data object Ended : PlaybackStatus
    data class Error(val message: String) : PlaybackStatus
}

data class VideoQuality(
    val label: String,
    val width: Int,
    val height: Int,
    val bitrate: Int = 0
)

data class StreamServer(
    val name: String,
    val url: String
)

@OptIn(UnstableApi::class)
@Singleton
class PlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    val vocalDspProcessor: VocalDspAudioProcessor,
    val smartChapterManager: SmartChapterManager,
    val onDeviceCaptionEngine: OnDeviceCaptionEngine,
    val dataUsageMeter: DataUsageMeter
) {
    private val _playbackStatus = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)
    val playbackStatus = _playbackStatus.asStateFlow()

    // عداد استهلاك البيانات والسرعة اللحظية للبث (Data Usage & Speed Meter)
    val networkStats = dataUsageMeter.dataStats

    fun resetSessionDataCounter() {
        dataUsageMeter.resetSessionCounter()
    }

    // معالج الصوت الترددي اللحظي (عازل الصوت وموضح الكلام)
    val vocalDspMode = vocalDspProcessor.currentMode

    // الترجمة التوضيحية الفورية (Captions & Subtitles)
    private val _availableSubtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val availableSubtitles = _availableSubtitles.asStateFlow()

    private val _selectedSubtitle = MutableStateFlow<SubtitleTrack?>(null)
    val selectedSubtitle = _selectedSubtitle.asStateFlow()

    private val _isCaptionsEnabled = MutableStateFlow(false)
    val isCaptionsEnabled = _isCaptionsEnabled.asStateFlow()

    private val _embeddedSubtitleText = MutableStateFlow("")

    val liveSpeechCaptionsText = onDeviceCaptionEngine.liveCaptionsText
    val isLiveSpeechCaptionsActive = onDeviceCaptionEngine.isActive

    // دمج الترجمة المدمجة مع التفريغ الصوتي اللحظي أوفلاين
    val currentCaptionText = combine(_embeddedSubtitleText, liveSpeechCaptionsText, _isCaptionsEnabled) { embedded, live, enabled ->
        if (!enabled) ""
        else embedded.ifBlank { live }
    }.stateIn(playerScope, SharingStarted.WhileSubscribed(5000), "")

    // الفصول الذكية للتسجيلات (Smart Chapters) ومؤشرات التوقيت
    private val _smartChapters = MutableStateFlow<List<SmartChapter>>(emptyList())
    val smartChapters = _smartChapters.asStateFlow()

    private val _currentChapter = MutableStateFlow<SmartChapter?>(null)
    val currentChapter = _currentChapter.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _isLiveStream = MutableStateFlow(true)
    val isLiveStream = _isLiveStream.asStateFlow()

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTickerJob: Job? = null

    // Stream Servers (Primary + Backups) & Auto-Failover
    private val _servers = MutableStateFlow<List<StreamServer>>(emptyList())
    val servers = _servers.asStateFlow()

    private val _currentServerIndex = MutableStateFlow(0)
    val currentServerIndex = _currentServerIndex.asStateFlow()

    // Volume & Mute State
    private val _volume = MutableStateFlow(1f)
    val volume = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()
    private var previousVolume = 1f

    // Resize Mode (Fit = 0, Fill = 3, Zoom = 4)
    private val _resizeMode = MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val resizeMode = _resizeMode.asStateFlow()

    // Video Qualities available
    private val _availableQualities = MutableStateFlow<List<VideoQuality>>(emptyList())
    val availableQualities = _availableQualities.asStateFlow()

    private val _selectedQuality = MutableStateFlow<VideoQuality?>(null) // null = Auto
    val selectedQuality = _selectedQuality.asStateFlow()

    // وضع الاستماع الصوتي فقط وموفر البيانات (Audio-Only Mode)
    private val _isAudioOnly = MutableStateFlow(false)
    val isAudioOnly = _isAudioOnly.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _playbackStatus.value = when (state) {
                Player.STATE_BUFFERING -> PlaybackStatus.Buffering
                Player.STATE_READY -> {
                    checkContentDurationAndChapters()
                    startProgressTicker()
                    PlaybackStatus.Playing
                }
                Player.STATE_ENDED -> PlaybackStatus.Ended
                else -> PlaybackStatus.Idle
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val nextIndex = _currentServerIndex.value + 1
            val list = _servers.value
            if (nextIndex < list.size) {
                // Auto-failover to backup server
                _currentServerIndex.value = nextIndex
                playUrlInternal(list[nextIndex].url)
            } else {
                _playbackStatus.value = PlaybackStatus.Error(error.localizedMessage ?: "Playback Error")
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            val texts = cueGroup.cues.mapNotNull { it.text?.toString() }
            _embeddedSubtitleText.value = texts.joinToString("\n")
        }

        override fun onTracksChanged(tracks: Tracks) {
            extractAvailableQualities(tracks)
            extractAvailableSubtitles(tracks)
        }
    }

    val exoPlayer: ExoPlayer by lazy {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setTransferListener(dataUsageMeter)

        // إعداد متحكم تخزين فائق السرعة لتقليل زمن انتظار فتح وتبديل القنوات (Zero-Buffer Fast Zapping)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1_500,
                /* maxBufferMs = */ 6_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // ربط معالج الصوت الرقمي (عازل الصوت وموضح الكلام) بمسار الصوت في ExoPlayer
        val audioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(vocalDspProcessor))
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return audioSink
            }
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSource))
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                addListener(playerListener)
            }
    }

    private fun extractAvailableQualities(tracks: Tracks) {
        val streamQualities = mutableListOf<VideoQuality>()
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.height > 0) {
                        val label = when {
                            format.height >= 1080 -> "1080p FHD"
                            format.height >= 720 -> "720p HD"
                            format.height >= 480 -> "480p SD"
                            format.height >= 360 -> "360p"
                            else -> "${format.height}p"
                        }
                        val bitrateLabel = if (format.bitrate > 0) " (${format.bitrate / 1000}k)" else ""
                        streamQualities.add(
                            VideoQuality(
                                label = "$label$bitrateLabel",
                                width = format.width,
                                height = format.height,
                                bitrate = format.bitrate
                            )
                        )
                    }
                }
            }
        }
        val distinct = streamQualities.distinctBy { it.height }.sortedByDescending { it.height }
        if (distinct.size > 1) {
            _availableQualities.value = distinct
        } else {
            // توفير خيارات الجودة القياسية لتمكين المستخدم من تحديد سقف استهلاك الباقة
            val baseList = distinct.toMutableList()
            val standardPresets = listOf(
                VideoQuality("1080p FHD (أعلى دقة)", 1920, 1080),
                VideoQuality("720p HD (متوازن)", 1280, 720),
                VideoQuality("480p SD (موفر للباقة)", 854, 480),
                VideoQuality("360p (أقصى توفير)", 640, 360)
            )
            for (preset in standardPresets) {
                if (baseList.none { it.height == preset.height }) {
                    baseList.add(preset)
                }
            }
            _availableQualities.value = baseList.sortedByDescending { it.height }
        }
    }

    private fun extractAvailableSubtitles(tracks: Tracks) {
        val subs = mutableListOf<SubtitleTrack>()
        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val id = format.id ?: "$groupIndex-$trackIndex"
                    val lang = format.language
                    val label = format.label ?: ""
                    val isSelected = group.isTrackSelected(trackIndex)
                    subs.add(
                        SubtitleTrack(
                            id = id,
                            language = lang,
                            label = label,
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            isSelected = isSelected
                        )
                    )
                }
            }
        }
        _availableSubtitles.value = subs
        val selected = subs.firstOrNull { it.isSelected }
        _selectedSubtitle.value = selected
        if (selected != null) {
            _isCaptionsEnabled.value = true
        }
    }

    fun selectSubtitleTrack(track: SubtitleTrack?) {
        _selectedSubtitle.value = track
        if (track == null) {
            _isCaptionsEnabled.value = false
            _embeddedSubtitleText.value = ""
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            _isCaptionsEnabled.value = true
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(track.language)
                .build()
        }
    }

    fun toggleCaptions() {
        val currentlyEnabled = _isCaptionsEnabled.value
        if (currentlyEnabled) {
            selectSubtitleTrack(null)
            onDeviceCaptionEngine.stopListening()
            _isCaptionsEnabled.value = false
        } else {
            val first = _availableSubtitles.value.firstOrNull()
            if (first != null) {
                selectSubtitleTrack(first)
            } else {
                _isCaptionsEnabled.value = true
                onDeviceCaptionEngine.startListening()
            }
        }
    }

    fun toggleLiveSpeechCaptions() {
        val active = onDeviceCaptionEngine.toggleListening()
        _isCaptionsEnabled.value = active || _selectedSubtitle.value != null
    }

    fun playChannel(streamUrl: String, backupUrls: List<String> = emptyList()) {
        dataUsageMeter.resetCurrentStreamCounter()
        _isLiveStream.value = true
        _smartChapters.value = emptyList()
        _currentChapter.value = null
        _durationMs.value = 0L
        _currentPositionMs.value = 0L
        _embeddedSubtitleText.value = ""
        val allUrls = (listOf(streamUrl) + backupUrls).filter { it.isNotBlank() }.distinct()
        val serverList = allUrls.mapIndexed { index, url ->
            val label = if (index == 0) "سيرفر 1 (رئيسي)" else "سيرفر ${index + 1} (بديل)"
            StreamServer(label, url)
        }
        _servers.value = serverList
        _currentServerIndex.value = 0
        if (serverList.isNotEmpty()) {
            playUrlInternal(serverList[0].url)
        }
    }

    fun selectServer(index: Int) {
        val list = _servers.value
        if (index in list.indices && index != _currentServerIndex.value) {
            _currentServerIndex.value = index
            playUrlInternal(list[index].url)
        }
    }

    private fun playUrlInternal(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        _selectedQuality.value = null
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun setQuality(quality: VideoQuality?) {
        _selectedQuality.value = quality
        if (quality == null) {
            // وضع التكيف التلقائي (Auto Adaptive)
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearVideoSizeConstraints()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
        } else {
            val builder = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setMaxVideoSize(quality.width, quality.height)
            if (quality.bitrate > 0) {
                builder.setMaxVideoBitrate(quality.bitrate)
            }
            for (group in exoPlayer.currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        if (format.height == quality.height) {
                            builder.setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, i)
                            )
                            break
                        }
                    }
                }
            }
            exoPlayer.trackSelectionParameters = builder.build()
        }
    }

    fun setVolume(newVolume: Float) {
        val clamped = newVolume.coerceIn(0f, 1f)
        _volume.value = clamped
        _isMuted.value = clamped == 0f
        exoPlayer.volume = clamped
    }

    fun toggleMute() {
        if (_isMuted.value) {
            setVolume(if (previousVolume > 0f) previousVolume else 1f)
        } else {
            previousVolume = _volume.value
            setVolume(0f)
        }
    }

    fun cycleResizeMode(): String {
        _resizeMode.value = when (_resizeMode.value) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        return when (_resizeMode.value) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom (ملء مع الحفاظ على الأبعاد)"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch (تمديد كامل الشاشة)"
            else -> "Fit (الحجم الأصلي)"
        }
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun toggleAudioOnly() {
        val next = !_isAudioOnly.value
        _isAudioOnly.value = next
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, next)
            .build()
    }

    fun cycleVocalDspMode(): VocalDspMode {
        return vocalDspProcessor.cycleMode()
    }

    fun setVocalDspMode(mode: VocalDspMode) {
        vocalDspProcessor.setMode(mode)
    }

    private fun checkContentDurationAndChapters() {
        val dur = exoPlayer.duration
        if (dur > 0L && dur != C.TIME_UNSET) {
            _durationMs.value = dur
            _isLiveStream.value = false
            if (_smartChapters.value.isEmpty()) {
                _smartChapters.value = smartChapterManager.generateChapters(dur)
            }
            _currentChapter.value = smartChapterManager.findCurrentChapter(exoPlayer.currentPosition, _smartChapters.value)
        } else {
            _durationMs.value = 0L
            _isLiveStream.value = true
            _smartChapters.value = emptyList()
            _currentChapter.value = null
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = playerScope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying || _playbackStatus.value is PlaybackStatus.Playing) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    _currentPositionMs.value = pos
                    val chapters = _smartChapters.value
                    if (chapters.isNotEmpty()) {
                        _currentChapter.value = smartChapterManager.findCurrentChapter(pos, chapters)
                    }
                }
                delay(500)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1L))
        exoPlayer.seekTo(target)
        _currentPositionMs.value = target
        val chapters = _smartChapters.value
        if (chapters.isNotEmpty()) {
            _currentChapter.value = smartChapterManager.findCurrentChapter(target, chapters)
        }
    }

    fun seekToChapter(chapter: SmartChapter) {
        seekTo(chapter.startMs)
    }

    fun seekToNextChapter(): Boolean {
        val next = smartChapterManager.getNextChapter(exoPlayer.currentPosition, _smartChapters.value)
        return if (next != null) {
            seekToChapter(next)
            true
        } else false
    }

    fun seekToPreviousChapter(): Boolean {
        val prev = smartChapterManager.getPreviousChapter(exoPlayer.currentPosition, _smartChapters.value)
        return if (prev != null) {
            seekToChapter(prev)
            true
        } else false
    }

    fun playFile(file: java.io.File) {
        dataUsageMeter.resetCurrentStreamCounter()
        _isLiveStream.value = false
        _smartChapters.value = emptyList()
        _currentChapter.value = null
        _durationMs.value = 0L
        _currentPositionMs.value = 0L
        _embeddedSubtitleText.value = ""
        _servers.value = listOf(StreamServer("تسجيل محلي", file.absolutePath))
        _currentServerIndex.value = 0
        playUrlInternal(android.net.Uri.fromFile(file).toString())
    }

    fun setResizeModeDirect(mode: Int) {
        _resizeMode.value = mode
    }

    fun release() {
        progressTickerJob?.cancel()
        playerScope.cancel()
        dataUsageMeter.release()
        onDeviceCaptionEngine.stopListening()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}
