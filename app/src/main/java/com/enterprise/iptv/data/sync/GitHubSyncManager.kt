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
        const val DEFAULT_REMOTE_CONFIG_URL =
            "https://raw.githubusercontent.com/Sa4aC3n/iptv-player-app/main/app/src/main/assets/default_channels.json"
        const val DEFAULT_PLAYLIST_ID = "official_default"
    }

    /**
     * تشغيل فوري من الـ Assets عند أول فتح للتطبيق أو عند توفر إصدار أحدث محلياً
     */
    suspend fun seedFromAssetsIfEmpty(): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open("default_channels.json")
            val jsonString = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            val json = JSONObject(jsonString)
            val version = json.optInt("version", 1)
            val count = channelDao.getChannelsCount()

            if (count > 0 && syncPreferenceManager.localChannelsVersion >= version) {
                return@withContext false // البيانات محملة مسبقاً ومطابقة للإصدار
            }

            val channelsArray = json.getJSONArray("channels")
            val existingFavorites = channelDao.getFavoriteChannels()
            val favIdSet = existingFavorites.map { it.id }.toSet()
            val favUrlSet = existingFavorites.map { it.streamUrl }.toSet()
            val favNameSet = existingFavorites.map { it.name }.toSet()
            val channelEntities = mutableListOf<ChannelEntity>()

            for (i in 0 until channelsArray.length()) {
                val obj = channelsArray.getJSONObject(i)
                val backupUrlsArray = obj.optJSONArray("backup_urls")
                val backupUrls = if (backupUrlsArray != null && backupUrlsArray.length() > 0) {
                    (0 until backupUrlsArray.length()).map { backupUrlsArray.getString(it) }.joinToString("||")
                } else null

                val channelId = obj.getString("id")
                val channelName = obj.getString("name")
                val streamUrl = obj.getString("stream_url")
                val isFav = favIdSet.contains(channelId) || favUrlSet.contains(streamUrl) || favNameSet.contains(channelName)

                channelEntities.add(
                    ChannelEntity(
                        id = channelId,
                        name = channelName,
                        streamUrl = streamUrl,
                        backupUrls = backupUrls,
                        logoUrl = obj.optString("logo_url", null),
                        groupTitle = obj.optString("category", "عام"),
                        countryCode = obj.optString("country", null),
                        languageCode = obj.optString("language", "ar"),
                        isFavorite = isFav,
                        playlistId = DEFAULT_PLAYLIST_ID
                    )
                )
            }

            channelDao.deletePlaylistChannels(DEFAULT_PLAYLIST_ID)
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
                val existingFavorites = channelDao.getFavoriteChannels()
                val favIdSet = existingFavorites.map { it.id }.toSet()
                val favUrlSet = existingFavorites.map { it.streamUrl }.toSet()
                val favNameSet = existingFavorites.map { it.name }.toSet()
                val updatedEntities = mutableListOf<ChannelEntity>()

                for (i in 0 until channelsArray.length()) {
                    val obj = channelsArray.getJSONObject(i)
                    val backupUrlsArray = obj.optJSONArray("backup_urls")
                    val backupUrls = if (backupUrlsArray != null && backupUrlsArray.length() > 0) {
                        (0 until backupUrlsArray.length()).map { backupUrlsArray.getString(it) }.joinToString("||")
                    } else null

                    val channelId = obj.getString("id")
                    val channelName = obj.getString("name")
                    val streamUrl = obj.getString("stream_url")
                    val isFav = favIdSet.contains(channelId) || favUrlSet.contains(streamUrl) || favNameSet.contains(channelName)

                    updatedEntities.add(
                        ChannelEntity(
                            id = channelId,
                            name = channelName,
                            streamUrl = streamUrl,
                            backupUrls = backupUrls,
                            logoUrl = obj.optString("logo_url", null),
                            groupTitle = obj.optString("category", "عام"),
                            countryCode = obj.optString("country", null),
                            languageCode = obj.optString("language", "ar"),
                            isFavorite = isFav,
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
