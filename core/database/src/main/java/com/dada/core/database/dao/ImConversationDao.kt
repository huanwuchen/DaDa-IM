package com.dada.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dada.core.database.entity.ImConversationEntity
import kotlinx.coroutines.flow.Flow

/**
 * IM 会话列表 DAO
 *
 * 一条记录代表一个「与某人」的会话，按最后消息时间倒序排列。
 */
@Dao
interface ImConversationDao {

    /**
     * 监听全部会话（按最后消息时间倒序）
     */
    @Query("SELECT * FROM im_conversations ORDER BY lastMessageTime DESC")
    fun observeConversations(): Flow<List<ImConversationEntity>>

    /**
     * 一次性获取所有会话
     */
    @Query("SELECT * FROM im_conversations ORDER BY lastMessageTime DESC")
    suspend fun getAll(): List<ImConversationEntity>

    /**
     * 查询单个会话
     */
    @Query("SELECT * FROM im_conversations WHERE peerId = :peerId LIMIT 1")
    suspend fun getByPeerId(peerId: Long): ImConversationEntity?

    /**
     * 插入或更新会话
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ImConversationEntity)

    /**
     * 把指定会话的未读数清零
     */
    @Query("UPDATE im_conversations SET unreadCount = 0 WHERE peerId = :peerId")
    suspend fun clearUnread(peerId: Long)

    /**
     * 删除某个会话
     */
    @Query("DELETE FROM im_conversations WHERE peerId = :peerId")
    suspend fun delete(peerId: Long)

    /**
     * 清空全部会话
     */
    @Query("DELETE FROM im_conversations")
    suspend fun clearAll()

    /**
     * 更新指定会话的对方用户信息（头像/昵称）
     */
    @Query("UPDATE im_conversations SET peerUsername = :username, peerAvatar = :avatar WHERE peerId = :peerId")
    suspend fun updatePeerInfo(peerId: Long, username: String, avatar: String?)
}
