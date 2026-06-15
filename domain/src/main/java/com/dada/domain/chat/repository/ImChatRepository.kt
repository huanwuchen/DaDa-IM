package com.dada.domain.chat.repository

import com.dada.core.common.domain.model.Message
import com.dada.core.database.entity.ImConversationEntity
import com.dada.core.network.model.UploadResponse
import com.dada.core.network.websocket.MessageModel
import com.dada.domain.chat.model.ChatPeer
import com.dada.domain.chat.model.SendMessageResult
import kotlinx.coroutines.flow.Flow

/**
 * IM 聊天 Repository 接口
 *
 * ViewModel / UseCase 只面向此接口编程，具体实现见 :app/data/repository/ImChatRepositoryImpl。
 *
 * 备注（过渡）：
 *  - 暂保留对 [ImConversationEntity] / [UploadResponse] / [MessageModel] 的引用，
 *    属于渐进式重构的过渡，后续应进一步抽离独立的领域类型。
 */
interface ImChatRepository {

    fun observeMessages(peerId: Long): Flow<List<Message>>

    fun observeConversations(): Flow<List<ImConversationEntity>>

    suspend fun sendTextMessage(myUserId: Long, peer: ChatPeer, content: String): Boolean

    suspend fun persistAndPushMedia(
        myUserId: Long,
        peer: ChatPeer,
        type: String,
        response: UploadResponse,
        previewText: String,
        wsContent: String? = null,
    ): SendMessageResult

    suspend fun saveIncomingMessage(
        myUserId: Long,
        peer: ChatPeer,
        message: MessageModel,
        incUnread: Boolean,
    )

    suspend fun saveCallHintMessage(
        myUserId: Long,
        peerId: Long,
        content: String,
        iconRes: Int,
    )

    suspend fun markAsRead(peerId: Long)

    /**
     * 补全某个会话中头像为空的历史消息（对方更换头像后调用）
     */
    suspend fun updateMessageAvatar(peerId: Long, avatar: String)

    suspend fun deleteConversation(peerId: Long)

    suspend fun clearAll()
}
