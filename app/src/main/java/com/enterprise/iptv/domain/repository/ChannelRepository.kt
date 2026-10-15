package com.enterprise.iptv.domain.repository

import com.enterprise.iptv.domain.model.Channel
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    fun getFilteredChannels(
        category: String?,
        country: String?,
        language: String?,
        query: String?
    ): Flow<List<Channel>>

    fun getAllCategories(): Flow<List<String>>
    fun getAllCountries(): Flow<List<String>>
    fun getAllLanguages(): Flow<List<String>>

    suspend fun toggleFavorite(channelId: String, isFav: Boolean)
    suspend fun importM3uFromUrl(playlistUrl: String, playlistId: String): Result<Int>
    suspend fun getChannelsCount(): Int

    suspend fun initializeDefaultChannels(): Boolean
    suspend fun syncRemoteChannels(): Result<Boolean>
}
