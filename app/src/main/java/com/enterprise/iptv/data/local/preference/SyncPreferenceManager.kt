package com.enterprise.iptv.data.local.preference

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("iptv_sync_prefs", Context.MODE_PRIVATE)

    var localChannelsVersion: Int
        get() = prefs.getInt(KEY_CHANNELS_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_CHANNELS_VERSION, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIME, value).apply()

    companion object {
        private const val KEY_CHANNELS_VERSION = "key_channels_version"
        private const val KEY_LAST_SYNC_TIME = "key_last_sync_time"
    }
}
