package com.dada.app.ui.chatlist

import com.dada.core.database.entity.ImConversationEntity

/**
 * 首页消息列表项的 UI 模型
 *
 * @property peerId          对方用户 ID
 * @property peerUsername    对方昵称
 * @property peerAvatar      对方头像
 * @property lastMessage     最后一条消息内容
 * @property lastMessageTime 最后一条消息时间戳
 * @property unreadCount     未读数（0 表示无未读）
 */
data class ChatListItem(
    val peerId: Long,
    val peerUsername: String,
    val peerAvatar: String? = null,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int = 0,
)

/**
 * Entity -> UI 模型
 */
fun ImConversationEntity.toChatListItem(): ChatListItem = ChatListItem(
    peerId = peerId,
    peerUsername = peerUsername,
    peerAvatar = peerAvatar,
    lastMessage = lastMessage,
    lastMessageTime = lastMessageTime,
    unreadCount = unreadCount,
)
