package com.enterprise.iptv.presentation.multiview

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.enterprise.iptv.domain.model.Channel
import com.enterprise.iptv.presentation.player.PlaybackStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class MultiViewManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isActive = MutableStateFlow(false)
    val isActive = _isActive.asStateFlow()

    private val _layout = MutableStateFlow(MultiViewLayout.DUAL)
    val layout = _layout.asStateFlow()

    private val _slots = MutableStateFlow<List<MultiViewSlotState>>(emptyList())
    val slots = _slots.asStateFlow()

    private val playerMap = mutableMapOf<Int, ExoPlayer>()

    private fun createPlayerForSlot(slotId: Int): ExoPlayer {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1_500,
                /* maxBufferMs = */ 5_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSource))
            .setLoadControl(loadControl)
            .build()

        player.playWhenReady = true
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val newStatus = when (playbackState) {
                    Player.STATE_BUFFERING -> PlaybackStatus.Buffering
                    Player.STATE_READY -> PlaybackStatus.Playing
                    Player.STATE_ENDED -> PlaybackStatus.Ended
                    else -> PlaybackStatus.Idle
                }
                updateSlotStatus(slotId, newStatus)
            }

            override fun onPlayerError(error: PlaybackException) {
                updateSlotStatus(slotId, PlaybackStatus.Error(error.localizedMessage ?: "فشل تشغيل القناة"))
            }
        })
        return player
    }

    private fun updateSlotStatus(slotId: Int, status: PlaybackStatus) {
        _slots.value = _slots.value.map { slot ->
            if (slot.slotId == slotId) slot.copy(status = status) else slot
        }
    }

    fun getPlayer(slotId: Int): ExoPlayer? = playerMap[slotId]

    fun enter(
        initialChannel: Channel?,
        availableChannels: List<Channel>,
        initialLayout: MultiViewLayout = MultiViewLayout.DUAL
    ) {
        releaseAllPlayers()
        _layout.value = initialLayout
        _isActive.value = true

        val slotCount = if (initialLayout == MultiViewLayout.DUAL) 2 else 4
        val initialList = mutableListOf<MultiViewSlotState>()

        // تجهيز القنوات للشاشات المفتوحة
        val distinctChannels = mutableListOf<Channel>()
        if (initialChannel != null) distinctChannels.add(initialChannel)
        for (ch in availableChannels) {
            if (distinctChannels.size >= slotCount) break
            if (distinctChannels.none { it.id == ch.id }) {
                distinctChannels.add(ch)
            }
        }

        for (i in 0 until slotCount) {
            val player = createPlayerForSlot(i)
            playerMap[i] = player

            val isFocused = (i == 0)
            player.volume = if (isFocused) 1f else 0f

            val ch = distinctChannels.getOrNull(i)
            if (ch != null) {
                player.setMediaItem(MediaItem.fromUri(ch.streamUrl))
                player.prepare()
                player.play()
            }

            initialList.add(
                MultiViewSlotState(
                    slotId = i,
                    channel = ch,
                    isFocused = isFocused,
                    status = if (ch != null) PlaybackStatus.Buffering else PlaybackStatus.Idle
                )
            )
        }
        _slots.value = initialList
    }

    fun focusSlot(slotId: Int) {
        playerMap.forEach { (id, player) ->
            player.volume = if (id == slotId) 1f else 0f
        }
        _slots.value = _slots.value.map { slot ->
            slot.copy(isFocused = (slot.slotId == slotId))
        }
    }

    fun setSlotChannel(slotId: Int, channel: Channel) {
        var player = playerMap[slotId]
        if (player == null) {
            player = createPlayerForSlot(slotId)
            playerMap[slotId] = player
        }

        val isFocused = _slots.value.find { it.slotId == slotId }?.isFocused ?: false
        player.volume = if (isFocused) 1f else 0f
        player.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        player.prepare()
        player.play()

        _slots.value = _slots.value.map { slot ->
            if (slot.slotId == slotId) {
                slot.copy(channel = channel, status = PlaybackStatus.Buffering)
            } else slot
        }
    }

    fun setLayout(newLayout: MultiViewLayout, availableChannels: List<Channel>) {
        if (_layout.value == newLayout) return
        _layout.value = newLayout

        val targetCount = if (newLayout == MultiViewLayout.DUAL) 2 else 4
        val currentSlots = _slots.value.toMutableList()

        if (targetCount == 2) {
            // تقليص من 4 إلى 2 شاشة: تحرير السلوتس 2 و 3
            for (i in 2..3) {
                playerMap.remove(i)?.release()
            }
            val retained = currentSlots.filter { it.slotId < 2 }
            // التأكد من وجود تركيز صوتي على إحدى الشاشتين
            val hasFocus = retained.any { it.isFocused }
            _slots.value = retained.mapIndexed { idx, slot ->
                val focused = if (!hasFocus) idx == 0 else slot.isFocused
                playerMap[slot.slotId]?.volume = if (focused) 1f else 0f
                slot.copy(isFocused = focused)
            }
        } else {
            // توسيع من 2 إلى 4 شاشات: إنشاء السلوتس 2 و 3
            val usedIds = currentSlots.mapNotNull { it.channel?.id }.toSet()
            val remainingChannels = availableChannels.filter { it.id !in usedIds }
            var remIdx = 0

            for (i in 2..3) {
                val player = createPlayerForSlot(i)
                player.volume = 0f
                playerMap[i] = player

                val ch = remainingChannels.getOrNull(remIdx++)
                if (ch != null) {
                    player.setMediaItem(MediaItem.fromUri(ch.streamUrl))
                    player.prepare()
                    player.play()
                }
                currentSlots.add(
                    MultiViewSlotState(
                        slotId = i,
                        channel = ch,
                        isFocused = false,
                        status = if (ch != null) PlaybackStatus.Buffering else PlaybackStatus.Idle
                    )
                )
            }
            _slots.value = currentSlots
        }
    }

    fun exit(): Channel? {
        val focusedChannel = _slots.value.find { it.isFocused }?.channel
            ?: _slots.value.firstOrNull()?.channel
        releaseAllPlayers()
        _isActive.value = false
        _slots.value = emptyList()
        return focusedChannel
    }

    private fun releaseAllPlayers() {
        playerMap.values.forEach { it.release() }
        playerMap.clear()
    }

    fun release() {
        releaseAllPlayers()
        _isActive.value = false
        _slots.value = emptyList()
    }
}
