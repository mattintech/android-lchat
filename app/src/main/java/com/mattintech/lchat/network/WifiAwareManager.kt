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
        
        // Keep-alive constants
        private const val KEEP_ALIVE_INTERVAL_MS = 15000L // Send keep-alive every 15 seconds
        private const val KEEP_ALIVE_TIMEOUT_MS = 30000L // Consider connection lost after 30 seconds
        private const val MESSAGE_TYPE_KEEP_ALIVE = "KEEP_ALIVE"
        private const val MESSAGE_TYPE_KEEP_ALIVE_ACK = "KEEP_ALIVE_ACK"
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
    
    // Keep-alive tracking
    private val lastPeerActivity = ConcurrentHashMap<String, Long>()
    private var keepAliveJob: Job? = null
    
    fun initialize() {
        coroutineScope.launch {
            val result = initializeAsync()
            if (result.isFailure) {
                Log.e(TAG, "Failed to initialize Wi-Fi Aware: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    
    suspend fun initializeAsync(): Result<Unit> = withContext(Dispatchers.IO) {
        wifiAwareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? android.net.wifi.aware.WifiAwareManager
        
        // Always check if Wi-Fi Aware is available
        if (wifiAwareManager?.isAvailable != true) {
            Log.e(TAG, "Wi-Fi Aware is not available")
            wifiAwareSession = null
            return@withContext Result.failure(Exception("Wi-Fi Aware is not available"))
        }
        
        // If we already have a session, verify it's still valid
        if (wifiAwareSession != null) {
            Log.d(TAG, "Wi-Fi Aware already initialized - verifying session is still valid")
            // Session is likely still valid
            return@withContext Result.success(Unit)
        }
        
        // Need to attach
        attachToWifiAware()
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
                
                when (messageStr) {
                    "CONNECT_REQUEST" -> {
                        Log.d(TAG, "Host: Received connection request from peer")
                        coroutineScope.launch {
                            val result = acceptConnectionAsync(peerHandle)
                            if (result.isSuccess) {
                                Log.d(TAG, "Host: Successfully accepted connection")
                                startKeepAlive()
                            } else {
                                Log.e(TAG, "Host: Failed to accept connection: ${result.exceptionOrNull()?.message}")
                            }
                        }
                    }
                    MESSAGE_TYPE_KEEP_ALIVE -> {
                        Log.d(TAG, "Host: Received keep-alive from peer")
                        handleKeepAlive(peerHandle, true)
                    }
                    MESSAGE_TYPE_KEEP_ALIVE_ACK -> {
                        Log.d(TAG, "Host: Received keep-alive ACK from peer")
                        handleKeepAlive(peerHandle, false)
                    }
                    else -> {
                        handleIncomingMessage(peerHandle, message)
                    }
                }
            }
            
            override fun onSessionTerminated() {
                Log.w(TAG, "Host publish session terminated")
                publishDiscoverySession = null
                stopKeepAlive()
                // Emit disconnection event
                coroutineScope.launch {
                    _connectionFlow.emit(Pair("", false))
                }
            }
        }, null)
    }
    
    fun startClientMode() {
        // Close any existing subscribe session before starting a new one
        if (subscribeDiscoverySession != null) {
            Log.d(TAG, "Closing existing subscribe session before starting new one")
            subscribeDiscoverySession?.close()
            subscribeDiscoverySession = null
        }
        
        // Clear any stale peer handles
        peerHandles.clear()
        
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
                
                // Update peer activity when discovered
                val peerId = peerHandle.toString()
                lastPeerActivity[peerId] = System.currentTimeMillis()
                
                // Wait a bit for host to prepare, then connect
                coroutineScope.launch {
                    delay(500)
                    val result = connectToPeerAsync(peerHandle, roomName)
                    if (result.isFailure) {
                        Log.e(TAG, "Failed to connect to peer: ${result.exceptionOrNull()?.message}")
                        lastPeerActivity.remove(peerId)
                    }
                }
            }
            
            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                val messageStr = String(message)
                when (messageStr) {
                    MESSAGE_TYPE_KEEP_ALIVE -> {
                        Log.d(TAG, "Client: Received keep-alive from host")
                        handleKeepAlive(peerHandle, true)
                    }
                    MESSAGE_TYPE_KEEP_ALIVE_ACK -> {
                        Log.d(TAG, "Client: Received keep-alive ACK from host")
                        handleKeepAlive(peerHandle, false)
                    }
                    else -> {
                        handleIncomingMessage(peerHandle, message)
                    }
                }
            }
            
            override fun onSessionTerminated() {
                Log.w(TAG, "Client subscribe session terminated")
                subscribeDiscoverySession = null
                stopKeepAlive()
                // Don't clear wifiAwareSession - it's still valid
                // Emit disconnection event
                coroutineScope.launch {
                    _connectionFlow.emit(Pair("", false))
                }
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
                        // Start keep-alive for client connections
                        startKeepAlive()
                    }
                    
                    override fun onLost(network: android.net.Network) {
                        Log.d(TAG, "onLost: Network lost for room: $roomName")
                        // Clear peer handles when connection is lost
                        peerHandles.remove(roomName)
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
                        val peerId = peerHandle.toString()
                        peerHandles[peerId] = peerHandle
                        lastPeerActivity[peerId] = System.currentTimeMillis()
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
            // Update peer activity on any message
            val peerId = peerHandle.toString()
            lastPeerActivity[peerId] = System.currentTimeMillis()
            
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
    
    fun sendMessage(userId: String, userName: String, content: String): Boolean {
        val message = "$userId|$userName|$content".toByteArray()
        var messagesSent = 0
        
        if (publishDiscoverySession != null && peerHandles.isNotEmpty()) {
            peerHandles.values.forEach { peerHandle ->
                try {
                    publishDiscoverySession?.sendMessage(peerHandle, messagesSent, message)
                    messagesSent++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send message to peer", e)
                }
            }
        } else if (subscribeDiscoverySession != null && peerHandles.isNotEmpty()) {
            peerHandles.values.forEach { peerHandle ->
                try {
                    subscribeDiscoverySession?.sendMessage(peerHandle, messagesSent, message)
                    messagesSent++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send message to peer", e)
                }
            }
        }
        
        if (messagesSent == 0) {
            Log.w(TAG, "No messages sent - no active session or no peer handles")
        }
        
        return messagesSent > 0
    }
    
    fun setMessageCallback(callback: (String, String, String) -> Unit) {
        messageCallback = callback
    }
    
    fun setConnectionCallback(callback: (String, Boolean) -> Unit) {
        connectionCallback = callback
    }
    
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = coroutineScope.launch {
            while (isActive) {
                delay(KEEP_ALIVE_INTERVAL_MS)
                sendKeepAlive()
                checkPeerActivity()
            }
        }
    }
    
    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
        lastPeerActivity.clear()
    }
    
    private fun sendKeepAlive() {
        val message = MESSAGE_TYPE_KEEP_ALIVE.toByteArray()
        var messagesSent = 0
        
        if (publishDiscoverySession != null && peerHandles.isNotEmpty()) {
            peerHandles.forEach { (peerId, peerHandle) ->
                try {
                    publishDiscoverySession?.sendMessage(peerHandle, messagesSent, message)
                    messagesSent++
                    Log.d(TAG, "Sent keep-alive to peer: $peerId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send keep-alive to peer: $peerId", e)
                }
            }
        } else if (subscribeDiscoverySession != null && peerHandles.isNotEmpty()) {
            peerHandles.forEach { (peerId, peerHandle) ->
                try {
                    subscribeDiscoverySession?.sendMessage(peerHandle, messagesSent, message)
                    messagesSent++
                    Log.d(TAG, "Sent keep-alive to peer: $peerId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send keep-alive to peer: $peerId", e)
                }
            }
        }
    }
    
    private fun handleKeepAlive(peerHandle: PeerHandle, shouldReply: Boolean) {
        // Update last activity for this peer
        val peerId = peerHandle.toString()
        lastPeerActivity[peerId] = System.currentTimeMillis()
        
        // Send acknowledgment if requested
        if (shouldReply) {
            val ackMessage = MESSAGE_TYPE_KEEP_ALIVE_ACK.toByteArray()
            try {
                if (publishDiscoverySession != null) {
                    publishDiscoverySession?.sendMessage(peerHandle, 0, ackMessage)
                } else if (subscribeDiscoverySession != null) {
                    subscribeDiscoverySession?.sendMessage(peerHandle, 0, ackMessage)
                }
                Log.d(TAG, "Sent keep-alive ACK to peer")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send keep-alive ACK", e)
            }
        }
    }
    
    private fun checkPeerActivity() {
        val currentTime = System.currentTimeMillis()
        val inactivePeers = mutableListOf<String>()
        
        lastPeerActivity.forEach { (peerId, lastActivity) ->
            if (currentTime - lastActivity > KEEP_ALIVE_TIMEOUT_MS) {
                Log.w(TAG, "Peer $peerId inactive for too long, considering disconnected")
                inactivePeers.add(peerId)
            }
        }
        
        // Remove inactive peers
        inactivePeers.forEach { peerId ->
            lastPeerActivity.remove(peerId)
            peerHandles.remove(peerId)
        }
        
        // If all peers are disconnected, emit disconnection event
        if (peerHandles.isEmpty() && lastPeerActivity.isNotEmpty()) {
            coroutineScope.launch {
                _connectionFlow.emit(Pair("", false))
            }
        }
    }
    
    fun stop() {
        Log.d(TAG, "Stopping WifiAwareManager")
        stopKeepAlive()
        publishDiscoverySession?.close()
        publishDiscoverySession = null
        subscribeDiscoverySession?.close()
        subscribeDiscoverySession = null
        // Close and clear the wifiAwareSession to force re-attachment
        wifiAwareSession?.close()
        wifiAwareSession = null
        peerHandles.clear()
        // Don't cancel the coroutine scope - we need it for future operations
        Log.d(TAG, "WifiAwareManager stopped - session cleared for fresh start")
    }
}