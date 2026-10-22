package com.enterprise.iptv.data.repository

import com.enterprise.iptv.data.local.dao.ChannelDao
import com.enterprise.iptv.data.local.entity.ChannelEntity
import com.enterprise.iptv.data.sync.GitHubSyncManager
import com.enterprise.iptv.domain.model.Channel
import com.enterprise.iptv.domain.repository.ChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: ChannelDao,
    private val okHttpClient: OkHttpClient,
    private val gitHubSyncManager: GitHubSyncManager
) : ChannelRepository {

    override fun getFilteredChannels(
        category: String?,
        country: String?,
        language: String?,
        query: String?
    ): Flow<List<Channel>> {
        return channelDao.getFilteredChannels(category, country, language, query).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getAllCategories(): Flow<List<String>> = channelDao.getAllCategories()
    override fun getAllCountries(): Flow<List<String>> = channelDao.getAllCountries()
    override fun getAllLanguages(): Flow<List<String>> = channelDao.getAllLanguages()

    override suspend fun toggleFavorite(channelId: String, isFav: Boolean) {
        channelDao.updateFavorite(channelId, isFav)
    }

    override suspend fun getChannelsCount(): Int = channelDao.getChannelsCount()

    override suspend fun initializeDefaultChannels(): Boolean {
        return gitHubSyncManager.seedFromAssetsIfEmpty()
    }

    override suspend fun syncRemoteChannels(): Result<Boolean> {
        return gitHubSyncManager.checkAndApplyRemoteUpdate()
    }

    override suspend fun importM3uFromUrl(playlistUrl: String, playlistId: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(playlistUrl).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error: ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
                val total = parseAndInsertM3u(BufferedReader(InputStreamReader(body.byteStream())), playlistId)
                Result.success(total)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun importM3uFromInputStream(inputStream: java.io.InputStream, playlistId: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val total = parseAndInsertM3u(BufferedReader(InputStreamReader(inputStream)), playlistId)
                Result.success(total)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun parseAndInsertM3u(reader: BufferedReader, playlistId: String): Int {
        val regexGroup = """group-title="([^"]*)"""".toRegex()
        val regexLogo = """tvg-logo="([^"]*)"""".toRegex()
        val regexCountry = """tvg-country="([^"]*)"""".toRegex()
        val regexLanguage = """tvg-language="([^"]*)"""".toRegex()

        val buffer = mutableListOf<ChannelEntity>()
        var totalInserted = 0

        var currentName = ""
        var currentLogo: String? = null
        var currentGroup = "عام"
        var currentCountry: String? = null
        var currentLanguage: String? = null

        reader.use { r ->
            var line: String?
            while (r.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.startsWith("#EXTINF:")) {
                    currentGroup = regexGroup.find(trimmed)?.groupValues?.get(1) ?: "عام"
                    currentLogo = regexLogo.find(trimmed)?.groupValues?.get(1)
                    currentCountry = regexCountry.find(trimmed)?.groupValues?.get(1)
                    currentLanguage = regexLanguage.find(trimmed)?.groupValues?.get(1)
                    currentName = trimmed.substringAfterLast(",").trim()
                } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                    val streamUrl = trimmed
                    buffer.add(
                        ChannelEntity(
                            id = UUID.randomUUID().toString(),
                            name = currentName.ifBlank { "Channel" },
                            streamUrl = streamUrl,
                            logoUrl = currentLogo,
                            groupTitle = currentGroup,
                            countryCode = currentCountry,
                            languageCode = currentLanguage,
                            isFavorite = false,
                            playlistId = playlistId
                        )
                    )

                    if (buffer.size >= 100) {
                        channelDao.insertChannels(buffer)
                        totalInserted += buffer.size
                        buffer.clear()
                    }
                    currentName = ""
                    currentLogo = null
                    currentGroup = "عام"
                    currentCountry = null
                    currentLanguage = null
                }
            }
            if (buffer.isNotEmpty()) {
                channelDao.insertChannels(buffer)
                totalInserted += buffer.size
                buffer.clear()
            }
        }
        return totalInserted
    }
}
