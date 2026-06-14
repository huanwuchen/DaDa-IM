package com.dada.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dada.core.database.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 聊天消息 DAO
 *
 * 提供聊天消息的数据库操作方法
 */
@Dao
interface ChatMessageDao {

    /**
     * 获取所有消息（按时间戳升序排列）
     *
     * @return 消息列表的 Flow，数据变化时自动通知
     */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    /**
     * 插入单条消息（冲突时替换）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    /**
     * 批量插入消息（冲突时替换）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    /**
     * 更新消息
     */
    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    /**
     * 删除消息
     */
    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)

    /**
     * 清空所有消息
     */
    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}
