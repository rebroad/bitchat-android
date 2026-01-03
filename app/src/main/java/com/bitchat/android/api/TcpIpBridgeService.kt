package com.bitchat.android.api

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.bitchat.android.service.MeshServiceHolder
import kotlinx.coroutines.*
import java.io.IOException
import java.net.*
import java.util.concurrent.ConcurrentHashMap

/**
 * TCP/IP Bridge Service
 *
 * Bridges the mesh network to TCP/IP, allowing:
 * - Mesh peers to connect via TCP/IP (if they have internet)
 * - TCP/IP clients to send messages to mesh network
 * - Hybrid mesh+internet connectivity
 *
 * Use cases:
 * - Connect mesh network to internet servers
 * - Bridge mesh to other networks (WiFi, cellular)
 * - Allow remote players to join mesh games via TCP/IP
 */
class TcpIpBridgeService : Service() {

    companion object {
        private const val TAG = "TcpIpBridgeService"
        private const val DEFAULT_PORT = 12345
        private const val MAX_CONNECTIONS = 10
    }

    private val binder = TcpIpBridgeBinder()
    private val bridgeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // TCP server socket
    private var serverSocket: ServerSocket? = null
    private val activeConnections = ConcurrentHashMap<Socket, Job>()

    // Bridge state
    private var isRunning = false
    private var listenPort = DEFAULT_PORT

    inner class TcpIpBridgeBinder : Binder() {
        fun getService(): TcpIpBridgeService = this@TcpIpBridgeService
    }

    /**
     * Start TCP/IP bridge server
     */
    fun startBridge(port: Int = DEFAULT_PORT): Boolean {
        if (isRunning) {
            Log.w(TAG, "Bridge already running")
            return false
        }

        return try {
            listenPort = port
            serverSocket = ServerSocket(port)
            isRunning = true

            bridgeScope.launch {
                acceptConnections()
            }

            Log.i(TAG, "TCP/IP bridge started on port $port")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start TCP/IP bridge: ${e.message}")
            false
        }
    }

    /**
     * Stop TCP/IP bridge server
     */
    fun stopBridge() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }

        // Close all active connections
        activeConnections.keys.forEach { socket ->
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
        activeConnections.clear()

        Log.i(TAG, "TCP/IP bridge stopped")
    }

    /**
     * Send message from mesh to TCP/IP client
     */
    fun sendToTcpClient(socket: Socket, message: MeshMessage) {
        bridgeScope.launch {
            try {
                val output = socket.getOutputStream()
                val json = """
                    {
                        "peerID": "${message.peerID}",
                        "messageType": "${message.messageType}",
                        "payload": "${message.payload.toString(Charsets.UTF_8)}",
                        "timestamp": ${message.timestamp},
                        "isEncrypted": ${message.isEncrypted}
                    }
                """.trimIndent()

                output.write(json.toByteArray(Charsets.UTF_8))
                output.write('\n'.code)
                output.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending to TCP client: ${e.message}")
                socket.close()
                activeConnections.remove(socket)
            }
        }
    }

    /**
     * Send message from TCP/IP client to mesh network
     */
    private fun sendToMesh(message: MeshMessage) {
        try {
            val meshService = MeshServiceHolder.getOrCreate(applicationContext)

            bridgeScope.launch {
                if (message.isEncrypted) {
                    // Send as private message
                    meshService.sendPrivateMessage(
                        content = "TCP_BRIDGE:${message.messageType}:${message.payload.toString(Charsets.UTF_8)}",
                        recipientPeerID = message.peerID,
                        recipientNickname = message.peerID,
                        messageID = null
                    )
                } else {
                    // Broadcast
                    meshService.sendMessage(
                        content = "TCP_BRIDGE:${message.messageType}:${message.payload.toString(Charsets.UTF_8)}",
                        mentions = emptyList(),
                        channel = null
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending to mesh: ${e.message}")
        }
    }

    private suspend fun acceptConnections() {
        while (isRunning && !serverSocket!!.isClosed) {
            try {
                val socket = withContext(Dispatchers.IO) {
                    serverSocket!!.accept()
                }

                if (activeConnections.size >= MAX_CONNECTIONS) {
                    Log.w(TAG, "Max connections reached, rejecting new connection")
                    socket.close()
                    continue
                }

                Log.d(TAG, "New TCP connection from ${socket.remoteSocketAddress}")

                val connectionJob = bridgeScope.launch {
                    handleConnection(socket)
                }

                activeConnections[socket] = connectionJob

            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Error accepting connection: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleConnection(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val buffer = ByteArray(4096)

            while (isRunning && !socket.isClosed) {
                val bytesRead = withContext(Dispatchers.IO) {
                    input.read(buffer)
                }

                if (bytesRead <= 0) break

                val messageJson = String(buffer, 0, bytesRead, Charsets.UTF_8).trim()
                if (messageJson.isEmpty()) continue

                // Parse JSON message (simplified - in production use proper JSON parser)
                try {
                    val message = parseTcpMessage(messageJson)
                    if (message != null) {
                        sendToMesh(message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing TCP message: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling connection: ${e.message}")
        } finally {
            socket.close()
            activeConnections.remove(socket)
        }
    }

    private fun parseTcpMessage(json: String): MeshMessage? {
        // Simplified JSON parsing - in production use proper JSON library
        // Format: {"peerID":"...","messageType":"...","payload":"...","timestamp":123,"isEncrypted":false}
        return try {
            val peerID = extractJsonField(json, "peerID") ?: "tcp_client"
            val messageType = extractJsonField(json, "messageType") ?: "tcp_message"
            val payloadStr = extractJsonField(json, "payload") ?: ""
            val timestamp = extractJsonLong(json, "timestamp") ?: System.currentTimeMillis()
            val isEncrypted = extractJsonBoolean(json, "isEncrypted") ?: false

            MeshMessage(
                peerID = peerID,
                messageType = messageType,
                payload = payloadStr.toByteArray(Charsets.UTF_8),
                timestamp = timestamp,
                isEncrypted = isEncrypted
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing TCP message JSON: ${e.message}")
            null
        }
    }

    private fun extractJsonField(json: String, field: String): String? {
        val pattern = "\"$field\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun extractJsonLong(json: String, field: String): Long? {
        val pattern = "\"$field\"\\s*:\\s*(\\d+)".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun extractJsonBoolean(json: String, field: String): Boolean? {
        val pattern = "\"$field\"\\s*:\\s*(true|false)".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toBoolean()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBridge()
        bridgeScope.cancel()
    }
}

