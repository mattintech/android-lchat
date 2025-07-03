package com.mattintech.lchat.network

import android.content.Context

object WifiAwareManagerSingleton {
    private var instance: WifiAwareManager? = null
    
    fun getInstance(context: Context): WifiAwareManager {
        if (instance == null) {
            instance = WifiAwareManager(context.applicationContext)
            instance!!.initialize()
        }
        return instance!!
    }
    
    fun reset() {
        instance?.stop()
        instance = null
    }
}