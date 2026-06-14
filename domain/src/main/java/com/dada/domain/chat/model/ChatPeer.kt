package com.dada.domain.chat.model

/**
 * 聊天对象（私聊场景下的"对方"）
 *
 * 原 [com.dada.app.data.repository.ImChatRepository.PeerInfo]，作为 3.2 步迁入 :domain
 */
data class ChatPeer(
    val id: Long,
    val username: String = "",
    val avatar: String? = null,
)

/**
 * 消息发送结果
 */
data class SendMessageResult(
    val messageId: String,
    val delivered: Boolean,
)

/**
 * 消息类型常量（与 [Message.type] 字段对齐）
 */
object MessageType {
    const val TEXT = "text"
    const val IMAGE = "image"
    const val VIDEO = "video"
    const val AUDIO = "audio"
    const val FILE = "file"
    const val CALL_HINT = "call_hint"
}
