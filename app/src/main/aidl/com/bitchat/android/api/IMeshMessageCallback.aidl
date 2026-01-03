package com.bitchat.android.api;

import com.bitchat.android.api.MeshMessage;

/**
 * Callback interface for receiving mesh messages
 */
interface IMeshMessageCallback {
    /**
     * Called when a message is received
     * @param message The received message
     */
    void onMessageReceived(in MeshMessage message);

    /**
     * Called when a peer connects/disconnects
     * @param peerID Peer ID
     * @param isConnected true if connected, false if disconnected
     */
    void onPeerConnectionChanged(String peerID, boolean isConnected);
}
