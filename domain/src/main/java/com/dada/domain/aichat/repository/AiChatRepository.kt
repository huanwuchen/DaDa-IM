package com.dada.domain.aichat.repository

import com.dada.core.database.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

interface AiChatRepository {

    /** 观察历史消息（按时间升序） */
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    /** 插入一条消息 */
    suspend fun insertMessage(message: ChatMessageEntity)

    /** 更新一条消息 */
    suspend fun updateMessage(message: ChatMessageEntity)

    /**
     * 发送消息并获取流式 AI 回复。
     *
     * 内部完成：
     *  - 持久化用户消息到 Room
     *  - 调用 MiMo API 获取流式回复
     *  - 返回 Flow，每个 emission 包含当前累积的完整内容
     *
     * @param content 用户消息文本
     * @return 流式回复的 Flow，完成时自动结束
     */
    fun sendMessage(content: String): Flow<AiStreamChunk>

    /** 取消当前进行中的请求 */
    fun cancelRequest()
}

/**
 * 流式回复的数据块。
 * [content] 和 [reasoning] 始终是**累积的完整文本**，不是增量。
 */
data class AiStreamChunk(
    val content: String = "",
    val reasoning: String = "",
)
