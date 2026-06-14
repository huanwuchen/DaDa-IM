package com.dada.core.database

import com.dada.core.database.dao.ChatMessageDao
import com.dada.core.database.entity.toDomain
import com.dada.core.database.entity.toEntity
import com.dada.core.common.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聊天本地数据源
 *
 * 封装 Room DAO 操作，提供领域模型级别的数据访问接口
 */
@Singleton
class ChatLocalDataSource @Inject constructor(
    private val chatMessageDao: ChatMessageDao
) {
    /**
     * 获取所有消息（按时间戳升序）
     *
     * @return 消息列表的 Flow，数据变化时自动通知
     */
    fun getAllMessages(): Flow<List<ChatMessage>> =
        chatMessageDao.getAllMessages().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * 插入单条消息
     */
    suspend fun insertMessage(message: ChatMessage) {
        chatMessageDao.insertMessage(message.toEntity())
    }

    /**
     * 更新消息
     */
    suspend fun updateMessage(message: ChatMessage) {
        chatMessageDao.updateMessage(message.toEntity())
    }

    /**
     * 清空所有消息
     */
    suspend fun clearAll() {
        chatMessageDao.clearAll()
    }
}
