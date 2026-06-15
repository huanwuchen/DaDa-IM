package com.dada.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dada.core.database.entity.ImMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * IM 消息 DAO
 *
 * 所有查询都按 conversationId 过滤；conversationId = 对方的 userId
 *
 * 排序策略：
 *  - 主键序：timestamp ASC（按发送方打的时间戳）
 *  - 二级序：seq ASC（本地入库顺序，保证同一毫秒多条消息也按入库顺序展示）
 *
 * 这样既能尽量保留消息真实时间感，又能避免高频发送时排序不稳定。
 */
@Dao
interface ImMessageDao {

    /**
     * 监听某个会话的消息（升序，最旧在前）
     */
    @Query(
        "SELECT * FROM im_messages WHERE conversationId = :conversationId " +
                "ORDER BY timestamp ASC, seq ASC"
    )
    fun observeMessages(conversationId: Long): Flow<List<ImMessageEntity>>

    /**
     * 一次性获取某个会话的全部消息
     */
    @Query(
        "SELECT * FROM im_messages WHERE conversationId = :conversationId " +
                "ORDER BY timestamp ASC, seq ASC"
    )
    suspend fun getMessages(conversationId: Long): List<ImMessageEntity>

    /**
     * 按业务 ID 查询是否已经入库（用于消息去重）
     */
    @Query("SELECT * FROM im_messages WHERE id = :messageId LIMIT 1")
    suspend fun findById(messageId: String): ImMessageEntity?

    /**
     * 插入单条消息（id 重复时忽略，防止 WebSocket 回显造成重复）
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: ImMessageEntity): Long

    /**
     * 批量插入
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<ImMessageEntity>)

    /**
     * 清空某个会话的全部消息
     */
    @Query("DELETE FROM im_messages WHERE conversationId = :conversationId")
    suspend fun clearByConversation(conversationId: Long)

    /**
     * 更新某个会话中对方消息的头像（对方更换头像后补全历史消息，仅更新非自己发送的消息）
     */
    @Query("UPDATE im_messages SET avatar = :avatar WHERE conversationId = :conversationId AND isMine = 0 AND (avatar IS NULL OR avatar = '')")
    suspend fun updateAvatarByConversation(conversationId: Long, avatar: String)

    /**
     * 清空全部消息（用户登出时使用）
     */
    @Query("DELETE FROM im_messages")
    suspend fun clearAll()
}
