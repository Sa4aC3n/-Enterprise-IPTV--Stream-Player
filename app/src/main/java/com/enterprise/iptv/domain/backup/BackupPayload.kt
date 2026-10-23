package com.enterprise.iptv.domain.backup

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * عنصر القناة في النسخة الاحتياطية (Channel Backup Item)
 */
data class ChannelBackupItem(
    val name: String,
    val streamUrl: String,
    val backupUrls: List<String> = emptyList(),
    val logoUrl: String? = null,
    val groupTitle: String = "عام",
    val countryCode: String? = null,
    val isFavorite: Boolean = true
)

/**
 * إعدادات التطبيق المحفوظة بالنسخة الاحتياطية
 */
data class AppSettingsBackup(
    val isKidsModeEnabled: Boolean = false,
    val kidsPin: String = "0000",
    val defaultAspectMode: Int = 0,
    val defaultPlaybackSpeed: Float = 1.0f,
    val isAudioOnlyDefault: Boolean = false
)

/**
 * هيكل بيانات ملف النسخة الاحتياطية الشامل (Backup Payload)
 * يدعم التشفير الذاتي والتحويل إلى صيغة JSON معيارية متوافقة
 */
data class BackupPayload(
    val version: Int = 1,
    val appVersion: String = "1.10.0",
    val timestamp: Long = System.currentTimeMillis(),
    val formattedDate: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
    val favorites: List<ChannelBackupItem> = emptyList(),
    val customChannels: List<ChannelBackupItem> = emptyList(),
    val settings: AppSettingsBackup = AppSettingsBackup()
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("version", version)
        root.put("appVersion", appVersion)
        root.put("timestamp", timestamp)
        root.put("formattedDate", formattedDate)

        // القنوات المفضلة
        val favArray = JSONArray()
        favorites.forEach { fav ->
            val obj = JSONObject()
            obj.put("name", fav.name)
            obj.put("streamUrl", fav.streamUrl)
            if (fav.backupUrls.isNotEmpty()) {
                val bArr = JSONArray()
                fav.backupUrls.forEach { bArr.put(it) }
                obj.put("backupUrls", bArr)
            }
            obj.put("logoUrl", fav.logoUrl ?: "")
            obj.put("groupTitle", fav.groupTitle)
            obj.put("countryCode", fav.countryCode ?: "")
            obj.put("isFavorite", fav.isFavorite)
            favArray.put(obj)
        }
        root.put("favorites", favArray)

        // القنوات المخصصة الإضافية إن وجدت
        val customArray = JSONArray()
        customChannels.forEach { ch ->
            val obj = JSONObject()
            obj.put("name", ch.name)
            obj.put("streamUrl", ch.streamUrl)
            obj.put("logoUrl", ch.logoUrl ?: "")
            obj.put("groupTitle", ch.groupTitle)
            customArray.put(obj)
        }
        root.put("customChannels", customArray)

        // إعدادات التطبيق
        val setObj = JSONObject()
        setObj.put("isKidsModeEnabled", settings.isKidsModeEnabled)
        setObj.put("kidsPin", settings.kidsPin)
        setObj.put("defaultAspectMode", settings.defaultAspectMode)
        setObj.put("defaultPlaybackSpeed", settings.defaultPlaybackSpeed.toDouble())
        setObj.put("isAudioOnlyDefault", settings.isAudioOnlyDefault)
        root.put("settings", setObj)

        return root.toString(2)
    }

    companion object {
        fun fromJsonString(jsonStr: String): BackupPayload {
            val root = JSONObject(jsonStr)
            val version = root.optInt("version", 1)
            val appVersion = root.optString("appVersion", "1.0.0")
            val timestamp = root.optLong("timestamp", System.currentTimeMillis())
            val formattedDate = root.optString("formattedDate", "")

            val favList = mutableListOf<ChannelBackupItem>()
            val favArray = root.optJSONArray("favorites")
            if (favArray != null) {
                for (i in 0 until favArray.length()) {
                    val obj = favArray.getJSONObject(i)
                    val backupUrls = mutableListOf<String>()
                    val bArr = obj.optJSONArray("backupUrls")
                    if (bArr != null) {
                        for (j in 0 until bArr.length()) {
                            backupUrls.add(bArr.getString(j))
                        }
                    }
                    favList.add(
                        ChannelBackupItem(
                            name = obj.getString("name"),
                            streamUrl = obj.getString("streamUrl"),
                            backupUrls = backupUrls,
                            logoUrl = obj.optString("logoUrl").ifBlank { null },
                            groupTitle = obj.optString("groupTitle", "عام"),
                            countryCode = obj.optString("countryCode").ifBlank { null },
                            isFavorite = obj.optBoolean("isFavorite", true)
                        )
                    )
                }
            }

            val customList = mutableListOf<ChannelBackupItem>()
            val customArray = root.optJSONArray("customChannels")
            if (customArray != null) {
                for (i in 0 until customArray.length()) {
                    val obj = customArray.getJSONObject(i)
                    customList.add(
                        ChannelBackupItem(
                            name = obj.getString("name"),
                            streamUrl = obj.getString("streamUrl"),
                            logoUrl = obj.optString("logoUrl").ifBlank { null },
                            groupTitle = obj.optString("groupTitle", "عام"),
                            isFavorite = false
                        )
                    )
                }
            }

            val setObj = root.optJSONObject("settings")
            val settings = if (setObj != null) {
                AppSettingsBackup(
                    isKidsModeEnabled = setObj.optBoolean("isKidsModeEnabled", false),
                    kidsPin = setObj.optString("kidsPin", "0000"),
                    defaultAspectMode = setObj.optInt("defaultAspectMode", 0),
                    defaultPlaybackSpeed = setObj.optDouble("defaultPlaybackSpeed", 1.0).toFloat(),
                    isAudioOnlyDefault = setObj.optBoolean("isAudioOnlyDefault", false)
                )
            } else {
                AppSettingsBackup()
            }

            return BackupPayload(
                version = version,
                appVersion = appVersion,
                timestamp = timestamp,
                formattedDate = formattedDate,
                favorites = favList,
                customChannels = customList,
                settings = settings
            )
        }
    }
}
