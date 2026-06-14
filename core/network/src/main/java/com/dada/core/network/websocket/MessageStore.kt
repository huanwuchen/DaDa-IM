package com.dada.core.network.websocket

/**
 * 消息持久化抽象。MessageManager 仅依赖此接口，由 app 层用 Room/DAO 实现。
 *
 * 状态机：
 *   SENDING ──ack──► SENT
 *           ──超出重试──► FAILED
 *
 * 实现要求：
 *  - insertOutgoing / insertIncoming 必须幂等（按 messageId 唯一约束）
 *  - loadUnacked() 仅在 MessageManager 启动时调用，用于进程重启后兜底重发
 */
interface MessageStore {

    /** 发送前落库（status=SENDING）。 */
    suspend fun insertOutgoing(message: MessageModel)

    /** 收到对端业务消息后落库。返回是否为新消息（false 表示重复）。 */
    suspend fun insertIncoming(message: MessageModel): Boolean

    /** ack 到达，把消息标记为 SENT。 */
    suspend fun markSent(messageId: String)

    /** 超出最大重试，标记为 FAILED，UI 显示"重发"按钮。 */
    suspend fun markFailed(messageId: String)

    /** 启动时加载未确认消息（status=SENDING），由 Manager 重新入队。 */
    suspend fun loadUnacked(): List<MessageModel>

    /** 提供一个空实现，方便集成阶段不接 Room。 */
    object Noop : MessageStore {
        override suspend fun insertOutgoing(message: MessageModel) = Unit
        override suspend fun insertIncoming(message: MessageModel): Boolean = true
        override suspend fun markSent(messageId: String) = Unit
        override suspend fun markFailed(messageId: String) = Unit
        override suspend fun loadUnacked(): List<MessageModel> = emptyList()
    }
}
