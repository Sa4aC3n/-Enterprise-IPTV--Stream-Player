package com.enterprise.iptv.domain.healing

import kotlinx.coroutines.Dispatchers
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
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun probeStream(url: String): ServerHealth = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1")
                .head()
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
        val results = urls.map { probeStream(it) }
        results.filter { it.isReachable }.minByOrNull { it.latencyMs }
    }
}
