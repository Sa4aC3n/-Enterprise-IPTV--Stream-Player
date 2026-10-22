package com.enterprise.iptv.domain.recorder

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed interface RecordState {
    data object Idle : RecordState
    data class Recording(val bytesRecorded: Long, val durationSeconds: Long, val filePath: String) : RecordState
    data class Completed(val file: File) : RecordState
    data class Error(val message: String) : RecordState
}

@Singleton
class StreamRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    fun recordStream(channelName: String, streamUrl: String): Flow<RecordState> = flow {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanName = channelName.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val outputFile = File(storageDir, "DVR_${cleanName}_$timeStamp.ts")

        val startTime = System.currentTimeMillis()
        var totalBytes: Long = 0
        var lastUpdate = 0L

        try {
            val isHls = streamUrl.contains(".m3u8", ignoreCase = true)

            if (isHls) {
                // معالجة تسجيل بث HLS الحي عبر سحب وتجميع مقاطع TS المتتابعة
                var currentPlaylistUrl = streamUrl
                val initialRequest = Request.Builder().url(streamUrl).build()
                val initialResp = okHttpClient.newCall(initialRequest).execute()
                val initialContent = initialResp.body?.string() ?: ""

                // فحص إذا كان Master Playlist واستخراج أول مسار فرعي
                if (initialContent.contains("#EXT-X-STREAM-INF")) {
                    val variantUri = initialContent.lines()
                        .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    if (variantUri != null) {
                        currentPlaylistUrl = resolveUrl(streamUrl, variantUri)
                    }
                }

                val downloadedSegments = mutableSetOf<String>()

                while (coroutineContext.isActive) {
                    val mediaRequest = Request.Builder().url(currentPlaylistUrl).build()
                    val mediaResp = okHttpClient.newCall(mediaRequest).execute()
                    val mediaBody = mediaResp.body?.string() ?: ""

                    val segmentLines = mediaBody.lines()
                        .filter { it.isNotBlank() && !it.startsWith("#") }

                    for (seg in segmentLines) {
                        if (!coroutineContext.isActive) break
                        val resolvedSegUrl = resolveUrl(currentPlaylistUrl, seg)
                        if (downloadedSegments.add(resolvedSegUrl)) {
                            try {
                                val segRequest = Request.Builder().url(resolvedSegUrl).build()
                                val segResp = okHttpClient.newCall(segRequest).execute()
                                val segBytes = segResp.body?.bytes()
                                if (segBytes != null && segBytes.isNotEmpty()) {
                                    FileOutputStream(outputFile, true).use { fos ->
                                        fos.write(segBytes)
                                    }
                                    totalBytes += segBytes.size
                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdate >= 1000L) {
                                        val durationSec = (now - startTime) / 1000
                                        emit(RecordState.Recording(totalBytes, durationSec, outputFile.absolutePath))
                                        lastUpdate = now
                                    }
                                }
                            } catch (e: Exception) {
                                // تجاهل انقطاع مقطع واحد والاستمرار في البقية لضمان استمرارية التسجيل
                            }
                        }
                    }

                    delay(2500L) // انتظار توليد المقطع التالي في البث الحي
                }
            } else {
                // تدفق البث المباشر المباشر Direct Stream (MPEG-TS أو Raw)
                val request = Request.Builder().url(streamUrl).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    emit(RecordState.Error("Server returned code ${response.code}"))
                    return@flow
                }

                val body = response.body ?: run {
                    emit(RecordState.Error("Stream content is empty"))
                    return@flow
                }

                val buffer = ByteArray(64 * 1024)
                body.byteStream().use { input ->
                    FileOutputStream(outputFile).use { output ->
                        var bytesRead = input.read(buffer)
                        while (coroutineContext.isActive && bytesRead != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 1000L) {
                                val durationSec = (now - startTime) / 1000
                                emit(RecordState.Recording(totalBytes, durationSec, outputFile.absolutePath))
                                lastUpdate = now
                            }
                            bytesRead = input.read(buffer)
                        }
                    }
                }
            }

            if (outputFile.exists() && outputFile.length() > 0) {
                emit(RecordState.Completed(outputFile))
            }
        } catch (e: Exception) {
            if (outputFile.exists() && outputFile.length() > 0) {
                emit(RecordState.Completed(outputFile))
            } else {
                emit(RecordState.Error(e.localizedMessage ?: "Unknown recording error"))
            }
        } finally {
            // حذف أي ملف فارغ تماماً نتج عن فشل فوري لمنع تراكم ملفات تالفة في ذاكرة المستخدم
            if (outputFile.exists() && outputFile.length() == 0L) {
                outputFile.delete()
            }
        }
    }.flowOn(Dispatchers.IO)

    fun getSavedRecordings(): List<File> {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        return storageDir.listFiles { file -> file.isFile && file.name.startsWith("DVR_") && file.name.endsWith(".ts") && file.length() > 0 }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteRecording(file: File): Boolean {
        return try {
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveUrl(base: String, relative: String): String {
        return try {
            URI(base).resolve(relative).toString()
        } catch (e: Exception) {
            if (relative.startsWith("http://") || relative.startsWith("https://")) {
                relative
            } else {
                val lastSlash = base.lastIndexOf('/')
                if (lastSlash != -1) base.substring(0, lastSlash + 1) + relative else relative
            }
        }
    }
}
