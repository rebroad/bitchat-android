# Bitchat Protocol - Low-Level Design (LLD)

## Introduction

This document provides a detailed, implementation-level specification of the Bitchat protocol. It covers packet formats, encoding/decoding procedures, cryptographic operations, routing algorithms, and state machines.

## Binary Packet Format

### Packet Structure

```
┌─────────────────────────────────────────────────────────┐
│                    Packet Header                        │
│  (13 bytes for v1, 15 bytes for v2)                     │
├─────────────────────────────────────────────────────────┤
│              Variable Sections                          │
│  (SenderID, RecipientID, Payload, Signature)            │
└─────────────────────────────────────────────────────────┘
```

### Header Format

#### Version 1 (v1) Header (13 bytes)

```
Offset  Size    Field           Description
─────────────────────────────────────────────────────────
0       1       Version         Protocol version (0x01)
1       1       Type            Message type (see MessageType enum)
2       1       TTL             Time-to-live (hops, 0-7)
3       8       Timestamp       Unix timestamp (milliseconds, big-endian)
11      1       Flags           Bit flags (see Flags section)
12      2       PayloadLength   Payload length (bytes, big-endian, max 65535)
```

#### Version 2 (v2) Header (15 bytes)

```
Offset  Size    Field           Description
─────────────────────────────────────────────────────────
0       1       Version         Protocol version (0x02)
1       1       Type            Message type (see MessageType enum)
2       1       TTL             Time-to-live (hops, 0-7)
3       8       Timestamp       Unix timestamp (milliseconds, big-endian)
11      1       Flags           Bit flags (see Flags section)
12      4       PayloadLength   Payload length (bytes, big-endian, max ~4 GiB)
```

### Flags Byte

```
Bit 0: HAS_RECIPIENT    (0x01) - RecipientID field present
Bit 1: HAS_SIGNATURE    (0x02) - Signature field present
Bit 2: IS_COMPRESSED    (0x04) - Payload is compressed
Bit 3: HAS_ROUTE        (0x08) - Source route present (optional)
Bits 4-7: Reserved      (0x10-0x80) - Reserved for future use
```

### Variable Sections (in order)

1. **SenderID** (8 bytes, always present)
   - Binary representation of peer ID
   - 16 hex characters → 8 bytes (left-to-right conversion)
   - Padded with 0x00 if shorter than 16 hex chars

2. **RecipientID** (8 bytes, if HAS_RECIPIENT flag set)
   - Binary representation of recipient peer ID
   - All 0xFF = broadcast recipient
   - Same encoding as SenderID

3. **Route** (optional, if HAS_ROUTE flag set)
   - Count: 1 byte (number of hops, 0-255)
   - Hops: count × 8 bytes (peer IDs in order)
   - See [SOURCE_ROUTING.md](SOURCE_ROUTING.md) for details

4. **Payload** (variable length)
   - If IS_COMPRESSED: 2 bytes original size (big-endian) + compressed data
   - Otherwise: raw payload data
   - Content depends on message type

5. **Signature** (64 bytes, if HAS_SIGNATURE flag set)
   - Ed25519 signature
   - Covers entire packet (excluding signature field and with TTL=0)

### Packet Encoding Algorithm

