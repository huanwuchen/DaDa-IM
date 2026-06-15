package com.dada.app.data.repository

import com.dada.core.network.api.ImApiService
import com.dada.core.network.api.UserApiService
import com.dada.core.database.UserPreferences
import com.dada.core.database.dao.ImContactDao
import com.dada.core.database.dao.ImConversationDao
import com.dada.core.database.entity.ImContactEntity
import com.dada.core.network.model.BatchUserInfoRequest
import com.dada.core.network.model.FriendInfo
import com.dada.core.network.model.ImUser
import com.dada.domain.contact.repository.ImContactRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IM 通讯录 Repository 实现
 *
 * 数据策略（Single Source of Truth）：
 *  - UI 永远从 [observeContacts] 订阅本地 Room
 *  - 拉取网络成功后整体替换本地缓存
 *  - 网络失败时本地缓存继续可用
 */
@Singleton
class ImContactRepositoryImpl @Inject constructor(
    private val contactDao: ImContactDao,
    private val conversationDao: ImConversationDao,
    private val imApiService: ImApiService,
    private val userApiService: UserApiService,
    private val userPreferences: UserPreferences,
) : ImContactRepository {

    override fun observeContacts(): Flow<List<ImContactEntity>> = contactDao.observeAll()

    override suspend fun getContact(id: Long): ImContactEntity? = contactDao.getById(id)

    override suspend fun refreshOnlineUsers(): Result<List<ImContactEntity>> {
        return try {
            val response = imApiService.getOnlineUsers()
            val data = response.data
            if (response.isSuccess && data != null) {
                val myUserId = userPreferences.getUserId()
                val entities = data.users
                    .filter { it.id != myUserId }
                    .map { it.toEntity() }
                contactDao.replaceAll(entities)
                Result.success(entities)
            } else {
                Result.failure(IllegalStateException(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun upsert(contact: ImContactEntity) = contactDao.upsert(contact)

    override suspend fun saveFriends(friends: List<FriendInfo>) {
        val entities = friends.map { friend ->
            ImContactEntity(
                id = friend.id,
                username = friend.username,
                avatar = friend.avatar,
                online = false,
                updateTime = System.currentTimeMillis(),
            )
        }
        contactDao.upsertAll(entities)
    }

    override suspend fun clear() = contactDao.clearAll()

    private fun ImUser.toEntity(): ImContactEntity = ImContactEntity(
        id = id,
        deviceId = deviceId,
        username = username,
        avatar = avatar,
        online = online,
    )

    override suspend fun batchRefreshContacts(userIds: List<Long>) {
        if (userIds.isEmpty()) return
        val response = userApiService.getBatchUserInfo(BatchUserInfoRequest(userIds))
        val users = response.data
        if (!response.isSuccess || users == null) return
        // 更新联系人表
        val entities = users.map { user ->
            ImContactEntity(
                id = user.id,
                username = user.username,
                avatar = user.avatar,
                coverImage = user.coverImage,
                updateTime = System.currentTimeMillis(),
            )
        }
        contactDao.upsertAll(entities)
        // 同步更新会话表中的头像和昵称
        users.forEach { user ->
            conversationDao.updatePeerInfo(user.id, user.username, user.avatar)
        }
    }
}
