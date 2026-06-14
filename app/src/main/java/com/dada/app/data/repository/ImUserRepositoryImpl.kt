package com.dada.app.data.repository

import com.dada.core.database.UserPreferences
import com.dada.core.database.dao.ImUserProfileDao
import com.dada.core.database.entity.ImUserProfileEntity
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
) : ImUserRepository {

    override fun observe(): Flow<ImUserProfileEntity?> = profileDao.observe()

    override suspend fun get(): ImUserProfileEntity? = profileDao.get()

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
