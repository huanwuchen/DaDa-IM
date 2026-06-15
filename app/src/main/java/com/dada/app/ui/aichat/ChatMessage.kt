package com.dada.app.ui.aichat

import com.dada.core.database.entity.ChatMessageEntity
import java.util.UUID

data class ChatMessage(
    val role: String,
    var content: String,
    val isUser: Boolean,
    var reasoningContent: String? = null,
    /** Room 主键，为 null 表示尚未持久化 */
    var entityId: String? = null,
) {
    fun toEntity(): ChatMessageEntity = ChatMessageEntity(
        id = entityId ?: UUID.randomUUID().toString(),
        role = if (isUser) "USER" else "AI",
        type = "TEXT",
        text = content,
        timestamp = System.currentTimeMillis(),
    )

    companion object {
        fun fromEntity(entity: ChatMessageEntity): ChatMessage = ChatMessage(
            role = if (entity.role == "USER") "user" else "assistant",
            content = entity.text,
            isUser = entity.role == "USER",
            entityId = entity.id,
        )
    }
}