```kotlin
fun encode(packet: BitchatPacket): ByteArray? {
    // 1. Compress payload if beneficial (>100 bytes)
    var payload = packet.payload
    var originalPayloadSize: UShort? = null
    var isCompressed = false

    if (shouldCompress(payload)) {
        val compressed = compress(payload)
        if (compressed != null && compressed.size < payload.size) {
            originalPayloadSize = payload.size.toUShort()
            payload = compressed
            isCompressed = true
        }
    }

    // 2. Calculate sizes
    val headerSize = if (packet.version >= 2u) 15 else 13
    val recipientBytes = if (packet.recipientID != null) 8 else 0
    val signatureBytes = if (packet.signature != null) 64 else 0
    val payloadBytes = payload.size + if (isCompressed) 2 else 0

    // 3. Build buffer
    val buffer = ByteBuffer.allocate(headerSize + 8 + recipientBytes + payloadBytes + signatureBytes)
    buffer.order(ByteOrder.BIG_ENDIAN)

    // 4. Write header
    buffer.put(packet.version.toByte())
    buffer.put(packet.type.toByte())
    buffer.put(packet.ttl.toByte())
    buffer.putLong(packet.timestamp.toLong())

    // 5. Write flags
    var flags: UByte = 0u
    if (packet.recipientID != null) flags = flags or 0x01u
    if (packet.signature != null) flags = flags or 0x02u
    if (isCompressed) flags = flags or 0x04u
    buffer.put(flags.toByte())

    // 6. Write payload length
    val payloadDataSize = payloadBytes
    if (packet.version >= 2u) {
        buffer.putInt(payloadDataSize)
    } else {
        buffer.putShort(payloadDataSize.toShort())
    }

    // 7. Write variable sections
    buffer.put(packet.senderID.take(8).toByteArray())
    if (packet.recipientID != null) {
        buffer.put(packet.recipientID.take(8).toByteArray())
    }
    if (isCompressed) {
        buffer.putShort(originalPayloadSize!!.toShort())
    }
    buffer.put(payload)
    if (packet.signature != null) {
        buffer.put(packet.signature.take(64).toByteArray())
    }

    // 8. Apply padding for traffic analysis resistance
    val result = ByteArray(buffer.position())
    buffer.rewind()
    buffer.get(result)
    return pad(result, optimalBlockSize(result.size))
}
```

### Packet Decoding Algorithm

```kotlin
fun decode(data: ByteArray): BitchatPacket? {
    // 1. Try decode as-is (robust to missing padding)
    decodeCore(data)?.let { return it }

    // 2. Try after removing padding
    val unpadded = unpad(data)
    if (unpadded.contentEquals(data)) return null
    return decodeCore(unpadded)
}

private fun decodeCore(raw: ByteArray): BitchatPacket? {
    if (raw.size < 13 + 8) return null // Minimum header + senderID

    val buffer = ByteBuffer.wrap(raw).apply { order(ByteOrder.BIG_ENDIAN) }

    // Read header
    val version = buffer.get().toUByte()
    if (version != 1u && version != 2u) return null

    val headerSize = if (version >= 2u) 15 else 13
    val type = buffer.get().toUByte()
    val ttl = buffer.get().toUByte()
    val timestamp = buffer.getLong().toULong()
    val flags = buffer.get().toUByte()

    val hasRecipient = (flags and 0x01u) != 0u
    val hasSignature = (flags and 0x02u) != 0u
    val isCompressed = (flags and 0x04u) != 0u

    // Read payload length
    val payloadLength = if (version >= 2u) {
        buffer.getInt().toUInt()
    } else {
        buffer.getShort().toUShort().toUInt()
    }

    // Verify size
    var expectedSize = headerSize + 8 + payloadLength.toInt()
    if (hasRecipient) expectedSize += 8
    if (hasSignature) expectedSize += 64
    if (raw.size < expectedSize) return null

    // Read variable sections
    val senderID = ByteArray(8)
    buffer.get(senderID)

    val recipientID = if (hasRecipient) {
        val recipientBytes = ByteArray(8)
        buffer.get(recipientBytes)
        recipientBytes
    } else null

    val payload = if (isCompressed) {
        val originalSize = buffer.getShort().toInt()
        val compressedPayload = ByteArray(payloadLength.toInt() - 2)
        buffer.get(compressedPayload)
        decompress(compressedPayload, originalSize) ?: return null
    } else {
        val payloadBytes = ByteArray(payloadLength.toInt())
        buffer.get(payloadBytes)
        payloadBytes
    }

    val signature = if (hasSignature) {
        val signatureBytes = ByteArray(64)
        buffer.get(signatureBytes)
        signatureBytes
    } else null

    return BitchatPacket(
        version = version,
        type = type,
        senderID = senderID,
        recipientID = recipientID,
        timestamp = timestamp,
        payload = payload,
        signature = signature,
        ttl = ttl
    )
}
```

