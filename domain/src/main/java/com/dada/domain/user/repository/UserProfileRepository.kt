package com.dada.domain.user.repository

import com.dada.core.network.model.OnlineUser
import com.dada.core.network.model.SearchUserItem
import com.dada.core.network.model.UserProfile
import java.io.File

interface UserProfileRepository {

    suspend fun getUserProfile(userId: Long): Result<UserProfile>

    suspend fun updateProfile(userId: Long, username: String): Result<String>

    suspend fun uploadAvatar(userId: Long, imageFile: File): Result<String>

    suspend fun searchUsers(keyword: String, userId: Long): Result<List<SearchUserItem>>

    suspend fun getOnlineUsers(): Result<List<OnlineUser>>

    suspend fun getCoverImage(userId: Long): Result<String?>

    suspend fun uploadCoverImage(userId: Long, imageFile: File): Result<String>
}
