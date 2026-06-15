package com.dada.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * IM 会话 Entity
 *
 * 一条记录代表「我 与 某个对方」的一条会话。
 * 收发消息时 Repository 会同步刷新此表的 lastMessage 字段。
 *
 * @property peerId           对方用户 ID（同时也是会话主键）
 * @property peerUsername     对方昵称（冗余存储，用于会话列表展示）
 * @property peerAvatar       对方头像 URL（冗余存储）
 * @property lastMessage      最后一条消息内容
 * @property lastMessageTime  最后一条消息时间戳（用于排序）
 * @property lastMessageType  最后一条消息类型（text/image/voice）
 * @property unreadCount      未读消息数量
 */
@Entity(tableName = "im_conversations")
data class ImConversationEntity(
    @PrimaryKey
    val peerId: Long,
    val peerUsername: String,
    val peerAvatar: String? = null,
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val lastMessageType: String = "text",
    val unreadCount: Int = 0,
)