## Message Types

### ANNOUNCE (0x01)

**Purpose**: Identity announcement with nickname, public keys, and optional neighbor gossip.

**Payload Format**: TLV-encoded with 1-byte type and 1-byte length fields
```
TLV 0x01: NICKNAME (UTF-8 string, ≤255 bytes)
TLV 0x02: NOISE_PUBLIC_KEY (32 bytes X25519 key)
TLV 0x03: SIGNING_PUBLIC_KEY (32 bytes Ed25519 key)
TLV 0x04: DIRECT_NEIGHBORS (N×8 bytes peer IDs, up to 10 neighbors) [optional]
```

**Behavior**:
- Broadcast every 30 seconds
- TTL = 7 (relayed through mesh)
- Signature recommended (Ed25519)
- See [ANNOUNCEMENT_GOSSIP.md](ANNOUNCEMENT_GOSSIP.md) for neighbor gossip details

### MESSAGE (0x02)

**Purpose**: User messages (both broadcast and private).

**Payload Format**:
- **Broadcast**: Plain text or encrypted channel message
- **Private**: TLV-encoded private message (see PrivateMessagePacket)

**TLV Format for Private Messages**:
```
TLV 0x01: MESSAGE_ID (UUID string)
TLV 0x02: CONTENT (UTF-8 message text)
```

**Behavior**:
- Broadcast messages: TTL = 7, no recipientID
- Private messages: TTL = 7, recipientID set, encrypted with Noise

### LEAVE (0x03)

**Purpose**: Peer departure notification.

**Payload Format**: Empty or optional reason string.

**Behavior**:
- TTL = 7 (relayed)
- Signature optional
- Triggers peer removal from mesh

### NOISE_HANDSHAKE (0x10)

**Purpose**: Noise protocol handshake messages.

**Payload Format**: Raw Noise handshake data (variable length).

**Behavior**:
- TTL = 7 (relayed to recipient)
- RecipientID must be set
- Initiates or continues Noise handshake
- Response determined by handshake state

**Noise Protocol Details**:
- **Pattern**: `Noise_XX_25519_ChaChaPoly_SHA256`
- **Handshake**: 3 messages (initiator → responder → initiator)
- **Key Exchange**: X25519
- **Cipher**: ChaCha20-Poly1305
- **Hash**: SHA-256
- **Message 1 Size**: 32 bytes (ephemeral public key `e`)
- **Message 2 Size**: 96 bytes (`e`, `ee`, `s`, `es` + 16-byte MAC)
- **Message 3 Size**: 48 bytes (`s`, `se` encrypted)
- **Rekey Thresholds**: After 1 hour or 10,000 messages (whichever comes first)
- **Replay Protection**: Sliding window of 1024 nonces (128 bytes)

### NOISE_ENCRYPTED (0x11)

**Purpose**: Encrypted transport messages using established Noise session.

**Payload Format**: Noise-encrypted TLV data.

**NoisePayload Format**:
```
[1 byte: NoisePayloadType] + [TLV data]
```

**NoisePayloadType Values**:
- `0x01`: PRIVATE_MESSAGE
- `0x02`: FILE_TRANSFER (future)
- `0x03`: ACK (future)

**Behavior**:
- Requires established Noise session
- TTL = 7, recipientID set
- Signature optional (integrity provided by Noise)

### FRAGMENT (0x20)

**Purpose**: Fragmented message chunks for large payloads.

**Payload Format**: TLV-encoded fragment data.

**Fragment TLV Format**:
```
TLV 0x01: FRAGMENT_ID (UUID string)
TLV 0x02: FRAGMENT_INDEX (uint16, 0-based)
TLV 0x03: TOTAL_FRAGMENTS (uint16)
TLV 0x04: DATA (opaque, max 469 bytes)
```

