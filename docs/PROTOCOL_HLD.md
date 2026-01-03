# Bitchat Protocol - High-Level Design (HLD)

## Overview

Bitchat is a decentralized, peer-to-peer messaging protocol designed to operate over Bluetooth Low Energy (BLE) mesh networks. The protocol enables secure, encrypted communication without requiring internet connectivity, phone numbers, or centralized servers. It supports both local mesh networking and optional internet-based geohash channels.

## Core Principles

1. **Decentralization**: No central servers or infrastructure required
2. **Privacy**: End-to-end encryption with forward secrecy
3. **Resilience**: Store-and-forward messaging for offline peers
4. **Efficiency**: Optimized binary protocol for low-bandwidth BLE links
5. **Compatibility**: 100% protocol compatibility between iOS and Android implementations

## Architecture Overview

### Protocol Layers

```
┌─────────────────────────────────────────────────────────┐
│              Application Layer                          │
│  (Channels, Private Messages, File Transfers)           │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│              Security Layer                             │
│  (Noise Protocol, Channel Encryption, Signatures)       │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│              Protocol Layer                             │
│  (Packet Encoding, Routing, Fragmentation, Sync)        │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│              Transport Layer                            │
│  (Bluetooth LE GATT, Mesh Relay)                        │
└─────────────────────────────────────────────────────────┘
```

### Key Components

1. **BluetoothMeshService**: Core coordinator managing all mesh operations
2. **PeerManager**: Tracks peer lifecycle and connectivity
3. **SecurityManager**: Handles encryption, authentication, and duplicate detection
4. **MessageHandler**: Processes different message types and routing logic
5. **BluetoothConnectionManager**: Manages BLE connections (central + peripheral roles)
6. **FragmentManager**: Handles message fragmentation and reassembly
7. **StoreForwardManager**: Caches messages for offline peers
8. **GossipSyncManager**: Manages gossip-based synchronization using GCS filters
9. **PacketProcessor**: Routes incoming packets to appropriate handlers

## Network Topology

### Mesh Network Model

- **Ad-hoc Mesh**: Devices form an ad-hoc mesh network via Bluetooth LE
- **Dual Role**: Each device operates as both BLE Central and Peripheral simultaneously
- **Multi-hop Routing**: Messages can traverse up to 7 hops (configurable TTL)
- **Store-and-Forward**: Messages are cached and delivered when peers reconnect

### Peer Discovery

- **Periodic Announcements**: Peers broadcast identity announcements every 30 seconds
- **BLE Scanning**: Continuous scanning for nearby peers
- **Connection Management**: Automatic connection establishment and maintenance
- **Topology Learning**: Gossip protocol builds mesh topology view

## Protocol Features

### Message Types

1. **ANNOUNCE (0x01)**: Identity announcements with nickname, public keys, and neighbor gossip
2. **MESSAGE (0x02)**: User messages (both broadcast and private)
3. **LEAVE (0x03)**: Peer departure notifications
4. **NOISE_HANDSHAKE (0x10)**: Noise protocol handshake messages
5. **NOISE_ENCRYPTED (0x11)**: Encrypted transport messages using Noise
6. **FRAGMENT (0x20)**: Fragmented message chunks for large payloads
7. **REQUEST_SYNC (0x21)**: Gossip sync requests with GCS filters
8. **FILE_TRANSFER (0x22)**: File transfer packets (images, audio, etc.)

### Routing Mechanisms

#### Broadcast Routing
- **Flooding**: Broadcast messages flood the mesh network
- **TTL-based**: Time-to-live limits propagation (default: 7 hops)
- **Adaptive Probability**: Relay probability adapts to network size
  - Small networks (≤10 peers): 100% relay probability
  - Medium networks (10-30 peers): 85% relay probability
  - Large networks (30-50 peers): 70% relay probability
  - Very large networks (50-100 peers): 55% relay probability
  - Extremely large networks (>100 peers): 40% relay probability
- **Duplicate Detection**: Bloom filters prevent message loops

