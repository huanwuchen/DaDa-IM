package com.dada.app.network.websocket

import com.dada.core.common.utils.LogUtil
import com.dada.core.network.websocket.WebSocketListener
import com.dada.core.network.websocket.MessageModel
import com.dada.core.database.entity.ImContactEntity
import com.dada.core.database.UserPreferences
import com.dada.domain.chat.model.ChatPeer
import com.dada.domain.chat.repository.ImChatRepository
import com.dada.domain.contact.repository.ImContactRepository
import com.dada.core.common.utils.AppForegroundTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 全局消息持久化拦截器
 *
 * 监听 WebSocket 收到的每一条聊天消息，无论用户是否打开聊天页都把消息写入 Room。
 * 这样能保证：
 *  - 首页消息列表（来自 im_conversations）即时更新
 *  - 重新进入某个会话时，能从本地直接看到所有历史
 *
 * 由 [WebSocketService.onCreate] 注册，[WebSocketService.onDestroy] 取消。
 */
class GlobalMessagePersister(
    private val chatRepository: ImChatRepository,
    private val contactRepository: ImContactRepository,
    private val userPreferences: UserPreferences,
) : WebSocketListener {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onConnected() = Unit
    override fun onDisconnected() = Unit
    override fun onConnectFailed(error: String) = Unit

    override fun onMessageReceived(message: MessageModel) {
        if (message.type in NON_CHAT_TYPES) return

        val from = message.fromId ?: return
        val to = message.toId ?: return
        val myUserId = userPreferences.getUserId()
        if (myUserId <= 0L) return

        // 只关心和「我」相关的消息
        val isInbound = (to == myUserId)
        val isOutboundEcho = (from == myUserId)
        if (!isInbound && !isOutboundEcho) return

        // peerId = 对端 ID
        val peerId = if (isInbound) from else to

        // 只对「真正接收的消息」入库；自己的回显已经在发送时入库，避免重复
        if (!isInbound) return

        // 根据是否在该会话页面决定是否累加未读数
        val incUnread = !(AppForegroundTracker.isForeground &&
                AppForegroundTracker.getCurrentChatUserId() == peerId)

        scope.launch {
            try {
                var contact = contactRepository.getContact(peerId)
                if (contact == null) {
                    // 未知联系人，先创建最小记录，等 refreshOnlineUsers 补全头像
                    contact = ImContactEntity(
                        id = peerId,
                        username = "用户$peerId",
                    )
                    contactRepository.upsert(contact)
                }
                val peer = ChatPeer(
                    id = peerId,
                    username = contact.username.ifBlank { "用户$peerId" },
                    avatar = contact.avatar,
                )
                chatRepository.saveIncomingMessage(myUserId, peer, message, incUnread)
            } catch (e: Exception) {
                LogUtil.e(TAG, "持久化消息失败: ${e.message}", e)
            }
        }
    }

    /**
     * 释放：通常在 Service 销毁时调用
     */
    fun release() {
        scope.cancel()
    }

    companion object {
        private const val TAG = "GlobalMessagePersister"

        /** 这些类型不入库（系统消息 / 心跳 / 通话信令） */
        private val NON_CHAT_TYPES = setOf(
            "system", "heartbeat",
            "call-invite", "call-accept", "call-reject", "call-hangup",
        )
    }
}