**Behavior**:
- Fragments sent with same recipientID as original
- Reassembly timeout: 30 seconds
- Missing fragments cause entire message to be discarded

### REQUEST_SYNC (0x21)

**Purpose**: Gossip sync request with GCS filter.

**Payload Format**: TLV-encoded sync request with 16-bit big-endian length fields.

**TLV Format**:
```
TLV 0x01: P (uint8) - Golomb-Rice parameter
TLV 0x02: M (uint32) - Hash range N × 2^P
TLV 0x03: data (opaque) - GCS bitstream (MSB-first)
```

**Behavior**:
- TTL = 0 (local-only, not relayed)
- Periodic: every 30 seconds to all neighbors (broadcast)
- Per-peer: 5 seconds after first ANNOUNCE from new peer (unicast)
- See [sync.md](sync.md) for detailed specification

### FILE_TRANSFER (0x22)

**Purpose**: File transfer packets (images, audio, generic files).

**Payload Format**: TLV-encoded file data with mixed length field sizes.

**Behavior**:
- Always uses v2 packet format (4-byte payload length)
- Fragmentation for files >512 bytes
- Progress tracking via fragment indices
- See [file_transfer.md](file_transfer.md) for detailed specification

## Cryptographic Operations

### Noise Protocol Handshake

#### Handshake State Machine

```
INITIATOR:
  START → send_message_1 → WAIT_RESPONSE → receive_message_2 → send_message_3 → ESTABLISHED

RESPONDER:
  START → receive_message_1 → send_message_2 → WAIT_RESPONSE → receive_message_3 → ESTABLISHED
```

#### Handshake Messages

**Message 1 (Initiator → Responder)**:
- Contains: `e` (ephemeral public key)
- Length: 32 bytes

**Message 2 (Responder → Initiator)**:
- Contains: `e` (ephemeral public key), `ee` (DH result), `s` (static public key), `es` (DH result), `se` (DH result)
- Length: 80 bytes

**Message 3 (Initiator → Responder)**:
- Contains: `s` (static public key), `se` (DH result)
- Length: 48 bytes

#### Session Establishment

After handshake completion:
- **Encryption Key**: Derived from handshake state
- **Decryption Key**: Same as encryption key (symmetric)
- **Nonce**: Starts at 0, increments per message (4-byte big-endian)
- **Nonce Format**: `<nonce (4 bytes)><ciphertext>` in combined payload
- **Rekey**: After 1 hour (3,600,000 ms) or 10,000 messages (whichever comes first)
- **Replay Protection**: Sliding window tracks last 1024 nonces
- **High Nonce Warning**: Warning logged when nonce exceeds 1,000,000,000

### Channel Encryption

#### Key Derivation

```kotlin
fun deriveChannelKey(password: String, channelName: String): SecretKeySpec {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(
        password.toCharArray(),
        channelName.toByteArray(Charsets.UTF_8), // Salt
        100000, // Iterations
        256 // Key length (bits)
    )
    val secretKey = factory.generateSecret(spec)
    return SecretKeySpec(secretKey.encoded, "AES")
}
```

#### Encryption

```kotlin
fun encryptChannelMessage(message: String, channel: String): ByteArray {
    val key = deriveChannelKey(password, channel)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)

    val iv = cipher.iv // 12 bytes for GCM
    val encryptedData = cipher.doFinal(message.toByteArray(Charsets.UTF_8))

    // Format: [IV (12 bytes)] + [encrypted_data + auth_tag]
    return iv + encryptedData
}
```

#### Decryption

```kotlin
fun decryptChannelMessage(encryptedData: ByteArray, channel: String): String {
    val key = deriveChannelKey(password, channel)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")

    val iv = encryptedData.sliceArray(0..11)
    val ciphertext = encryptedData.sliceArray(12 until encryptedData.size)

    val gcmSpec = GCMParameterSpec(128, iv) // 128-bit auth tag
    cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

    val decryptedBytes = cipher.doFinal(ciphertext)
    return String(decryptedBytes, Charsets.UTF_8)
}
```

