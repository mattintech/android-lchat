package com.mattintech.lchat

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}