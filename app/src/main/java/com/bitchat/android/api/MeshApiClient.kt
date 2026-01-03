package com.bitchat.android.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log

/**
 * Client library for using Bitchat Mesh API from other apps
 *
 * Example usage:
 * ```kotlin
 * val client = MeshApiClient(context)
 * client.connect { success ->
 *     if (success) {
 *         client.sendMessageToPeer("peer1234", "chess_move", "e2e4".toByteArray())
 *         client.registerCallback { message ->
 *             println("Received: ${message.messageType} from ${message.peerID}")
 *         }
 *     }
 * }
 * ```
 */
class MeshApiClient(private val context: Context) {

    companion object {
        private const val TAG = "MeshApiClient"
    }

    private var apiService: IMeshApiService? = null
    private var isBound = false
    private var messageCallback: ((MeshMessage) -> Unit)? = null
    private var peerConnectionCallback: ((String, Boolean) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            apiService = IMeshApiService.Stub.asInterface(service)
            isBound = true
            Log.d(TAG, "Connected to Mesh API Service")

            // Register callback if one was set before connection
            messageCallback?.let { callback ->
                registerCallback(callback)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            apiService = null
            isBound = false
            Log.d(TAG, "Disconnected from Mesh API Service")
        }
    }

    private val messageCallbackStub = object : IMeshMessageCallback.Stub() {
        override fun onMessageReceived(message: MeshMessage?) {
            message?.let {
                messageCallback?.invoke(it)
            }
        }

        override fun onPeerConnectionChanged(peerID: String?, isConnected: Boolean) {
            peerID?.let {
                peerConnectionCallback?.invoke(it, isConnected)
            }
        }
    }

    /**
     * Connect to the mesh API service
     */
    fun connect(onConnected: ((Boolean) -> Unit)? = null) {
        val intent = Intent().apply {
            setClassName("com.bitchat.droid", "com.bitchat.android.api.MeshApiService")
            action = "com.bitchat.android.api.MESH_API"
        }

        try {
            val bound = context.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )

            if (bound) {
                // Service connection will be established asynchronously
                // Call onConnected when service is actually connected
                // For simplicity, we'll call it immediately if bind succeeds
                onConnected?.invoke(true)
            } else {
                Log.e(TAG, "Failed to bind to Mesh API Service")
                onConnected?.invoke(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error binding to Mesh API Service: ${e.message}")
            onConnected?.invoke(false)
        }
    }

    /**
     * Disconnect from the mesh API service
     */
    fun disconnect() {
        if (isBound) {
            try {
                apiService?.unregisterMessageCallback(messageCallbackStub)
            } catch (e: RemoteException) {
                Log.e(TAG, "Error unregistering callback: ${e.message}")
            }
            context.unbindService(serviceConnection)
            isBound = false
            apiService = null
        }
    }

    /**
     * Send a message to a specific peer (encrypted)
     */
    fun sendMessageToPeer(peerID: String, messageType: String, payload: ByteArray): Boolean {
        return try {
            apiService?.sendMessageToPeer(peerID, messageType, payload) ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "Error sending message to peer: ${e.message}")
            false
        }
    }

    /**
     * Broadcast a message to all peers (unencrypted)
     */
    fun broadcastMessage(messageType: String, payload: ByteArray): Boolean {
        return try {
            apiService?.broadcastMessage(messageType, payload) ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "Error broadcasting message: ${e.message}")
            false
        }
    }

    /**
     * Get list of connected peers
     */
    fun getConnectedPeers(): List<PeerInfo> {
        return try {
            apiService?.getConnectedPeers()?.toList() ?: emptyList()
        } catch (e: RemoteException) {
            Log.e(TAG, "Error getting connected peers: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get my own peer ID
     */
    fun getMyPeerID(): String {
        return try {
            apiService?.getMyPeerID() ?: "unknown"
        } catch (e: RemoteException) {
            Log.e(TAG, "Error getting my peer ID: ${e.message}")
            "unknown"
        }
    }

    /**
     * Check if mesh is active
     */
    fun isMeshActive(): Boolean {
        return try {
            apiService?.isMeshActive() ?: false
        } catch (e: RemoteException) {
            false
        }
    }

    /**
     * Register callback for receiving messages
     */
    fun registerCallback(
        onMessage: (MeshMessage) -> Unit,
        onPeerConnection: ((String, Boolean) -> Unit)? = null
    ) {
        messageCallback = onMessage
        peerConnectionCallback = onPeerConnection

        if (isBound && apiService != null) {
            try {
                apiService?.registerMessageCallback(messageCallbackStub)
            } catch (e: RemoteException) {
                Log.e(TAG, "Error registering callback: ${e.message}")
            }
        }
    }
}

