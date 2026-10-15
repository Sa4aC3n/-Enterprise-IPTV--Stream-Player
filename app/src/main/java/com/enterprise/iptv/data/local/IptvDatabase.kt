package com.enterprise.iptv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.enterprise.iptv.data.local.dao.ChannelDao
import com.enterprise.iptv.data.local.entity.ChannelEntity

@Database(
    entities = [ChannelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class IptvDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao

    companion object {
        const val DATABASE_NAME = "enterprise_iptv.db"
    }
}
