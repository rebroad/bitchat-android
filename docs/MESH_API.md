# Bitchat Mesh API

The Bitchat Mesh API allows other Android apps to send and receive messages over the Bitchat mesh network. This enables building games, collaborative apps, and other peer-to-peer applications on top of the mesh infrastructure.

## Status

⚠️ **Note**: The Mesh API is currently being implemented. There may be build issues related to AIDL compilation that need to be resolved. See "Troubleshooting" section below.

## Features

- **Send encrypted private messages** to specific peers
- **Broadcast messages** to all connected peers
- **Discover peers** in the mesh network
- **Receive real-time messages** via callbacks
- **TCP/IP bridge** for internet connectivity

## Setup

### 1. Add Permission to Your App's Manifest

```xml
<uses-permission android:name="com.bitchat.android.permission.USE_MESH_API" />
```

### 2. Add Bitchat AIDL Files to Your Project

Copy the AIDL files from `bitchat-android/app/src/main/aidl/com/bitchat/android/api/` to your project:
- `IMeshApiService.aidl`
- `IMeshMessageCallback.aidl`
- `MeshMessage.aidl`
- `PeerInfo.aidl`

And the Kotlin classes:
- `MeshMessage.kt`
- `PeerInfo.kt`

### 3. Use the MeshApiClient

```kotlin
import com.bitchat.android.api.MeshApiClient
import com.bitchat.android.api.MeshMessage

class YourGameActivity : AppCompatActivity() {
    private lateinit var meshClient: MeshApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        meshClient = MeshApiClient(this)
        meshClient.connect { success ->
            if (success) {
                Log.d("Game", "Connected to mesh!")

                // Register for messages
                meshClient.registerCallback(
                    onMessage = { message ->
                        handleGameMove(message)
                    },
                    onPeerConnection = { peerID, isConnected ->
                        Log.d("Game", "Peer $peerID ${if (isConnected) "connected" else "disconnected"}")
                    }
                )
            }
        }
    }

    private fun handleGameMove(message: MeshMessage) {
        when (message.messageType) {
            "chess_move" -> {
                val move = String(message.payload, Charsets.UTF_8)
                // Process chess move: "e2e4"
            }
            "connect4_move" -> {
                val column = String(message.payload, Charsets.UTF_8).toInt()
                // Process Connect 4 move
            }
        }
    }

    fun sendChessMove(peerID: String, move: String) {
        meshClient.sendMessageToPeer(
            peerID = peerID,
            messageType = "chess_move",
            payload = move.toByteArray(Charsets.UTF_8)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        meshClient.disconnect()
    }
}
```

## Message Types

### Game Messages

- `chess_move`: Chess move notation (e.g., "e2e4")
- `connect4_move`: Column number (0-6)
- `game_<custom>`: Custom game message types

### System Messages

- `private_message`: Encrypted private message
- `chat`: Public broadcast message

## TCP/IP Bridge

The TCP/IP bridge allows connecting the mesh network to internet servers or other TCP/IP networks.

### Starting the Bridge

```kotlin
val bridgeService = TcpIpBridgeService()
bridgeService.startBridge(port = 12345)
```

### Connecting via TCP

```python
import socket
import json

# Connect to mesh bridge
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.connect(("device_ip", 12345))

# Send a message
message = {
    "peerID": "target_peer_id",
    "messageType": "chess_move",
    "payload": "e2e4",
    "timestamp": 1234567890,
    "isEncrypted": False
}
sock.send(json.dumps(message).encode() + b"\n")

# Receive messages
while True:
    data = sock.recv(4096)
    if data:
        message = json.loads(data.decode())
        print(f"Received: {message['messageType']} from {message['peerID']}")
```

## Example: Chess Game

