package com.mattintech.lchat.di

import android.content.Context
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
}