package com.mattintech.lchat.network

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ChatService : Service() {
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onCreate() {
        super.onCreate()
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }
}