package com.enterprise.iptv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.enterprise.iptv.domain.model.Channel

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["groupTitle"]),
        Index(value = ["countryCode"]),
        Index(value = ["languageCode"]),
        Index(value = ["isFavorite"])
    ]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val groupTitle: String,
    val countryCode: String?,
    val languageCode: String?,
    val isFavorite: Boolean = false,
    val playlistId: String = "default"
) {
    fun toDomain(): Channel = Channel(
        id = id,
        name = name,
        streamUrl = streamUrl,
        logoUrl = logoUrl,
        groupTitle = groupTitle,
        countryCode = countryCode,
        languageCode = languageCode,
        isFavorite = isFavorite,
        playlistId = playlistId
    )

    companion object {
        fun fromDomain(channel: Channel): ChannelEntity = ChannelEntity(
            id = channel.id,
            name = channel.name,
            streamUrl = channel.streamUrl,
            logoUrl = channel.logoUrl,
            groupTitle = channel.groupTitle,
            countryCode = channel.countryCode,
            languageCode = channel.languageCode,
            isFavorite = channel.isFavorite,
            playlistId = channel.playlistId
        )
    }
}
