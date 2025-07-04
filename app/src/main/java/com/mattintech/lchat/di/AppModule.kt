package com.mattintech.lchat.di

import android.content.Context
import androidx.room.Room
import com.mattintech.lchat.data.db.LChatDatabase
import com.mattintech.lchat.data.db.dao.MessageDao
import com.mattintech.lchat.network.WifiAwareManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideWifiAwareManager(
        @ApplicationContext context: Context
    ): WifiAwareManager {
        return WifiAwareManager(context)
    }
    
    @Provides
    @Singleton
    fun provideLChatDatabase(
        @ApplicationContext context: Context
    ): LChatDatabase {
        return Room.databaseBuilder(
            context,
            LChatDatabase::class.java,
            "lchat_database"
        ).build()
    }
    
    @Provides
    fun provideMessageDao(database: LChatDatabase): MessageDao {
        return database.messageDao()
    }
}