# Real-Time Voice Calls Design

## Overview

Add real-time voice calls (like phone calls) to Bitchat, allowing users to have live conversations over the mesh network instead of sending voice clips.

## Current State

- **Voice clips**: Uses `MediaRecorder` to record to M4A/AAC files (16kHz, 20kbps)
- **File transfer**: Already supports large file transfers with fragmentation
- **Mesh network**: Supports real-time message delivery with encryption (Noise protocol)

## Architecture

### Components Needed

1. **VoiceCallManager**: Coordinates call state, signaling, audio streams
2. **AudioStreamRecorder**: Captures audio in real-time (AudioRecord)
3. **AudioStreamPlayer**: Plays audio in real-time (AudioTrack)
4. **AudioCodec**: Encodes/decodes audio (Opus codec recommended)
5. **Call Signaling**: New message types for call setup/teardown

### Message Types (New)

Add to `MessageType` enum:

- `VOICE_CALL_INVITE`: Initiates a call (contains call ID, codec info)
- `VOICE_CALL_ACCEPT`: Accepts an incoming call
- `VOICE_CALL_REJECT`: Rejects an incoming call
- `VOICE_CALL_END`: Ends an active call
- `VOICE_CALL_AUDIO`: Streamed audio chunks (encrypted, fragmented)

### Audio Streaming Protocol

1. **Codec**: Opus (optimized for real-time, low latency, good quality at low bitrates)
   - Sample rate: 16kHz (matches current voice clips)
   - Bitrate: 24-32 kbps (slightly higher than voice clips for better quality)
   - Frame size: 20-40ms (balance latency vs efficiency)
   - Channels: Mono (speech doesn't need stereo)

2. **Chunking**:
   - Encode audio into small chunks (e.g., 40ms = 640 samples at 16kHz)
   - Each chunk ≈ 160 bytes (32 kbps * 0.04s / 8 = 160 bytes)
   - Send as `VOICE_CALL_AUDIO` messages
   - Use existing fragmentation if chunks exceed MTU

3. **Streaming**:
   - Continuous send/receive loop during active call
   - Buffer 2-3 chunks to handle network jitter
   - Use circular buffer for playback
   - Sequence numbers for reordering (though BLE should preserve order)

### Call Flow

```
Caller                          Callee
  |                               |
  |-- VOICE_CALL_INVITE --------->|  (with callID, codec params)
  |                               |  [Show incoming call UI]
  |<-- VOICE_CALL_ACCEPT ---------|  (or VOICE_CALL_REJECT)
  |                               |
  |-- Start audio stream ---------|  (start sending VOICE_CALL_AUDIO)
  |<-- Start audio stream --------|  (start sending VOICE_CALL_AUDIO)
  |                               |
  |<== Audio chunks (bidirectional) ==>
  |                               |
  |-- VOICE_CALL_END ------------>|  (or either side can end)
  |                               |
```

### Implementation Steps

#### Phase 1: Audio Infrastructure

1. **AudioStreamRecorder.kt**
   - Use `AudioRecord` instead of `MediaRecorder`
   - Capture PCM samples at 16kHz, 16-bit, mono
   - Stream to Opus encoder
   - Output encoded chunks

2. **AudioStreamPlayer.kt**
   - Use `AudioTrack` for playback
   - Receive Opus-encoded chunks
   - Decode to PCM
   - Play through audio buffer

3. **OpusCodec.kt**
   - Wrap Opus codec (use native library like `libopus`)
   - Encode: PCM → Opus
   - Decode: Opus → PCM
   - Handle codec initialization/cleanup

#### Phase 2: Call Signaling

1. **VoiceCallManager.kt**
   - Manage call state (IDLE, INVITING, RINGING, ACTIVE, ENDED)
   - Handle call signaling messages
   - Coordinate audio streams
   - Track active calls (only one at a time per peer)

2. **Call Signaling Messages**
   - Extend `BluetoothMeshService` with call methods:
     - `inviteToCall(peerID)`
     - `acceptCall(callID)`
     - `rejectCall(callID)`
     - `endCall(callID)`

3. **Call UI**
   - Incoming call screen (fullscreen overlay)
   - Active call screen (minimizable)
   - Call controls (mute, speaker, end)

#### Phase 3: Audio Streaming

1. **Stream Audio Chunks**
   - During active call, continuously:
     - Record → Encode → Send `VOICE_CALL_AUDIO`
     - Receive `VOICE_CALL_AUDIO` → Decode → Play
   - Use coroutines for async streaming
   - Handle network interruptions gracefully

2. **Audio Quality**
   - Echo cancellation (Android's `AcousticEchoCanceler`)
   - Noise suppression (`NoiseSuppressor`)
   - Automatic gain control (optional)

#### Phase 4: Polish

1. **Call Notifications**
   - Foreground service for active calls
   - Notification with call controls
   - System audio routing (speaker/earpiece/bluetooth)

2. **Error Handling**
   - Network disconnection during call
   - Codec errors
   - Audio permission revocation
   - Battery optimization handling

## Technical Considerations

### Opus Codec

**Option 1: Native library** (recommended)
- Use `libopus` (C library, widely available)
- JNI wrapper or use existing Android bindings
- Better performance, lower latency

**Option 2: Java implementation**
- `java-opus` or similar (may have higher latency)
- Easier integration, but less optimal

### Latency Targets

- End-to-end latency: < 200ms (acceptable for voice)
- BLE transmission: ~50-100ms per hop
- Encoding/decoding: < 20ms per chunk
- Buffering: 2-3 chunks (~80-120ms)

### Network Considerations

- BLE mesh can have variable latency
- May need adaptive buffering based on network conditions
- Handle packet loss gracefully (small gaps acceptable)
- Consider priority queuing for voice packets

### Battery Impact

- Continuous audio recording/playback is battery-intensive
- Active calls should prevent device sleep
- Consider lower quality modes for battery savings
- Monitor battery level and warn users

## Alternative: Simplified Approach

If Opus integration is complex, could start with:

1. **PCM streaming** (no codec)
   - Raw PCM at 16kHz, 16-bit, mono
   - Larger chunks (~1280 bytes per 40ms)
   - Requires fragmentation, but simpler implementation

2. **G.711 μ-law** (simple codec)
   - 8-bit, 64 kbps
   - Very low CPU usage
   - Standard on Android (`AudioFormat.ENCODING_PCM_8BIT`)
   - Larger than Opus but simpler

## References

- Android AudioRecord: https://developer.android.com/reference/android/media/AudioRecord
- Android AudioTrack: https://developer.android.com/reference/android/media/AudioTrack
- Opus codec: https://opus-codec.org/
- BLE MTU limitations: Current implementation handles up to 512 bytes with fragmentation

## Future Enhancements

- Video calls (more complex, requires video codec)
- Group calls (multiple participants)
- Call recording
- Call history
- Call quality metrics

