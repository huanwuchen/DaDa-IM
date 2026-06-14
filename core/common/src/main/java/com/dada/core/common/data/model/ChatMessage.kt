package com.dada.core.common.data.model

import java.util.UUID

enum class ChatRole { USER, AI }
enum class ChatType { TEXT, VOICE }

/**
 * 微信聊天页面使用的消息模型。
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val type: ChatType,
    val text: String = "",
    val voicePath: String? = null,
    val voiceDurationSec: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isPlaying: Boolean = false,
)
