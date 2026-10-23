package com.enterprise.iptv.domain.meter

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * بيانات إحصائيات استهلاك الإنترنت والسرعة اللحظية للبث
 */
data class NetworkDataStats(
    val sessionBytes: Long = 0L,
    val currentStreamBytes: Long = 0L,
    val bitrateBps: Long = 0L,
    val formattedSessionData: String = "0.0 MB",
    val formattedCurrentStreamData: String = "0.0 MB",
    val formattedBitrate: String = "0 Kbps"
)

/**
 * عداد استهلاك البيانات وسرعة تدفق البث اللحظي (Data Usage & Real-Time Bitrate Meter)
 * يرتبط مباشرة بمحرك Media3 / ExoPlayer عبر [TransferListener] لقياس كل بايت يتم تحميله لحظياً
 */
@OptIn(UnstableApi::class)
@Singleton
class DataUsageMeter @Inject constructor() : TransferListener {

    private val meterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val totalSessionBytes = AtomicLong(0L)
    private val currentStreamBytes = AtomicLong(0L)
    private val windowBytes = AtomicLong(0L)

    private val _dataStats = MutableStateFlow(NetworkDataStats())
    val dataStats = _dataStats.asStateFlow()

    private var tickerJob: Job? = null

    init {
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = meterScope.launch {
            while (isActive) {
                delay(1000)
                updateStats()
            }
        }
    }

    private fun updateStats() {
        val window = windowBytes.getAndSet(0L)
        val instantBps = window * 8L
        val session = totalSessionBytes.get()
        val current = currentStreamBytes.get()

        val currentBps = if (instantBps > 0) {
            instantBps
        } else if (_dataStats.value.bitrateBps > 0) {
            // تخفيض تدريجي في حال توقف البث مؤقتاً
            (_dataStats.value.bitrateBps * 0.65).toLong()
        } else {
            0L
        }

        _dataStats.value = NetworkDataStats(
            sessionBytes = session,
            currentStreamBytes = current,
            bitrateBps = currentBps,
            formattedSessionData = formatBytes(session),
            formattedCurrentStreamData = formatBytes(current),
            formattedBitrate = formatBitrate(currentBps)
        )
    }

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}

    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}

    override fun onBytesTransferred(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
        bytesTransferred: Int
    ) {
        if (isNetwork && bytesTransferred > 0) {
            totalSessionBytes.addAndGet(bytesTransferred.toLong())
            currentStreamBytes.addAndGet(bytesTransferred.toLong())
            windowBytes.addAndGet(bytesTransferred.toLong())
        }
    }

    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}

    fun resetCurrentStreamCounter() {
        currentStreamBytes.set(0L)
        updateStats()
    }

    fun resetSessionCounter() {
        totalSessionBytes.set(0L)
        currentStreamBytes.set(0L)
        windowBytes.set(0L)
        updateStats()
    }

    fun release() {
        tickerJob?.cancel()
        meterScope.cancel()
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
                bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
                bytes >= 1024L -> String.format(Locale.US, "%d KB", bytes / 1024)
                else -> "$bytes B"
            }
        }

        fun formatBitrate(bps: Long): String {
            return when {
                bps >= 1_000_000L -> String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
                bps >= 1_000L -> String.format(Locale.US, "%d Kbps", bps / 1_000)
                bps > 0L -> "$bps bps"
                else -> "0 Kbps"
            }
        }
    }
}