### Digital Signatures

#### Signing

```kotlin
fun signPacket(packet: BitchatPacket, privateKey: Ed25519PrivateKey): ByteArray {
    // Create packet encoding with TTL=0 and signature=null for signing
    val unsignedPacket = packet.copy(ttl = 0u, signature = null)
    val dataToSign = BinaryProtocol.encode(unsignedPacket) ?: return ByteArray(0)

    val signer = Ed25519Signer()
    signer.init(true, privateKey)
    signer.update(dataToSign, 0, dataToSign.size)
    return signer.generateSignature()
}
```

#### Verification

```kotlin
fun verifySignature(packet: BitchatPacket, publicKey: Ed25519PublicKey): Boolean {
    val signature = packet.signature ?: return false

    // Recreate packet encoding with TTL=0 and signature=null
    val unsignedPacket = packet.copy(ttl = 0u, signature = null)
    val dataToVerify = BinaryProtocol.encode(unsignedPacket) ?: return false

    val verifier = Ed25519Signer()
    verifier.init(false, publicKey)
    verifier.update(dataToVerify, 0, dataToVerify.size)
    return verifier.verifySignature(signature)
}
```

## Routing Algorithms

### TTL-Based Routing

```kotlin
fun relayPacket(packet: BitchatPacket): BitchatPacket? {
    // Check TTL
    if (packet.ttl == 0u) return null

    // Decrement TTL
    val relayPacket = packet.copy(ttl = (packet.ttl - 1u).toUByte())

    // Check if we should relay (adaptive probability)
    val networkSize = getNetworkSize()
    val relayProb = when {
        networkSize <= 10 -> 1.0
        networkSize <= 30 -> 0.85
        networkSize <= 50 -> 0.70
        networkSize <= 100 -> 0.55
        else -> 0.40
    }

    if (Random.nextDouble() < relayProb) {
        return relayPacket
    }

    return null
}
```

### Source Routing

See [SOURCE_ROUTING.md](SOURCE_ROUTING.md) for detailed source routing specification. The implementation uses Dijkstra's shortest path algorithm with unit weights to compute routes.

## Duplicate Detection

### Packet ID Generation

```kotlin
fun computePacketID(packet: BitchatPacket): ByteArray {
    val hash = MessageDigest.getInstance("SHA-256")
    hash.update(packet.type.toByte())
    hash.update(packet.senderID)
    hash.update(packet.timestamp.toLong().toByteArray())
    hash.update(packet.payload)
    return hash.digest().sliceArray(0..15) // First 16 bytes
}
```

### Bloom Filter

```kotlin
class BloomFilter(capacity: Int, falsePositiveRate: Double) {
    private val bitSet = BitSet()
    private val hashCount: Int
    private val bitCount: Int

    init {
        val m = (-capacity * ln(falsePositiveRate) / (ln(2.0) * ln(2.0))).toInt()
        bitCount = m
        hashCount = ((m.toDouble() / capacity) * ln(2.0)).toInt().coerceAtLeast(1)
    }

    fun add(element: ByteArray) {
        for (i in 0 until hashCount) {
            val hash = hash(element, i)
            bitSet.set((hash % bitCount).toInt())
        }
    }

    fun mightContain(element: ByteArray): Boolean {
        for (i in 0 until hashCount) {
            val hash = hash(element, i)
            if (!bitSet.get((hash % bitCount).toInt())) {
                return false
            }
        }
        return true
    }

    private fun hash(data: ByteArray, seed: Int): Long {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(seed.toByte())
        md.update(data)
        val digest = md.digest()
        return ByteBuffer.wrap(digest.sliceArray(0..7)).order(ByteOrder.BIG_ENDIAN).long
    }
}
```

