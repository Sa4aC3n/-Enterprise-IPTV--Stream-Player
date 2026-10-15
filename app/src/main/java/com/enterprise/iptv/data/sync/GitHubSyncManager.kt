package com.enterprise.iptv.data.sync

import android.content.Context
import com.enterprise.iptv.data.local.dao.ChannelDao
import com.enterprise.iptv.data.local.entity.ChannelEntity
import com.enterprise.iptv.data.local.preference.SyncPreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: ChannelDao,
    private val syncPreferenceManager: SyncPreferenceManager,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        // ضع هنا رابط ملف الـ JSON الخاص بك على GitHub (raw.githubusercontent.com)
        const val DEFAULT_REMOTE_CONFIG_URL =
            "https://raw.githubusercontent.com/your-username/iptv-config/main/default_channels.json"
        const val DEFAULT_PLAYLIST_ID = "official_default"
    }

    /**
     * تشغيل فوري من الـ Assets عند أول فتح للتطبيق بدون أي اتصال بالإنترنت
     */
    suspend fun seedFromAssetsIfEmpty(): Boolean = withContext(Dispatchers.IO) {
        val count = channelDao.getChannelsCount()
        if (count > 0 && syncPreferenceManager.localChannelsVersion > 0) {
            return@withContext false // البيانات محملة مسبقاً
        }

        try {
            val inputStream = context.assets.open("default_channels.json")
            val jsonString = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            val json = JSONObject(jsonString)
            val version = json.optInt("version", 1)

            val channelsArray = json.getJSONArray("channels")
            val channelEntities = mutableListOf<ChannelEntity>()

            for (i in 0 until channelsArray.length()) {
                val obj = channelsArray.getJSONObject(i)
                channelEntities.add(
                    ChannelEntity(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        streamUrl = obj.getString("stream_url"),
                        logoUrl = obj.optString("logo_url", null),
                        groupTitle = obj.optString("category", "عام"),
                        countryCode = obj.optString("country", null),
                        languageCode = obj.optString("language", "ar"),
                        isFavorite = false,
                        playlistId = DEFAULT_PLAYLIST_ID
                    )
                )
            }

            channelDao.insertChannels(channelEntities)
            syncPreferenceManager.localChannelsVersion = version
            syncPreferenceManager.lastSyncTimestamp = System.currentTimeMillis()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * فحص التحديثات من GitHub في الخلفية
     * يستهلك بضعة بايتات فقط لفحص الـ version
     */
    suspend fun checkAndApplyRemoteUpdate(remoteUrl: String = DEFAULT_REMOTE_CONFIG_URL): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(remoteUrl)
                    .header("Cache-Control", "no-cache")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }

                val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val json = JSONObject(bodyString)
                val remoteVersion = json.getInt("version")

                // لا نقوم بأي تعديل إذا لم يتغير رقم الـ version
                if (remoteVersion <= syncPreferenceManager.localChannelsVersion) {
                    return@withContext Result.success(false)
                }

                val channelsArray = json.getJSONArray("channels")
                val updatedEntities = mutableListOf<ChannelEntity>()

                for (i in 0 until channelsArray.length()) {
                    val obj = channelsArray.getJSONObject(i)
                    updatedEntities.add(
                        ChannelEntity(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            streamUrl = obj.getString("stream_url"),
                            logoUrl = obj.optString("logo_url", null),
                            groupTitle = obj.optString("category", "عام"),
                            countryCode = obj.optString("country", null),
                            languageCode = obj.optString("language", "ar"),
                            isFavorite = false,
                            playlistId = DEFAULT_PLAYLIST_ID
                        )
                    )
                }

                // تحديث قاعدة البيانات وحفظ رقم الإصدار الجديد
                channelDao.deletePlaylistChannels(DEFAULT_PLAYLIST_ID)
                channelDao.insertChannels(updatedEntities)
                syncPreferenceManager.localChannelsVersion = remoteVersion
                syncPreferenceManager.lastSyncTimestamp = System.currentTimeMillis()

                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
