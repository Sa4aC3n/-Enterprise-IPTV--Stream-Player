package com.enterprise.iptv.domain.healing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ServerHealth(
    val url: String,
    val latencyMs: Long,
    val isReachable: Boolean,
    val statusCode: Int
)

@Singleton
class StreamAutoHealer @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val probeClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun probeStream(url: String): ServerHealth = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            // استخدام طلب GET محدود البايتات متوافق مع كافة خوادم البث المباشر و HLS/TS
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1024")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) EnterpriseIPTV/1.0")
                .build()

            val response = probeClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            val isSuccess = response.isSuccessful || response.code in 200..399
            response.close()
            ServerHealth(
                url = url,
                latencyMs = latency,
                isReachable = isSuccess,
                statusCode = response.code
            )
        } catch (e: Exception) {
            ServerHealth(
                url = url,
                latencyMs = -1,
                isReachable = false,
                statusCode = -1
            )
        }
    }

    suspend fun findFastestServer(urls: List<String>): ServerHealth? = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext null
        // فحص متوازي فائق السرعة لكافة السيرفرات في نفس اللحظة
        val deferreds = urls.map { async { probeStream(it) } }
        val results = deferreds.awaitAll()
        results.filter { it.isReachable }.minByOrNull { it.latencyMs }
    }
}
