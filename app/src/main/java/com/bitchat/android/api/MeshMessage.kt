package com.bitchat.android.api

import android.os.Parcel
import android.os.Parcelable

/**
 * Message object for mesh API
 * Used for game moves, chat, or any custom data
 */
data class MeshMessage(
    val peerID: String,           // Sender peer ID (16 hex chars)
    val messageType: String,      // Message type: "game_move", "chat", "custom", etc.
    val payload: ByteArray,       // Message payload
    val timestamp: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false  // Whether message was encrypted
) : Parcelable {
    constructor(parcel: Parcel) : this(
        peerID = parcel.readString() ?: "",
        messageType = parcel.readString() ?: "",
        payload = parcel.createByteArray() ?: ByteArray(0),
        timestamp = parcel.readLong(),
        isEncrypted = parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(peerID)
        parcel.writeString(messageType)
        parcel.writeByteArray(payload)
        parcel.writeLong(timestamp)
        parcel.writeByte(if (isEncrypted) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<MeshMessage> {
        override fun createFromParcel(parcel: Parcel): MeshMessage = MeshMessage(parcel)
        override fun newArray(size: Int): Array<MeshMessage?> = arrayOfNulls(size)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MeshMessage
        if (peerID != other.peerID) return false
        if (messageType != other.messageType) return false
        if (!payload.contentEquals(other.payload)) return false
        if (timestamp != other.timestamp) return false
        if (isEncrypted != other.isEncrypted) return false
        return true
    }

    override fun hashCode(): Int {
        var result = peerID.hashCode()
        result = 31 * result + messageType.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isEncrypted.hashCode()
        return result
    }
}

