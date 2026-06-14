package com.dada.core.network.websocket

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * WebSocket 消息模型
 *
 * 普通消息：fromId/toId 必有
 * 系统消息：fromId/toId 为 null，只有 type 和 content
 *
 * 媒体消息字段说明（type ≠ text 时使用）：
 *  - content：服务端返回的文件 URL（图片/视频/语音/文件）
 *  - thumbUrl：图片/视频缩略图（可选）
 *  - duration：语音/视频时长（毫秒）
 *  - size：文件字节数
 *  - width/height：图片/视频尺寸
 *  - fileName：原始文件名（仅 file 类型用于展示）
 */
data class MessageModel(
    @SerializedName(value = "id", alternate = ["messageId"])
    val id: String = UUID.randomUUID().toString(),

    @SerializedName("fromId")
    val fromId: Long? = null,

    @SerializedName("toId")
    val toId: Long? = null,

    @SerializedName("content")
    val content: String = "",

    @SerializedName("type")
    val type: String = "text",

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    // ============================== 媒体扩展字段 ==============================

    @SerializedName("thumbUrl")
    val thumbUrl: String? = null,

    @SerializedName("duration")
    val duration: Long = 0,

    @SerializedName("size")
    val size: Long = 0,

    @SerializedName("width")
    val width: Int = 0,

    @SerializedName("height")
    val height: Int = 0,

    @SerializedName("fileName")
    val fileName: String? = null,

    @SerializedName("avatar")
    val avatar: String? = null,
)

/**
 * 心跳消息
 */
data class PingMessage(
    @SerializedName("type")
    val type: String = "heartbeat",

    @SerializedName("content")
    val content: String = "ping"
)