## Compression

### LZ4 Compression

```kotlin
fun compress(data: ByteArray): ByteArray? {
    if (data.size < COMPRESSION_THRESHOLD) return null

    return try {
        val compressor = LZ4Factory.fastestInstance().fastCompressor()
        val maxCompressedLength = compressor.maxCompressedLength(data.size)
        val compressed = ByteArray(maxCompressedLength)
        val compressedLength = compressor.compress(data, 0, data.size, compressed, 0, maxCompressedLength)
        compressed.sliceArray(0 until compressedLength)
    } catch (e: Exception) {
        null
    }
}

fun decompress(compressed: ByteArray, originalSize: Int): ByteArray? {
    return try {
        val decompressor = LZ4Factory.fastestInstance().fastDecompressor()
        val decompressed = ByteArray(originalSize)
        decompressor.decompress(compressed, 0, decompressed, 0, originalSize)
        decompressed
    } catch (e: Exception) {
        null
    }
}
```

## Message Padding

### Traffic Analysis Resistance

Padding uses PKCS#7 style to normalize message sizes and resist traffic analysis.

**Block Sizes**: 256, 512, 1024, 2048 bytes

**Algorithm**:
```kotlin
fun optimalBlockSize(dataSize: Int): Int {
    // Account for encryption overhead (~16 bytes for AES-GCM tag)
    val totalSize = dataSize + 16

    // Find smallest block that fits
    for (blockSize in listOf(256, 512, 1024, 2048)) {
        if (totalSize <= blockSize) {
            return blockSize
        }
    }

    // For very large messages, just use the original size
    // (will be fragmented anyway)
    return dataSize
}

fun pad(data: ByteArray, targetSize: Int): ByteArray {
    if (data.size >= targetSize) return data

    val paddingNeeded = targetSize - data.size

    // Constrain to 255 to fit a single-byte pad length marker
    if (paddingNeeded <= 0 || paddingNeeded > 255) return data

    val result = ByteArray(targetSize)

    // Copy original data
    System.arraycopy(data, 0, result, 0, data.size)

    // PKCS#7: All pad bytes are equal to the pad length
    for (i in data.size until targetSize) {
        result[i] = paddingNeeded.toByte()
    }

    return result
}

fun unpad(data: ByteArray): ByteArray {
    if (data.isEmpty()) return data

    val last = data[data.size - 1]
    val paddingLength = last.toInt() and 0xFF

    // Must have at least 1 pad byte and not exceed data length
    if (paddingLength <= 0 || paddingLength > data.size) return data

    // Verify PKCS#7: all last N bytes equal to pad length
    val start = data.size - paddingLength
    for (i in start until data.size) {
        if (data[i] != last) {
            return data // Invalid padding, return original
        }
    }

    return data.copyOfRange(0, start)
}
```

## GCS Filter (Gossip Sync)

See [sync.md](sync.md) for detailed GCS filter construction and membership testing algorithms. The implementation uses:
- Packet ID: First 16 bytes of SHA-256 over `[type | senderID | timestamp | payload]`
- GCS hash: First 8 bytes of SHA-256 over the 16-byte Packet ID, interpreted as unsigned 64-bit integer
- Golomb-Rice encoding with parameter P derived from target false positive rate
- MSB-first bit packing for the bitstream

## State Machines

### Noise Session State

```
UNINITIALIZED → INITIATING → WAITING_RESPONSE → ESTABLISHED
                    ↓              ↓
                 FAILED         FAILED
```

### Peer Connection State

```
DISCONNECTED → CONNECTING → CONNECTED → AUTHENTICATED → ACTIVE
                   ↓            ↓            ↓
                FAILED       FAILED      DISCONNECTED
```

### Fragment Reassembly State

```
WAITING → [receiving fragments] → COMPLETE → DELIVERED
              ↓ (timeout)
           EXPIRED
```

## Constants

### Protocol Constants