```kotlin
class ChessGame {
    private val meshClient = MeshApiClient(context)
    private var opponentPeerID: String? = null

    fun startGame() {
        meshClient.connect { success ->
            if (success) {
                // Find opponent
                val peers = meshClient.getConnectedPeers()
                opponentPeerID = peers.firstOrNull()?.peerID

                // Register for moves
                meshClient.registerCallback { message ->
                    if (message.messageType == "chess_move") {
                        val move = String(message.payload, Charsets.UTF_8)
                        makeMove(move)
                    }
                }
            }
        }
    }

    fun sendMove(move: String) {
        opponentPeerID?.let { peerID ->
            meshClient.sendMessageToPeer(
                peerID = peerID,
                messageType = "chess_move",
                payload = move.toByteArray(Charsets.UTF_8)
            )
        }
    }
}
```

## Example: Connect 4 Game

```kotlin
class Connect4Game {
    private val meshClient = MeshApiClient(context)

    fun makeMove(column: Int) {
        val peerID = getOpponentPeerID()
        meshClient.sendMessageToPeer(
            peerID = peerID,
            messageType = "connect4_move",
            payload = column.toString().toByteArray(Charsets.UTF_8)
        )
    }

    init {
        meshClient.registerCallback { message ->
            if (message.messageType == "connect4_move") {
                val column = String(message.payload, Charsets.UTF_8).toInt()
                handleOpponentMove(column)
            }
        }
    }
}
```

## Security

- **Private messages** are encrypted using Noise Protocol (X25519 + ChaCha20-Poly1305)
- **Broadcast messages** are signed but not encrypted
- **Peer IDs** are derived from cryptographic identities
- Apps must request `USE_MESH_API` permission

## Limitations

- Mesh networking requires Bluetooth LE
- Messages are limited by BLE MTU (typically 20-512 bytes)
- Large messages are automatically fragmented
- Network is ad-hoc (no central server)

## API Reference

### MeshApiClient

- `connect(onConnected: (Boolean) -> Unit?)`: Connect to mesh service
- `disconnect()`: Disconnect from mesh service
- `sendMessageToPeer(peerID, messageType, payload)`: Send encrypted message
- `broadcastMessage(messageType, payload)`: Broadcast message
- `getConnectedPeers()`: Get list of connected peers
- `getMyPeerID()`: Get your peer ID
- `isMeshActive()`: Check if mesh is running
- `registerCallback(onMessage, onPeerConnection?)`: Register for messages

### MeshMessage

- `peerID: String`: Sender peer ID
- `messageType: String`: Message type
- `payload: ByteArray`: Message payload
- `timestamp: Long`: Timestamp
- `isEncrypted: Boolean`: Whether message was encrypted

### PeerInfo

- `peerID: String`: Peer ID
- `nickname: String?`: Optional nickname
- `isConnected: Boolean`: Connection status
- `lastSeen: Long`: Last seen timestamp

## Implementation Details

The Mesh API consists of:

- **AIDL Interfaces** (`app/src/main/aidl/com/bitchat/android/api/`)
  - `IMeshApiService.aidl` - Main service interface
  - `IMeshMessageCallback.aidl` - Callback interface for receiving messages
  - `MeshMessage.aidl` & `PeerInfo.aidl` - Parcelable types

- **Kotlin Implementation** (`app/src/main/java/com/bitchat/android/api/`)
  - `MeshApiService.kt` - Exported Android Service wrapping BluetoothMeshService
  - `MeshApiClient.kt` - Client library for easy integration
  - `MeshMessage.kt` & `PeerInfo.kt` - Data classes
  - `TcpIpBridgeService.kt` - TCP/IP bridge for internet connectivity

- **Manifest**: Exported `MeshApiService` with `USE_MESH_API` permission protection

## Troubleshooting

### Build Issues

If you encounter compilation errors related to AIDL interfaces:

1. Ensure AIDL files are in the correct location: `app/src/main/aidl/com/bitchat/android/api/`
2. The Android Gradle Plugin should automatically compile AIDL before Kotlin
3. Try:
   - Clean and rebuild: `./gradlew clean assembleDebug`
   - Sync project in Android Studio (File → Sync Project with Gradle Files)
   - Check that `namespace = "com.bitchat.android"` matches AIDL package
   - Verify AIDL syntax is correct
