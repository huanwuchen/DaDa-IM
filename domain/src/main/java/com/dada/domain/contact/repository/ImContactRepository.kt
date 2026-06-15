package com.dada.domain.contact.repository

import com.dada.core.database.entity.ImContactEntity
import com.dada.core.network.model.FriendInfo
import kotlinx.coroutines.flow.Flow

/**
 * 通讯录 / 联系人 Repository 接口
 */
interface ImContactRepository {

    fun observeContacts(): Flow<List<ImContactEntity>>

    suspend fun getContact(id: Long): ImContactEntity?

    suspend fun refreshOnlineUsers(): Result<List<ImContactEntity>>

    suspend fun upsert(contact: ImContactEntity)

    suspend fun saveFriends(friends: List<FriendInfo>)

    suspend fun clear()

    /**
     * 批量刷新指定用户的信息（从后台拉取后同时更新联系人表和会话表）
     */
    suspend fun batchRefreshContacts(userIds: List<Long>)
}
