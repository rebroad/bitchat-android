package com.bitchat.android.api

import android.os.Parcel
import android.os.Parcelable

/**
 * Information about a peer in the mesh network
 */
data class PeerInfo(
    val peerID: String,           // Peer ID (16 hex chars)
    val nickname: String?,         // Optional nickname
    val isConnected: Boolean,      // Whether currently connected
    val lastSeen: Long = System.currentTimeMillis()  // Last seen timestamp
) : Parcelable {
    constructor(parcel: Parcel) : this(
        peerID = parcel.readString() ?: "",
        nickname = parcel.readString(),
        isConnected = parcel.readByte() != 0.toByte(),
        lastSeen = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(peerID)
        parcel.writeString(nickname)
        parcel.writeByte(if (isConnected) 1 else 0)
        parcel.writeLong(lastSeen)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<PeerInfo> {
        override fun createFromParcel(parcel: Parcel): PeerInfo = PeerInfo(parcel)
        override fun newArray(size: Int): Array<PeerInfo?> = arrayOfNulls(size)
    }
}

