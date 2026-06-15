package com.dada.app.data.repository

import com.dada.core.database.UserPreferences
import com.dada.core.database.dao.ImUserProfileDao
import com.dada.core.database.entity.ImUserProfileEntity
import com.dada.core.network.api.ImApiService
import com.dada.core.network.model.ImUser
import com.dada.core.network.model.RegisterRequest
import com.dada.domain.user.repository.ImUserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IM 当前登录用户资料 Repository 实现
 *
 * 数据来源：
 *  - 注册成功后调用 [save] 写入 Room + MMKV
 *  - 「我」页面通过 [observe] 订阅 Room 单一数据源
 */
@Singleton
class ImUserRepositoryImpl @Inject constructor(
    private val profileDao: ImUserProfileDao,
    private val userPreferences: UserPreferences,
    private val imApiService: ImApiService,
) : ImUserRepository {

    override fun observe(): Flow<ImUserProfileEntity?> = profileDao.observe()

    override suspend fun get(): ImUserProfileEntity? = profileDao.get()

    override suspend fun register(
        username: String,
        avatar: String?,
    ): Result<ImUser> = runCatching {
        val response = imApiService.register(
            RegisterRequest(
                deviceId = userPreferences.generateDeviceId(),
                username = username,
                avatar = avatar,
            )
        )
        val user = response.data
        if (!response.isSuccess || user == null) {
            error(response.message.ifBlank { "注册失败" })
        }
        save(
            userId = user.id,
            deviceId = user.deviceId,
            username = user.username,
            avatar = user.avatar,
            coverImage = user.coverImage,
        )
        user
    }

    override suspend fun save(
        userId: Long,
        deviceId: String,
        username: String,
        avatar: String?,
        coverImage: String?,
    ) {
        userPreferences.saveUser(userId, deviceId, username, avatar, coverImage)
        profileDao.upsert(
            ImUserProfileEntity(
                userId = userId,
                deviceId = deviceId,
                username = username,
                avatar = avatar,
                coverImage = coverImage,
            )
        )
    }

    override suspend fun logout() {
        profileDao.clear()
        userPreferences.clearUserData()
    }
}
