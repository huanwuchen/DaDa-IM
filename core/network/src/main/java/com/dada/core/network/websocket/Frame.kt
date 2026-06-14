package com.dada.core.network.websocket

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * 可靠 IM 协议帧。
 *
 * 协议层面上每个帧都带 messageId（id 字段）。type 区分用途：
 *  - msg / text / image / ...：业务消息（兼容旧 MessageModel.type）
 *  - ack：对端确认收到 messageId
 *  - heartbeat / heartbeat_ack：心跳与心跳确认
 *
 * 服务端短期内可能仍按旧协议回包（无 type=ack），需要时再扩展。
 */
data class Frame(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),

    @SerializedName("type")
    val type: String = TYPE_TEXT,

    @SerializedName("ackId")
    val ackId: String? = null,

    @SerializedName("payload")
    val payload: MessageModel? = null,
) {
    val isAck: Boolean get() = type == TYPE_ACK
    val isHeartbeat: Boolean get() = type == TYPE_HEARTBEAT
    val isHeartbeatAck: Boolean get() = type == TYPE_HEARTBEAT_ACK

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_ACK = "ack"
        const val TYPE_HEARTBEAT = "heartbeat"
        const val TYPE_HEARTBEAT_ACK = "heartbeat_ack"

        fun ack(messageId: String): Frame =
            Frame(id = UUID.randomUUID().toString(), type = TYPE_ACK, ackId = messageId)

        fun heartbeat(id: String = UUID.randomUUID().toString()): Frame =
            Frame(id = id, type = TYPE_HEARTBEAT)

        fun heartbeatAck(pingId: String): Frame =
            Frame(id = UUID.randomUUID().toString(), type = TYPE_HEARTBEAT_ACK, ackId = pingId)
    }
}
