package com.mattintech.lchat.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.mattintech.lchat.utils.LOG_PREFIX
import java.util.concurrent.ConcurrentHashMap

@RequiresApi(Build.VERSION_CODES.O)
class WifiAwareManager(private val context: Context) {
    
    companion object {
        private const val TAG = LOG_PREFIX + "WifiAwareManager:"
        private const val SERVICE_NAME = "lchat"
        private const val PORT = 8888
    }
    
    private var wifiAwareManager: android.net.wifi.aware.WifiAwareManager? = null
    private var wifiAwareSession: WifiAwareSession? = null
    private var publishDiscoverySession: PublishDiscoverySession? = null
    private var subscribeDiscoverySession: SubscribeDiscoverySession? = null
    
    private val peerHandles = ConcurrentHashMap<String, PeerHandle>()
    private var messageCallback: ((String, String, String) -> Unit)? = null
    private var connectionCallback: ((String, Boolean) -> Unit)? = null
    
    private val attachCallback = object : AttachCallback() {
        override fun onAttached(session: WifiAwareSession) {
            Log.d(TAG, "Wi-Fi Aware attached")
            wifiAwareSession = session
        }
        
        override fun onAttachFailed() {
            Log.e(TAG, "Wi-Fi Aware attach failed")
        }
    }
    
    fun initialize() {
        wifiAwareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? android.net.wifi.aware.WifiAwareManager
        
        if (wifiAwareManager?.isAvailable == true) {
            wifiAwareManager?.attach(attachCallback, null)
        } else {
            Log.e(TAG, "Wi-Fi Aware is not available")
        }
    }
    
    fun startHostMode(roomName: String) {
        val config = PublishConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .setServiceSpecificInfo(roomName.toByteArray())
            .build()
            
        wifiAwareSession?.publish(config, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                Log.d(TAG, "Publish started for room: $roomName")
                publishDiscoverySession = session
            }
            
            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                val messageStr = String(message)
                Log.d(TAG, "Host: Received message: $messageStr")
                
                if (messageStr == "CONNECT_REQUEST") {
                    Log.d(TAG, "Host: Received connection request")
                    acceptConnection(peerHandle)
                } else {
                    handleIncomingMessage(peerHandle, message)
                }
            }
        }, null)
    }
    
    fun startClientMode() {
        val config = SubscribeConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .build()
            
        wifiAwareSession?.subscribe(config, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                Log.d(TAG, "Subscribe started")
                subscribeDiscoverySession = session
            }
            
            override fun onServiceDiscovered(
                peerHandle: PeerHandle,
                serviceSpecificInfo: ByteArray?,
                matchFilter: List<ByteArray>?
            ) {
                val roomName = serviceSpecificInfo?.let { String(it) } ?: "Unknown"
                Log.d(TAG, "Discovered room: $roomName")
                
                // Store peer handle for this room
                peerHandles[roomName] = peerHandle
                
                // Send connection request to host
                Log.d(TAG, "Sending connection request to room: $roomName")
                subscribeDiscoverySession?.sendMessage(peerHandle, 0, "CONNECT_REQUEST".toByteArray())
                
                // Wait a bit for host to prepare, then connect
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    connectToPeer(peerHandle, roomName)
                }, 500)
            }
            
            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                handleIncomingMessage(peerHandle, message)
            }
        }, null)
    }
    
    private fun connectToPeer(peerHandle: PeerHandle, roomName: String) {
        Log.d(TAG, "connectToPeer: Starting connection to room: $roomName")
        val networkSpecifier = WifiAwareNetworkSpecifier.Builder(subscribeDiscoverySession!!, peerHandle)
            .setPskPassphrase("lchat-secure-key")
            .build()
            
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()
            
        Log.d(TAG, "connectToPeer: Network request created for room: $roomName")
            
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        try {
            connectivityManager.requestNetwork(networkRequest, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    Log.d(TAG, "onAvailable: Network connected for room: $roomName")
                    connectionCallback?.invoke(roomName, true)
                }
                
                override fun onLost(network: android.net.Network) {
                    Log.d(TAG, "onLost: Network lost for room: $roomName")
                    connectionCallback?.invoke(roomName, false)
                }
                
                override fun onUnavailable() {
                    Log.e(TAG, "onUnavailable: Network request failed for room: $roomName")
                    connectionCallback?.invoke(roomName, false)
                }
                
                override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: NetworkCapabilities) {
                    Log.d(TAG, "onCapabilitiesChanged: Capabilities changed for room: $roomName")
                }
                
                override fun onLinkPropertiesChanged(network: android.net.Network, linkProperties: android.net.LinkProperties) {
                    Log.d(TAG, "onLinkPropertiesChanged: Link properties changed for room: $roomName")
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()), 30000) // 30 second timeout
            
            Log.d(TAG, "connectToPeer: Network request submitted for room: $roomName")
        } catch (e: Exception) {
            Log.e(TAG, "connectToPeer: Failed to request network", e)
            connectionCallback?.invoke(roomName, false)
        }
    }
    
    private fun acceptConnection(peerHandle: PeerHandle) {
        Log.d(TAG, "acceptConnection: Accepting connection from client")
        val networkSpecifier = WifiAwareNetworkSpecifier.Builder(publishDiscoverySession!!, peerHandle)
            .setPskPassphrase("lchat-secure-key")
            .setPort(PORT)
            .build()
            
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()
            
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.requestNetwork(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                Log.d(TAG, "Client connected")
                peerHandles[peerHandle.toString()] = peerHandle
            }
            
            override fun onUnavailable() {
                Log.e(TAG, "Failed to accept client connection - Check if Wi-Fi is enabled")
            }
        })
    }
    
    private fun handleIncomingMessage(peerHandle: PeerHandle, message: ByteArray) {
        try {
            val messageStr = String(message)
            val parts = messageStr.split("|", limit = 3)
            if (parts.size == 3) {
                messageCallback?.invoke(parts[0], parts[1], parts[2])
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message", e)
        }
    }
    
    fun sendMessage(userId: String, userName: String, content: String) {
        val message = "$userId|$userName|$content".toByteArray()
        
        if (publishDiscoverySession != null) {
            peerHandles.values.forEach { peerHandle ->
                publishDiscoverySession?.sendMessage(peerHandle, 0, message)
            }
        } else if (subscribeDiscoverySession != null) {
            peerHandles.values.forEach { peerHandle ->
                subscribeDiscoverySession?.sendMessage(peerHandle, 0, message)
            }
        }
    }
    
    fun setMessageCallback(callback: (String, String, String) -> Unit) {
        messageCallback = callback
    }
    
    fun setConnectionCallback(callback: (String, Boolean) -> Unit) {
        connectionCallback = callback
    }
    
    fun stop() {
        publishDiscoverySession?.close()
        subscribeDiscoverySession?.close()
        wifiAwareSession?.close()
        peerHandles.clear()
    }
}