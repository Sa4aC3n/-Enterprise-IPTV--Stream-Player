package com.enterprise.iptv.di

import android.content.Context
import androidx.room.Room
import com.enterprise.iptv.data.local.IptvDatabase
import com.enterprise.iptv.data.local.dao.ChannelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideIptvDatabase(
        @ApplicationContext context: Context
    ): IptvDatabase {
        return Room.databaseBuilder(
            context,
            IptvDatabase::class.java,
            IptvDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideChannelDao(database: IptvDatabase): ChannelDao {
        return database.channelDao()
    }
}