```kotlin
object ProtocolConstants {
    // TTL
    const val MAX_TTL: UByte = 7u
    const val SYNC_TTL: UByte = 0u

    // Packet sizes
    const val HEADER_SIZE_V1 = 13
    const val HEADER_SIZE_V2 = 15
    const val SENDER_ID_SIZE = 8
    const val RECIPIENT_ID_SIZE = 8
    const val SIGNATURE_SIZE = 64

    // Compression
    const val COMPRESSION_THRESHOLD = 100

    // Fragmentation
    const val FRAGMENT_THRESHOLD = 512
    const val MAX_FRAGMENT_SIZE = 469
    const val FRAGMENT_TIMEOUT_MS = 30000L

    // Sync
    const val SYNC_INTERVAL_MS = 30000L
    const val SYNC_DELAY_MS = 5000L
    const val ANNOUNCE_INTERVAL_MS = 30000L

    // Store-and-forward
    const val CACHE_TIMEOUT_MS = 43200000L // 12 hours
    const val MAX_CACHED_MESSAGES = 100
    const val MAX_CACHED_MESSAGES_FAVORITES = 1000
    const val CACHE_CLEANUP_INTERVAL_MS = 600000L // 10 minutes

    // Security
    const val MESSAGE_TIMEOUT_MS = 300000L // 5 minutes
    const val SECURITY_CLEANUP_INTERVAL_MS = 300000L // 5 minutes
    const val MAX_PROCESSED_MESSAGES = 10000
    const val MAX_PROCESSED_KEY_EXCHANGES = 1000

    // Noise Protocol
    const val REKEY_TIME_LIMIT_MS = 3600000L // 1 hour
    const val REKEY_MESSAGE_LIMIT_ENCRYPTION = 1000L
    const val REKEY_MESSAGE_LIMIT_SESSION = 10000L
    const val MAX_PAYLOAD_SIZE_BYTES = 256
    const val HIGH_NONCE_WARNING_THRESHOLD = 1000000000L

    // Mesh
    const val STALE_PEER_TIMEOUT_MS = 180000L // 3 minutes
    const val PEER_CLEANUP_INTERVAL_MS = 60000L // 1 minute
    const val CONNECTION_RETRY_DELAY_MS = 5000L
    const val MAX_CONNECTION_ATTEMPTS = 3
    const val RSSI_UPDATE_INTERVAL_MS = 5000L
}
```

## Error Handling

### Packet Validation

```kotlin
fun validatePacket(packet: BitchatPacket): ValidationResult {
    // Check version
    if (packet.version != 1u && packet.version != 2u) {
        return ValidationResult.INVALID_VERSION
    }

    // Check senderID
    if (packet.senderID.size != 8) {
        return ValidationResult.INVALID_SENDER_ID
    }

    // Check recipientID if present
    if (packet.recipientID != null && packet.recipientID.size != 8) {
        return ValidationResult.INVALID_RECIPIENT_ID
    }

    // Check signature if present
    if (packet.signature != null && packet.signature.size != 64) {
        return ValidationResult.INVALID_SIGNATURE
    }

    // Check TTL
    if (packet.ttl > MAX_TTL) {
        return ValidationResult.INVALID_TTL
    }

    // Check payload size
    val maxPayloadSize = if (packet.version >= 2u) {
        0xFFFFFFFFL // ~4 GiB
    } else {
        0xFFFFL // 64 KiB
    }
    if (packet.payload.size > maxPayloadSize) {
        return ValidationResult.PAYLOAD_TOO_LARGE
    }

    return ValidationResult.VALID
}
```

## References

- [High-Level Design Document](PROTOCOL_HLD.md) - Protocol overview
- [Source Routing](SOURCE_ROUTING.md) - Source routing extension
- [Gossip Sync](sync.md) - Synchronization protocol
- [File Transfer](file_transfer.md) - File transfer specification
- [Announcement Gossip](ANNOUNCEMENT_GOSSIP.md) - Topology gossip


