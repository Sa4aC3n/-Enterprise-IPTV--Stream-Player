package com.enterprise.iptv.domain.recorder

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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

        val request = Request.Builder().url(streamUrl).build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(RecordState.Error("Server returned code ${response.code}"))
                return@flow
            }

            val body = response.body ?: run {
                emit(RecordState.Error("Stream content is empty"))
                return@flow
            }

            val buffer = ByteArray(64 * 1024) // 64KB buffer
            var totalBytes: Long = 0
            val startTime = System.currentTimeMillis()
            var lastUpdate = 0L

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

            emit(RecordState.Completed(outputFile))
        } catch (e: Exception) {
            emit(RecordState.Error(e.localizedMessage ?: "Unknown recording error"))
        }
    }.flowOn(Dispatchers.IO)
}
