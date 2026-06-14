package com.dada.app.data.repository

import com.dada.core.network.api.UserApiService
import com.dada.core.network.model.OnlineUser
import com.dada.core.network.model.SearchUserItem
import com.dada.core.network.model.UpdateProfileRequest
import com.dada.core.network.model.UserProfile
import com.dada.domain.user.repository.UserProfileRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
) : UserProfileRepository {

    override suspend fun getUserProfile(userId: Long): Result<UserProfile> = try {
        val response = userApiService.getUserProfile(userId)
        if (response.isSuccess && response.data != null) {
            Result.success(response.data!!)
        } else {
            Result.failure(Exception(response.message ?: "获取用户信息失败"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateProfile(userId: Long, username: String): Result<String> = try {
        val response = userApiService.updateProfile(UpdateProfileRequest(userId, username))
        if (response.isSuccess) Result.success(response.message ?: "修改成功")
        else Result.failure(Exception(response.message ?: "修改失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun uploadAvatar(userId: Long, imageFile: File): Result<String> = try {
        val requestBody = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", imageFile.name, requestBody)
        val response = userApiService.uploadAvatar(userId, part)
        if (response.isSuccess && response.data != null) Result.success(response.data!!.url)
        else Result.failure(Exception(response.message ?: "上传失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun searchUsers(keyword: String, userId: Long): Result<List<SearchUserItem>> = try {
        val response = userApiService.searchUsers(keyword, userId)
        if (response.isSuccess && response.data != null) Result.success(response.data!!)
        else Result.failure(Exception(response.message ?: "搜索失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getOnlineUsers(): Result<List<OnlineUser>> = try {
        val response = userApiService.getOnlineUsers()
        if (response.isSuccess && response.data != null) {
            val onlineUsers = response.data!!.users.map { imUser ->
                OnlineUser(
                    id = imUser.id,
                    username = imUser.username,
                    avatar = imUser.avatar,
                    deviceId = imUser.deviceId,
                    online = imUser.online,
                )
            }
            Result.success(onlineUsers)
        } else {
            Result.failure(Exception(response.message ?: "获取在线用户失败"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getCoverImage(userId: Long): Result<String?> = try {
        val response = userApiService.getCoverImage(userId)
        if (response.isSuccess) Result.success(response.data?.coverImage)
        else Result.failure(Exception(response.message ?: "获取封面图失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun uploadCoverImage(userId: Long, imageFile: File): Result<String> = try {
        val requestBody = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", imageFile.name, requestBody)
        val response = userApiService.uploadCoverImage(userId, part)
        if (response.isSuccess && response.data != null) Result.success(response.data!!.url)
        else Result.failure(Exception(response.message ?: "上传封面图失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
