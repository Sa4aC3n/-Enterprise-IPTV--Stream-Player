package com.enterprise.iptv.presentation.multiview

import androidx.media3.exoplayer.ExoPlayer
import com.enterprise.iptv.domain.model.Channel
import com.enterprise.iptv.presentation.player.PlaybackStatus

enum class MultiViewLayout {
    DUAL, // شاشتان
    QUAD  // 4 شاشات
}

data class MultiViewSlotState(
    val slotId: Int,
    val channel: Channel?,
    val isFocused: Boolean = false,
    val status: PlaybackStatus = PlaybackStatus.Idle
)
