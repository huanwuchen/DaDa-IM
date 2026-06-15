package com.dada.domain.user.repository

import com.dada.core.database.entity.ImUserProfileEntity
import com.dada.core.network.model.ImUser
import kotlinx.coroutines.flow.Flow

/**
 * 当前登录用户资料 Repository 接口
 */
interface ImUserRepository {

    fun observe(): Flow<ImUserProfileEntity?>

    suspend fun get(): ImUserProfileEntity?

    /**
     * 注册新用户。成功时同时把账号信息写入 Room + MMKV。
     * deviceId 由 Repository 内部生成，调用方只需提供用户名/头像。
     */
    suspend fun register(
        username: String,
        avatar: String?,
    ): Result<ImUser>

    suspend fun save(
        userId: Long,
        deviceId: String,
        username: String,
        avatar: String?,
        coverImage: String? = null,
    )

    suspend fun logout()
}
