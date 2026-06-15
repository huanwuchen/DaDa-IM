package com.dada.domain.user.repository

import com.dada.core.database.entity.ImUserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * 当前登录用户资料 Repository 接口
 */
interface ImUserRepository {

    fun observe(): Flow<ImUserProfileEntity?>

    suspend fun get(): ImUserProfileEntity?

    suspend fun save(
        userId: Long,
        deviceId: String,
        username: String,
        avatar: String?,
        coverImage: String? = null,
    )

    suspend fun logout()
}
