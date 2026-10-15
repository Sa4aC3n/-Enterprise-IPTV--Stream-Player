package com.enterprise.iptv.domain.model

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val backupUrls: List<String> = emptyList(),
    val logoUrl: String? = null,
    val groupTitle: String = "General",
    val countryCode: String? = null,
    val languageCode: String? = null,
    val isFavorite: Boolean = false,
    val playlistId: String = "default"
)