#### Source Routing (Optional)
- See [SOURCE_ROUTING.md](SOURCE_ROUTING.md) for detailed specification

### Security Model

#### Encryption Layers

1. **Noise Protocol**:
   - X25519 key exchange
   - Forward secrecy
   - Authenticated encryption
   - Used for private messages

2. **Channel Encryption**:
   - PBKDF2 key derivation (100,000 iterations)
   - AES-256-GCM encryption
   - Channel name as salt
   - Used for password-protected channels

3. **Digital Signatures**:
   - Ed25519 signatures
   - Packet authenticity verification
   - Prevents tampering

#### Security Properties

- **Forward Secrecy**: New key pairs generated per session
- **Authentication**: Ed25519 signatures verify message authenticity
- **Privacy**: No persistent identifiers, ephemeral peer IDs
- **Traffic Analysis Resistance**: Message padding to standard block sizes

### Synchronization

#### Gossip-Based Sync

- See [sync.md](sync.md) for detailed GCS filter synchronization specification
- **Periodic Sync**: Every 30 seconds to immediate neighbors
- **Per-Peer Sync**: Initial sync 5 seconds after first ANNOUNCE from new peer
- **Scope**: Only public messages (ANNOUNCE and broadcast MESSAGE)
- **Local-Only**: Sync packets use TTL=0 and are not relayed

#### Store-and-Forward

- **Message Caching**: Messages cached for offline peers
- **TTL-based Expiry**: Messages expire after 12 hours (43,200,000 ms)
- **Capacity Limits**: Max 100 cached messages (1000 for favorites)
- **Cleanup Interval**: Cache cleanup every 10 minutes
- **Automatic Delivery**: Cached messages sent when peer reconnects

## Protocol Versions

### Version 1 (v1)
- **Header Size**: 13 bytes
- **Payload Length**: 2 bytes (max 64 KiB)
- **Use Case**: Standard messages, announcements

### Version 2 (v2)
- **Header Size**: 15 bytes
- **Payload Length**: 4 bytes (max ~4 GiB)
- **Use Case**: Large file transfers
- **Backward Compatible**: Clients must support both versions

## Performance Optimizations

### Compression
- **LZ4 Compression**: Automatic compression for messages >100 bytes
- **30-70% Bandwidth Savings**: Typical compression ratios
- **Smart Detection**: Skips already-compressed data

### Fragmentation
- **Threshold**: Messages >512 bytes are fragmented
- **Fragment Size**: Max 469 bytes per fragment (BLE MTU constraints)
- **Reassembly**: Automatic reassembly at receiver
- **Timeout**: 30-second timeout for incomplete fragments

### Battery Optimization

- **Adaptive Scanning**: Scanning duty cycle adapts to battery level
  - **Performance Mode** (charging or >60% battery): 8s on / 2s off
  - **Balanced Mode** (30-60% battery): 8s on / 2s off (default)
  - **Power Saver** (<30% battery): 2s on / 8s off
  - **Ultra-Low Power** (<10% battery): 1s on / 10s off
- **Connection Limits**: Adaptive connection limits based on power mode
  - Normal/Power Saver: Max 8 connections
  - Ultra-Low Power: Max 4 connections
- **Background Efficiency**: Automatic power saving when app backgrounded

## Integration Points

### Nostr Protocol
- **Geohash Channels**: Internet-based channels using Nostr relays
- **NIP-17**: Private direct messages via gift-wrap encryption
- **Relay Selection**: Automatic relay discovery and connection
- **Tor Support**: Optional Tor proxy for enhanced privacy

### File Transfer
- See [file_transfer.md](file_transfer.md) for detailed file transfer specification
- **Supported Types**: Images, audio (voice notes), generic files
- **Protocol Version**: Always uses v2 format (4-byte payload length)
- **Max File Size**: ~4 GiB (limited by v2 payload length field)

## Network Behavior

### Connection Lifecycle

