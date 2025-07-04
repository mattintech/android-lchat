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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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
    
    // Replace callbacks with Flows
    private val _messageFlow = MutableSharedFlow<Triple<String, String, String>>()
    val messageFlow: SharedFlow<Triple<String, String, String>> = _messageFlow.asSharedFlow()
    
    private val _connectionFlow = MutableSharedFlow<Pair<String, Boolean>>()
    val connectionFlow: SharedFlow<Pair<String, Boolean>> = _connectionFlow.asSharedFlow()
    
    // Keep legacy callbacks for backward compatibility
    private var messageCallback: ((String, String, String) -> Unit)? = null
    private var connectionCallback: ((String, Boolean) -> Unit)? = null
    
    // Exception handler for coroutine errors
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception: ", throwable)
    }
    
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    
    fun initialize() {
        coroutineScope.launch {
            initializeAsync()
        }
    }
    
    suspend fun initializeAsync(): Result<Unit> = withContext(Dispatchers.IO) {
        wifiAwareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? android.net.wifi.aware.WifiAwareManager
        
        if (wifiAwareManager?.isAvailable == true) {
            attachToWifiAware()
        } else {
            Result.failure(Exception("Wi-Fi Aware is not available"))
        }
    }
    
    private suspend fun attachToWifiAware(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        wifiAwareManager?.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                Log.d(TAG, "Wi-Fi Aware attached")
                wifiAwareSession = session
                continuation.resume(Result.success(Unit))
            }
            
            override fun onAttachFailed() {
                Log.e(TAG, "Wi-Fi Aware attach failed")
                continuation.resume(Result.failure(Exception("Wi-Fi Aware attach failed")))
            }
        }, null)
        
        continuation.invokeOnCancellation {
            Log.d(TAG, "Wi-Fi Aware attach cancelled")
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
                    Log.d(TAG, "Host: Received connection request from peer")
                    coroutineScope.launch {
                        val result = acceptConnectionAsync(peerHandle)
                        if (result.isSuccess) {
                            Log.d(TAG, "Host: Successfully accepted connection")
                        } else {
                            Log.e(TAG, "Host: Failed to accept connection: ${result.exceptionOrNull()?.message}")
                        }
                    }
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
                coroutineScope.launch {
                    delay(500)
                    val result = connectToPeerAsync(peerHandle, roomName)
                    if (result.isFailure) {
                        Log.e(TAG, "Failed to connect to peer: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
            
            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                handleIncomingMessage(peerHandle, message)
            }
        }, null)
    }
    
    private fun connectToPeer(peerHandle: PeerHandle, roomName: String) {
        coroutineScope.launch {
            connectToPeerAsync(peerHandle, roomName)
        }
    }
    
    private suspend fun connectToPeerAsync(peerHandle: PeerHandle, roomName: String): Result<Unit> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
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
                var isResumed = false
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        Log.d(TAG, "onAvailable: Network connected for room: $roomName")
                        if (!isResumed) {
                            isResumed = true
                            continuation.resume(Result.success(Unit))
                        }
                        // Emit to flow and legacy callback
                        coroutineScope.launch {
                            _connectionFlow.emit(Pair(roomName, true))
                        }
                        connectionCallback?.invoke(roomName, true)
                    }
                    
                    override fun onLost(network: android.net.Network) {
                        Log.d(TAG, "onLost: Network lost for room: $roomName")
                        coroutineScope.launch {
                            _connectionFlow.emit(Pair(roomName, false))
                        }
                        connectionCallback?.invoke(roomName, false)
                    }
                    
                    override fun onUnavailable() {
                        Log.e(TAG, "onUnavailable: Network request failed for room: $roomName")
                        if (!isResumed) {
                            isResumed = true
                            continuation.resume(Result.failure(Exception("Network unavailable for room: $roomName")))
                        }
                        coroutineScope.launch {
                            _connectionFlow.emit(Pair(roomName, false))
                        }
                        connectionCallback?.invoke(roomName, false)
                    }
                    
                    override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: NetworkCapabilities) {
                        Log.d(TAG, "onCapabilitiesChanged: Capabilities changed for room: $roomName")
                    }
                    
                    override fun onLinkPropertiesChanged(network: android.net.Network, linkProperties: android.net.LinkProperties) {
                        Log.d(TAG, "onLinkPropertiesChanged: Link properties changed for room: $roomName")
                    }
                }
                
                connectivityManager.requestNetwork(networkRequest, callback, android.os.Handler(android.os.Looper.getMainLooper()))
                
                Log.d(TAG, "connectToPeer: Network request submitted for room: $roomName")
                
                continuation.invokeOnCancellation {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            } catch (e: Exception) {
                Log.e(TAG, "connectToPeer: Failed to request network", e)
                continuation.resume(Result.failure(e))
                coroutineScope.launch {
                    _connectionFlow.emit(Pair(roomName, false))
                }
                connectionCallback?.invoke(roomName, false)
            }
        }
    }
    
    
    private suspend fun acceptConnectionAsync(peerHandle: PeerHandle): Result<Unit> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "acceptConnection: Accepting connection from client")
            
            try {
                val networkSpecifier = WifiAwareNetworkSpecifier.Builder(publishDiscoverySession!!, peerHandle)
                    .setPskPassphrase("lchat-secure-key")
                    .setPort(PORT)
                    .build()
                
                val networkRequest = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                    .setNetworkSpecifier(networkSpecifier)
                    .build()
                
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                var isResumed = false
                
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        Log.d(TAG, "Client connected")
                        peerHandles[peerHandle.toString()] = peerHandle
                        if (!isResumed) {
                            isResumed = true
                            continuation.resume(Result.success(Unit))
                        }
                    }
                    
                    override fun onUnavailable() {
                        Log.e(TAG, "Failed to accept client connection - Check if Wi-Fi is enabled")
                        if (!isResumed) {
                            isResumed = true
                            continuation.resume(Result.failure(Exception("Failed to accept client connection")))
                        }
                    }
                }
                
                connectivityManager.requestNetwork(networkRequest, callback)
                
                continuation.invokeOnCancellation {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            } catch (e: Exception) {
                Log.e(TAG, "acceptConnection: Failed to accept connection", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    private fun handleIncomingMessage(peerHandle: PeerHandle, message: ByteArray) {
        try {
            val messageStr = String(message)
            val parts = messageStr.split("|", limit = 3)
            if (parts.size == 3) {
                // Emit to flow and legacy callback
                coroutineScope.launch {
                    _messageFlow.emit(Triple(parts[0], parts[1], parts[2]))
                }
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
        coroutineScope.cancel()
    }
}