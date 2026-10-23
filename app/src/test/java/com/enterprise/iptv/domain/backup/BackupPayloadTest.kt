package com.enterprise.iptv.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPayloadTest {

    @Test
    fun testSerializationAndDeserialization() {
        val payload = BackupPayload(
            version = 1,
            appVersion = "1.10.0",
            favorites = listOf(
                ChannelBackupItem(
                    name = "قناة الأخبار",
                    streamUrl = "http://example.com/live.m3u8",
                    backupUrls = listOf("http://backup1.com/live.m3u8", "http://backup2.com/live.m3u8"),
                    logoUrl = "http://example.com/logo.png",
                    groupTitle = "أخبار",
                    countryCode = "EG",
                    isFavorite = true
                )
            ),
            settings = AppSettingsBackup(
                isKidsModeEnabled = true,
                kidsPin = "1234",
                defaultPlaybackSpeed = 1.25f
            )
        )

        val json = payload.toJsonString()
        assertTrue(json.contains("قناة الأخبار"))
        assertTrue(json.contains("http://example.com/live.m3u8"))
        assertTrue(json.contains("1234"))

        val restored = BackupPayload.fromJsonString(json)
        assertEquals(payload.version, restored.version)
        assertEquals(1, restored.favorites.size)
        assertEquals("قناة الأخبار", restored.favorites[0].name)
        assertEquals("http://example.com/live.m3u8", restored.favorites[0].streamUrl)
        assertEquals(2, restored.favorites[0].backupUrls.size)
        assertEquals("أخبار", restored.favorites[0].groupTitle)
        assertTrue(restored.favorites[0].isFavorite)
        assertTrue(restored.settings.isKidsModeEnabled)
        assertEquals("1234", restored.settings.kidsPin)
    }
}
