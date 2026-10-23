package com.enterprise.iptv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enterprise.iptv.data.local.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("""
        SELECT * FROM channels 
        WHERE (:category IS NULL OR groupTitle = :category)
          AND (:country IS NULL OR countryCode = :country)
          AND (:language IS NULL OR languageCode = :language)
          AND (:searchQuery IS NULL OR name LIKE '%' || :searchQuery || '%')
        ORDER BY isFavorite DESC, name ASC
    """)
    fun getFilteredChannels(
        category: String?,
        country: String?,
        language: String?,
        searchQuery: String?
    ): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE groupTitle IS NOT NULL AND groupTitle != '' ORDER BY groupTitle ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT DISTINCT countryCode FROM channels WHERE countryCode IS NOT NULL AND countryCode != '' ORDER BY countryCode ASC")
    fun getAllCountries(): Flow<List<String>>

    @Query("SELECT DISTINCT languageCode FROM channels WHERE languageCode IS NOT NULL AND languageCode != '' ORDER BY languageCode ASC")
    fun getAllLanguages(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :isFav WHERE id = :channelId")
    suspend fun updateFavorite(channelId: String, isFav: Boolean)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deletePlaylistChannels(playlistId: String)

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun getChannelsCount(): Int

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY name ASC")
    suspend fun getFavoriteChannels(): List<ChannelEntity>

    @Query("SELECT COUNT(*) FROM channels WHERE isFavorite = 1")
    suspend fun getFavoritesCount(): Int

    @Query("UPDATE channels SET isFavorite = :isFav WHERE streamUrl = :streamUrl OR name = :name")
    suspend fun updateFavoriteByStreamOrName(streamUrl: String, name: String, isFav: Boolean): Int
}
