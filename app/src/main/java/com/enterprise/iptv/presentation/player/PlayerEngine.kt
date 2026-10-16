package com.enterprise.iptv.presentation.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    @ApplicationContext private val context: Context
) {
    private val _playbackStatus = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)
    val playbackStatus = _playbackStatus.asStateFlow()

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

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _playbackStatus.value = when (state) {
                Player.STATE_BUFFERING -> PlaybackStatus.Buffering
                Player.STATE_READY -> PlaybackStatus.Playing
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

        override fun onTracksChanged(tracks: Tracks) {
            extractAvailableQualities(tracks)
        }
    }

    val exoPlayer: ExoPlayer by lazy {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSource))
            .build().apply {
                playWhenReady = true
                addListener(playerListener)
            }
    }

    private fun extractAvailableQualities(tracks: Tracks) {
        val qualities = mutableListOf<VideoQuality>()
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
                        qualities.add(
                            VideoQuality(
                                label = label,
                                width = format.width,
                                height = format.height,
                                bitrate = format.bitrate
                            )
                        )
                    }
                }
            }
        }
        _availableQualities.value = qualities.distinctBy { it.height }.sortedByDescending { it.height }
    }

    fun playChannel(streamUrl: String, backupUrls: List<String> = emptyList()) {
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
            // Auto Quality
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearVideoSizeConstraints()
                .build()
        } else {
            // Constrain max video size to selected quality
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setMaxVideoSize(quality.width, quality.height)
                .build()
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

    fun setResizeModeDirect(mode: Int) {
        _resizeMode.value = mode
    }

    fun release() {
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}