1. **Discovery**: BLE scanning discovers nearby peers
2. **Connection**: Automatic GATT connection establishment
3. **Announcement Exchange**: Peers exchange identity announcements
4. **Key Exchange**: Noise protocol handshake for private messaging
5. **Message Exchange**: Normal messaging operations
6. **Disconnection**: Graceful disconnection with LEAVE messages

### Peer Management

- **Stale Timeout**: Peers considered stale after 3 minutes (180,000 ms) of inactivity
- **Cleanup Interval**: Peer cleanup every 60 seconds
- **RSSI Tracking**: Signal strength monitoring every 5 seconds
- **Connection Retry**: Automatic retry with 5-second delay, max 3 attempts
- **Connection Cleanup**: Failed connections cleaned up after 500ms delay
- **Broadcast Cleanup**: Broadcast operations cleaned up after 500ms delay

### Message Flow

1. **Send**: Application creates message
2. **Encrypt**: Message encrypted (Noise or channel encryption)
3. **Encode**: Message encoded into binary packet
4. **Sign**: Packet signed with Ed25519 (if enabled)
5. **Fragment**: Large messages fragmented if needed
6. **Route**: Packet routed through mesh network
7. **Relay**: Intermediate peers relay packet (if TTL > 0)
8. **Receive**: Destination peer receives packet
9. **Verify**: Signature verified (if present)
10. **Decrypt**: Message decrypted
11. **Deliver**: Message delivered to application

## Error Handling

### Packet-Level Errors
- **Malformed Packets**: Silently dropped
- **Invalid Signatures**: Rejected
- **TTL Expired**: Not relayed
- **Duplicate Packets**: Filtered out

### Connection Errors
- **Connection Failures**: Automatic retry with backoff
- **Timeout**: Connection cleanup after timeout
- **BLE Errors**: Graceful degradation

### Application-Level Errors
- **Decryption Failures**: Message rejected
- **Missing Keys**: Handshake initiated
- **Fragment Timeouts**: Incomplete fragments discarded

## Scalability Considerations

### Network Size Limits
- **Small Networks (≤10 peers)**: Always relay (100% probability)
- **Medium Networks (10-30 peers)**: High relay probability (85%)
- **Large Networks (30-50 peers)**: Moderate relay probability (70%)
- **Very Large Networks (50-100 peers)**: Lower relay probability (55%)
- **Extremely Large Networks (>100 peers)**: Lowest relay probability (40%)

### Resource Management
- **Memory**: Bloom filters and message caches have size limits
- **CPU**: Compression and encryption operations optimized
- **Battery**: Adaptive power management based on battery level
- **Bandwidth**: Message aggregation and compression reduce overhead

## Compatibility

### Cross-Platform
- **iOS ↔ Android**: 100% protocol compatibility
- **Binary Format**: Identical packet encoding/decoding
- **Cryptography**: Same algorithms and parameters
- **UUIDs**: Identical Bluetooth service and characteristic UUIDs

### Version Compatibility
- **v1 ↔ v2**: Clients must support both versions
- **Backward Compatible**: New features are optional extensions
- **Graceful Degradation**: Missing features don't break communication

## Future Extensions

### Planned Features
- Enhanced source routing with dynamic path optimization
- Multi-path routing for improved reliability
- Quality-of-service prioritization
- Enhanced compression algorithms
- Additional file transfer types

### Extension Mechanism
- **TLV Format**: Type-Length-Value encoding allows extensibility
- **Flag Bits**: Reserved flag bits for future features
- **Unknown Types**: Unknown message types are ignored
- **Version Negotiation**: Protocol version in packet header

## References

- [Low-Level Design Document](PROTOCOL_LLD.md) - Detailed protocol specification
- [Source Routing](SOURCE_ROUTING.md) - Source routing extension
- [Gossip Sync](sync.md) - Synchronization protocol details
- [File Transfer](file_transfer.md) - File transfer protocol specification
- [Announcement Gossip](ANNOUNCEMENT_GOSSIP.md) - Topology gossip protocol

