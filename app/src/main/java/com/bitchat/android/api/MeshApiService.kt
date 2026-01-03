package com.bitchat.android.api

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.util.Log
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.service.MeshServiceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Exported Android Service that provides mesh networking API to other apps
 *
 * This service wraps BluetoothMeshService and exposes it via AIDL for inter-app communication.
 * Other apps can bind to this service to send/receive messages over the mesh network.
 *
 * Use cases:
 * - Games (chess, connect 4, etc.) - send game moves as messages
 * - Custom apps - use mesh for any peer-to-peer communication
 * - TCP/IP bridge - bridge mesh to TCP/IP networks
 */
class MeshApiService : Service() {

    companion object {
        private const val TAG = "MeshApiService"
        const val ACTION_BIND = "com.bitchat.android.api.MESH_API"
    }

    private val binder = MeshApiBinder()
    private val callbacks = RemoteCallbackList<com.bitchat.android.api.IMeshMessageCallback>()
    private val apiScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track registered callbacks per peer for connection notifications
    private val peerConnectionState = ConcurrentHashMap<String, Boolean>()

    // Delegate wrapper to receive messages from mesh service
    private val meshDelegateWrapper = MeshApiDelegateWrapper(callbacks, peerConnectionState)

    inner class MeshApiBinder : com.bitchat.android.api.IMeshApiService.Stub() {

        override fun sendMessageToPeer(peerID: String, messageType: String, payload: ByteArray): Boolean {
            return try {
                val meshService = MeshServiceHolder.getOrCreate(applicationContext)

                // Create a custom message format: "GAME:type:payload"
                val messageContent = when (messageType) {
                    "chess_move", "connect4_move" -> "$messageType:${payload.toString(Charsets.UTF_8)}"
                    else -> "GAME:$messageType:${payload.toString(Charsets.UTF_8)}"
                }

                apiScope.launch {
                    meshService.sendPrivateMessage(
                        content = messageContent,
                        recipientPeerID = peerID,
                        recipientNickname = peerID, // Use peerID as nickname if not available
                        messageID = null
                    )
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message to peer $peerID: ${e.message}")
                false
            }
        }

        override fun broadcastMessage(messageType: String, payload: ByteArray): Boolean {
            return try {
                val meshService = MeshServiceHolder.getOrCreate(applicationContext)

                val messageContent = when (messageType) {
                    "chess_move", "connect4_move" -> "$messageType:${payload.toString(Charsets.UTF_8)}"
                    else -> "GAME:$messageType:${payload.toString(Charsets.UTF_8)}"
                }

                apiScope.launch {
                    meshService.sendMessage(
                        content = messageContent,
                        mentions = emptyList(),
                        channel = null
                    )
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting message: ${e.message}")
                false
            }
        }

        override fun getConnectedPeers(): Array<PeerInfo> {
            return try {
                val meshService = MeshServiceHolder.getOrCreate(applicationContext)
                val peers = meshService.getConnectedPeers()

                peers.map { peerInfo ->
                    PeerInfo(
                        peerID = peerInfo.id,
                        nickname = peerInfo.nickname,
                        isConnected = peerInfo.isConnected,
                        lastSeen = peerInfo.lastSeen
                    )
                }.toTypedArray()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting connected peers: ${e.message}")
                emptyArray()
            }
        }

        override fun getMyPeerID(): String {
            return try {
                val meshService = MeshServiceHolder.getOrCreate(applicationContext)
                meshService.myPeerID
            } catch (e: Exception) {
                Log.e(TAG, "Error getting my peer ID: ${e.message}")
                "unknown"
            }
        }

        override fun registerMessageCallback(callback: com.bitchat.android.api.IMeshMessageCallback?) {
            if (callback != null) {
                callbacks.register(callback)
                Log.d(TAG, "Registered message callback (total: ${callbacks.registeredCallbackCount})")

                // Register delegate with mesh service if this is the first callback
                if (callbacks.registeredCallbackCount == 1) {
                    val meshService = MeshServiceHolder.getOrCreate(applicationContext)
                    meshService.delegate = meshDelegateWrapper
                }
            }
        }

        override fun unregisterMessageCallback(callback: com.bitchat.android.api.IMeshMessageCallback?) {
            if (callback != null) {
                callbacks.unregister(callback)
                Log.d(TAG, "Unregistered message callback (total: ${callbacks.registeredCallbackCount})")

                // Unregister delegate if no callbacks remain
                if (callbacks.registeredCallbackCount == 0) {
                    val meshService = MeshServiceHolder.meshService
                    meshService?.delegate = null
                }
            }
        }

        override fun isMeshActive(): Boolean {
            return try {
                val meshService = MeshServiceHolder.meshService
                meshService != null && meshService.isReusable()
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "MeshApiService bound")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        callbacks.kill()
        Log.d(TAG, "MeshApiService destroyed")
    }
}

/**
 * Delegate wrapper that implements BluetoothMeshDelegate for API service
 * Converts mesh messages to API format and notifies registered callbacks
 */
private class MeshApiDelegateWrapper(
    private val callbacks: RemoteCallbackList<com.bitchat.android.api.IMeshMessageCallback>,
    private val peerConnectionState: ConcurrentHashMap<String, Boolean>
) : com.bitchat.android.mesh.BluetoothMeshDelegate {

    override fun didReceiveMessage(message: BitchatMessage) {
        val meshMessage = MeshMessage(
            peerID = message.senderPeerID ?: "unknown",
            messageType = determineMessageType(message),
            payload = message.content.toByteArray(Charsets.UTF_8),
            timestamp = message.timestamp.time,
            isEncrypted = message.isPrivate
        )

        notifyCallbacks { callback ->
            try {
                callback.onMessageReceived(meshMessage)
            } catch (e: RemoteException) {
                Log.e("MeshApiService", "Error notifying callback: ${e.message}")
            }
        }
    }

    override fun didUpdatePeerList(peers: List<String>) {
        // Notify about peer connections/disconnections
        peers.forEach { peerID ->
            val wasConnected = peerConnectionState[peerID] ?: false
            if (!wasConnected) {
                peerConnectionState[peerID] = true
                notifyCallbacks { callback ->
                    try {
                        callback.onPeerConnectionChanged(peerID, true)
                    } catch (e: RemoteException) {
                        Log.e("MeshApiService", "Error notifying peer connection: ${e.message}")
                    }
                }
            }
        }

        // Check for disconnected peers
        val currentPeers = peers.toSet()
        peerConnectionState.keys.toList().forEach { peerID ->
            if (!currentPeers.contains(peerID) && peerConnectionState[peerID] == true) {
                peerConnectionState[peerID] = false
                notifyCallbacks { callback ->
                    try {
                        callback.onPeerConnectionChanged(peerID, false)
                    } catch (e: RemoteException) {
                        Log.e("MeshApiService", "Error notifying peer disconnection: ${e.message}")
                    }
                }
            }
        }
    }

    override fun didReceiveChannelLeave(channel: String, fromPeer: String) {
        // Not needed for API
    }

    override fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String) {
        // Not needed for API
    }

    override fun didReceiveReadReceipt(messageID: String, recipientPeerID: String) {
        // Not needed for API
    }

    override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? {
        // Not needed for API - games use private messages
        return null
    }

    override fun getNickname(): String? {
        return null
    }

    override fun isFavorite(peerID: String): Boolean {
        return false
    }

    private fun determineMessageType(message: BitchatMessage): String {
        val content = message.content
        return when {
            content.startsWith("GAME:") -> {
                val type = content.substringAfter("GAME:").substringBefore(":")
                "game_$type"
            }
            content.startsWith("CHESS:") -> "chess_move"
            content.startsWith("CONNECT4:") -> "connect4_move"
            message.isPrivate -> "private_message"
            else -> "chat"
        }
    }

    private fun notifyCallbacks(action: (com.bitchat.android.api.IMeshMessageCallback) -> Unit) {
        val count = callbacks.beginBroadcast()
        try {
            for (i in 0 until count) {
                val callback = callbacks.getBroadcastItem(i)
                callback?.let(action)
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }
}

