package com.dada.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 聊天消息 Room Entity
 *
 * 用于持久化存储聊天消息到本地数据库
 *
 * @property id 消息唯一标识（UUID）
 * @property role 消息角色："USER" 或 "AI"
 * @property type 消息类型："TEXT" 或 "VOICE"
 * @property text 消息文本内容
 * @property voicePath 语音文件路径（语音消息时有值）
 * @property voiceDurationSec 语音时长（秒）
 * @property timestamp 消息时间戳
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val role: String,
    val type: String,
    val text: String = "",
    val voicePath: String? = null,
    val voiceDurationSec: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
)
