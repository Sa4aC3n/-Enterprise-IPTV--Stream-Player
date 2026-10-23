package com.enterprise.iptv.presentation.player

import android.app.Activity
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * حالة مؤشر التحكم بالإيماءات على الشاشة (Gesture HUD State)
 */
sealed interface GestureHudState {
    data object Hidden : GestureHudState
    data class Volume(val percentage: Int) : GestureHudState
    data class Brightness(val percentage: Int) : GestureHudState
    data class Seek(val targetTimeMs: Long, val totalDurationMs: Long, val deltaSeconds: Long) : GestureHudState
    data class DoubleTapJump(val isForward: Boolean) : GestureHudState
}

/**
 * شارة مؤشر التحكم بالإيماءات العائمة في منتصف الشاشة
 */
@Composable
fun GestureHudOverlay(
    state: GestureHudState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state !is GestureHudState.Hidden,
        enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.85f),
        exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
            tonalElevation = 8.dp
        ) {
            when (state) {
                is GestureHudState.Volume -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                state.percentage == 0 -> Icons.Default.VolumeMute
                                state.percentage < 50 -> Icons.Default.VolumeDown
                                else -> Icons.Default.VolumeUp
                            },
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "${state.percentage}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.percentage / 100f },
                            modifier = Modifier.width(110.dp).height(6.dp).clip(CircleShape),
                            color = Color(0xFF00E5FF),
                            trackColor = Color.DarkGray
                        )
                    }
                }
                is GestureHudState.Brightness -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                state.percentage < 30 -> Icons.Default.BrightnessLow
                                state.percentage < 70 -> Icons.Default.BrightnessMedium
                                else -> Icons.Default.BrightnessHigh
                            },
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "${state.percentage}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.percentage / 100f },
                            modifier = Modifier.width(110.dp).height(6.dp).clip(CircleShape),
                            color = Color(0xFFFFD54F),
                            trackColor = Color.DarkGray
                        )
                    }
                }
                is GestureHudState.Seek -> {
                    val isForward = state.deltaSeconds >= 0
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp)
                    ) {
                        Icon(
                            imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${if (isForward) "+" else ""}${state.deltaSeconds}s",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${formatTimeMs(state.targetTimeMs)} / ${formatTimeMs(state.totalDurationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }
                is GestureHudState.DoubleTapJump -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.isForward) "+10 ثوانٍ" else "-10 ثوانٍ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                GestureHudState.Hidden -> {}
            }
        }
    }
}

private enum class DragMode { NONE, BRIGHTNESS, VOLUME, SEEK }

/**
 * Modifier لإضافة استشعار إيماءات اللمس والسحب والنقر المزدوج على سطح الفيديو
 */
fun Modifier.videoPlayerGestures(
    context: Context,
    isLiveStream: Boolean,
    durationMs: Long,
    currentPositionMs: Long,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleControls: () -> Unit,
    onHudChange: (GestureHudState) -> Unit
): Modifier = this.pointerInput(isLiveStream, durationMs, currentPositionMs, volume) {
    coroutineScope {
        var hudDismissJob: Job? = null

        fun showHud(hudState: GestureHudState) {
            hudDismissJob?.cancel()
            onHudChange(hudState)
            hudDismissJob = launch {
                delay(1200)
                onHudChange(GestureHudState.Hidden)
            }
        }

        var lastTapTime = 0L
        var lastTapOffset = Offset.Zero

        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false)
                val initialPos = down.position
                val screenWidth = size.width
                val screenHeight = size.height

                val activity = context as? Activity
                val initialBrightness = activity?.window?.attributes?.screenBrightness?.let {
                    if (it < 0f) 0.5f else it
                } ?: 0.5f

                var dragMode = DragMode.NONE
                var currentVolume = volume
                var currentBrightness = initialBrightness
                var targetSeekMs = currentPositionMs
                var seekDeltaSeconds = 0L

                var isDragDetected = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                    if (change.changedToUp()) {
                        // Pointer released
                        if (isDragDetected) {
                            if (dragMode == DragMode.SEEK && !isLiveStream && durationMs > 0) {
                                onSeekTo(targetSeekMs)
                            }
                            hudDismissJob?.cancel()
                            hudDismissJob = launch {
                                delay(600)
                                onHudChange(GestureHudState.Hidden)
                            }
                        } else {
                            // Tap detection
                            val now = System.currentTimeMillis()
                            val isDoubleTap = (now - lastTapTime < 300) && (abs(change.position.x - lastTapOffset.x) < 80)
                            if (isDoubleTap) {
                                lastTapTime = 0L
                                val isForward = change.position.x > screenWidth / 2
                                val jumpDelta = if (isForward) 10_000L else -10_000L
                                if (!isLiveStream && durationMs > 0) {
                                    val newPos = (currentPositionMs + jumpDelta).coerceIn(0L, durationMs)
                                    onSeekTo(newPos)
                                }
                                showHud(GestureHudState.DoubleTapJump(isForward))
                            } else {
                                lastTapTime = now
                                lastTapOffset = change.position
                                launch {
                                    delay(300)
                                    if (System.currentTimeMillis() - lastTapTime >= 300) {
                                        onToggleControls()
                                    }
                                }
                            }
                        }
                        break
                    }

                    val dx = change.position.x - initialPos.x
                    val dy = change.position.y - initialPos.y

                    if (!isDragDetected) {
                        val touchSlop = 20f
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            isDragDetected = true
                            dragMode = if (abs(dx) > abs(dy) && !isLiveStream && durationMs > 0) {
                                DragMode.SEEK
                            } else if (abs(dy) >= abs(dx)) {
                                if (initialPos.x > screenWidth / 2) DragMode.BRIGHTNESS else DragMode.VOLUME
                            } else {
                                DragMode.NONE
                            }
                        }
                    }

                    if (isDragDetected) {
                        change.consume()
                        when (dragMode) {
                            DragMode.BRIGHTNESS -> {
                                val deltaFraction = -dy / screenHeight
                                val newBrightness = (initialBrightness + deltaFraction).coerceIn(0.01f, 1.0f)
                                currentBrightness = newBrightness
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    lp.screenBrightness = newBrightness
                                    act.window.attributes = lp
                                }
                                onHudChange(GestureHudState.Brightness((newBrightness * 100).toInt()))
                            }
                            DragMode.VOLUME -> {
                                val deltaFraction = -dy / screenHeight
                                val newVolume = (volume + deltaFraction).coerceIn(0f, 1f)
                                currentVolume = newVolume
                                onVolumeChange(newVolume)
                                onHudChange(GestureHudState.Volume((newVolume * 100).toInt()))
                            }
                            DragMode.SEEK -> {
                                seekDeltaSeconds = (dx / screenWidth * 90).toLong()
                                targetSeekMs = (currentPositionMs + seekDeltaSeconds * 1000).coerceIn(0L, durationMs)
                                onHudChange(GestureHudState.Seek(targetSeekMs, durationMs, seekDeltaSeconds))
                            }
                            DragMode.NONE -> {}
                        }
                    }
                }
            }
        }
    }
}

/**
 * تنسيق الوقت بالدقائق والثواني
 */
private fun formatTimeMs(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
