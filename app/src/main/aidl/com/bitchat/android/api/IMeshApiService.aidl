package com.bitchat.android.api;

import com.bitchat.android.api.MeshMessage;
import com.bitchat.android.api.PeerInfo;
import com.bitchat.android.api.IMeshMessageCallback;

/**
 * AIDL interface for Bitchat Mesh API
 * Allows other apps to send/receive messages over the mesh network
 */
interface IMeshApiService {
    /**
     * Send a message to a specific peer (encrypted)
     * @param peerID Target peer ID (16 hex chars)
     * @param messageType Message type (e.g., "game_move", "chat", "custom")
     * @param payload Message payload as byte array
     * @return true if message was queued successfully
     */
    boolean sendMessageToPeer(String peerID, String messageType, in byte[] payload);

    /**
     * Broadcast a message to all peers (unencrypted)
     * @param messageType Message type
     * @param payload Message payload as byte array
     * @return true if message was queued successfully
     */
    boolean broadcastMessage(String messageType, in byte[] payload);

    /**
     * Get list of currently connected peers
     * @return Array of PeerInfo objects
     */
    PeerInfo[] getConnectedPeers();

    /**
     * Get my own peer ID
     * @return My peer ID (16 hex chars)
     */
    String getMyPeerID();

    /**
     * Register a callback for receiving messages
     * @param callback Callback interface for message delivery
     */
    void registerMessageCallback(IMeshMessageCallback callback);

    /**
     * Unregister message callback
     * @param callback Callback to unregister
     */
    void unregisterMessageCallback(IMeshMessageCallback callback);

    /**
     * Check if mesh service is active
     * @return true if mesh is running
     */
    boolean isMeshActive();
}

