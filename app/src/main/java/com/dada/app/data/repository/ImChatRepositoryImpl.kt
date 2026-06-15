package com.dada.app.data.repository

import com.dada.core.database.UserPreferences
import com.dada.core.database.dao.ImConversationDao
import com.dada.core.database.dao.ImMessageDao
import com.dada.core.database.entity.ImConversationEntity
import com.dada.core.database.entity.ImMessageEntity
import com.dada.core.network.model.UploadResponse
import com.dada.core.common.domain.model.Message
import com.dada.core.database.entity.toDomain
import com.dada.core.network.websocket.MessageModel
import com.dada.core.network.websocket.WebSocketManager
import com.dada.domain.chat.model.ChatPeer
import com.dada.domain.chat.model.MessageType
import com.dada.domain.chat.model.SendMessageResult
import com.dada.domain.chat.repository.ImChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IM 聊天 Repository 实现（实现 [com.dada.domain.chat.repository.ImChatRepository] 接口）
 *
 * 职责（纯数据层）：
 *  - 消息表 / 会话表的 CRUD
 *  - 接收消息入库
 *  - 通话提示消息入库
 *
 * 发送业务编排逻辑在 [com.dada.app.domain.SendMessageUseCase] 中。
 */
@Singleton
class ImChatRepositoryImpl @Inject constructor(
    private val messageDao: ImMessageDao,
    private val conversationDao: ImConversationDao,
    private val userPreferences: UserPreferences,
    private val webSocketManager: WebSocketManager,
) : ImChatRepository {

    override fun observeMessages(peerId: Long): Flow<List<Message>> =
        messageDao.observeMessages(peerId).map { list -> list.map { it.toDomain() } }

    override fun observeConversations(): Flow<List<ImConversationEntity>> =
        conversationDao.observeConversations()

    override suspend fun sendTextMessage(
        myUserId: Long,
        peer: ChatPeer,
        content: String,
    ): Boolean {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val entity = ImMessageEntity(
            id = messageId,
            conversationId = peer.id,
            fromId = myUserId,
            toId = peer.id,
            content = content,
            type = MessageType.TEXT,
            timestamp = now,
            isMine = true,
            avatar = userPreferences.getUserAvatar(),
        )
        messageDao.insert(entity)
        upsertConversation(peer, content, MessageType.TEXT, now, incUnread = false)

        return webSocketManager.sendMessage(
            MessageModel(
                id = messageId,
                fromId = myUserId,
                toId = peer.id,
                content = content,
                type = MessageType.TEXT,
                timestamp = now,
                avatar = userPreferences.getUserAvatar(),
            )
        )
    }

    override suspend fun persistAndPushMedia(
        myUserId: Long,
        peer: ChatPeer,
        type: String,
        response: UploadResponse,
        previewText: String,
        wsContent: String?,
    ): SendMessageResult {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val entity = ImMessageEntity(
            id = messageId,
            conversationId = peer.id,
            fromId = myUserId,
            toId = peer.id,
            content = response.url,
            type = type,
            timestamp = now,
            isMine = true,
            thumbUrl = response.thumbUrl,
            duration = response.duration,
            size = response.size,
            width = response.width,
            height = response.height,
            fileName = response.fileName,
            avatar = userPreferences.getUserAvatar(),
        )
        messageDao.insert(entity)
        upsertConversation(peer, previewText, type, now, incUnread = false)

        val sent = webSocketManager.sendMessage(
            MessageModel(
                id = messageId,
                fromId = myUserId,
                toId = peer.id,
                content = wsContent ?: response.url,
                type = type,
                timestamp = now,
                thumbUrl = response.thumbUrl,
                duration = response.duration,
                size = response.size,
                width = response.width,
                height = response.height,
                fileName = response.fileName,
                avatar = userPreferences.getUserAvatar(),
            )
        )
        return SendMessageResult(messageId = messageId, delivered = sent)
    }

    override suspend fun saveIncomingMessage(
        myUserId: Long,
        peer: ChatPeer,
        message: MessageModel,
        incUnread: Boolean,
    ) {
        val from = message.fromId ?: return
        val to = message.toId ?: return
        val localTime = System.currentTimeMillis()

        val decoded = if (message.type == MessageType.FILE) decodeFileContent(message.content) else null

        val entity = ImMessageEntity(
            id = message.id.ifBlank { UUID.randomUUID().toString() },
            conversationId = peer.id,
            fromId = from,
            toId = to,
            content = decoded?.url ?: message.content,
            type = message.type,
            timestamp = localTime,
            isMine = (from == myUserId),
            thumbUrl = message.thumbUrl,
            duration = message.duration,
            size = decoded?.size ?: message.size,
            width = message.width,
            height = message.height,
            fileName = decoded?.fileName ?: message.fileName,
            avatar = message.avatar ?: peer.avatar,
        )
        messageDao.insert(entity)
        upsertConversation(peer, previewFromMessage(message), message.type, localTime, incUnread)
    }

    override suspend fun saveCallHintMessage(
        myUserId: Long,
        peerId: Long,
        content: String,
        iconRes: Int,
    ) {
        val entity = ImMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = peerId,
            fromId = myUserId,
            toId = peerId,
            content = content,
            type = MessageType.CALL_HINT,
            timestamp = System.currentTimeMillis(),
            isMine = true,
            iconRes = iconRes,
        )
        messageDao.insert(entity)
    }

    override suspend fun markAsRead(peerId: Long) = conversationDao.clearUnread(peerId)

    override suspend fun updateMessageAvatar(peerId: Long, avatar: String) =
        messageDao.updateAvatarByConversation(peerId, avatar)

    override suspend fun deleteConversation(peerId: Long) {
        messageDao.clearByConversation(peerId)
        conversationDao.delete(peerId)
    }

    override suspend fun clearAll() {
        messageDao.clearAll()
        conversationDao.clearAll()
    }

    // ============================== 内部 ==============================

    private fun previewFromMessage(message: MessageModel): String = when (message.type) {
        MessageType.IMAGE -> "[图片]"
        MessageType.VIDEO -> "[视频]"
        MessageType.AUDIO -> "[语音]"
        MessageType.FILE -> {
            val name = message.fileName ?: decodeFileContent(message.content)?.fileName
            "[文件] ${name.orEmpty()}"
        }
        else -> message.content
    }

    private fun decodeFileContent(content: String): DecodedFile? {
        val parts = content.split("|", limit = 3)
        if (parts.size < 3) return null
        return DecodedFile(
            url = parts[0],
            size = parts[1].toLongOrNull() ?: 0L,
            fileName = parts[2].takeIf { it.isNotBlank() },
        )
    }

    private data class DecodedFile(val url: String, val size: Long, val fileName: String?)

    private suspend fun upsertConversation(
        peer: ChatPeer,
        lastContent: String,
        lastType: String,
        lastTime: Long,
        incUnread: Boolean,
    ) {
        val existing = conversationDao.getByPeerId(peer.id)
        val unread = (existing?.unreadCount ?: 0) + if (incUnread) 1 else 0
        // 当 existing == null 时 shouldOverrideLast 必为 true，进入新建分支；
        // 否则 existing 非空，使用其字段做合并
        val finalLast = if (existing == null || lastTime >= existing.lastMessageTime) {
            Triple(lastContent, lastTime, lastType)
        } else {
            Triple(existing.lastMessage, existing.lastMessageTime, existing.lastMessageType)
        }

        conversationDao.upsert(
            ImConversationEntity(
                peerId = peer.id,
                peerUsername = peer.username.ifBlank {
                    existing?.peerUsername ?: "用户${peer.id}"
                },
                peerAvatar = peer.avatar ?: existing?.peerAvatar,
                lastMessage = finalLast.first,
                lastMessageTime = finalLast.second,
                lastMessageType = finalLast.third,
                unreadCount = unread,
            )
        )
    }
}
