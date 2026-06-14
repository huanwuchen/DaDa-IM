package com.dada.app.data.repository

import com.dada.core.network.api.UserApiService
import com.dada.core.network.model.FriendInfo
import com.dada.core.network.model.FriendRequest
import com.dada.core.network.model.HandleFriendRequestRequest
import com.dada.core.network.model.RemoveFriendRequest
import com.dada.core.network.model.SendFriendRequestRequest
import com.dada.domain.friend.repository.FriendRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
) : FriendRepository {

    override suspend fun sendFriendRequest(
        fromUserId: Long,
        toUserId: Long,
        message: String?,
    ): Result<String> = try {
        val request = SendFriendRequestRequest(fromUserId, toUserId, message)
        val response = userApiService.sendFriendRequest(request)
        if (response.isSuccess) Result.success(response.message ?: "好友请求已发送")
        else Result.failure(Exception(response.message ?: "发送失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getFriendRequests(userId: Long): Result<List<FriendRequest>> = try {
        val response = userApiService.getFriendRequests(userId)
        if (response.isSuccess && response.data != null) Result.success(response.data!!)
        else Result.failure(Exception(response.message ?: "获取失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun acceptFriendRequest(requestId: Long, userId: Long): Result<String> = try {
        val request = HandleFriendRequestRequest(requestId, userId)
        val response = userApiService.acceptFriendRequest(request)
        if (response.isSuccess) Result.success(response.message ?: "已同意")
        else Result.failure(Exception(response.message ?: "操作失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun rejectFriendRequest(requestId: Long, userId: Long): Result<String> = try {
        val request = HandleFriendRequestRequest(requestId, userId)
        val response = userApiService.rejectFriendRequest(request)
        if (response.isSuccess) Result.success(response.message ?: "已拒绝")
        else Result.failure(Exception(response.message ?: "操作失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getFriendList(userId: Long): Result<List<FriendInfo>> = try {
        val response = userApiService.getFriendList(userId)
        if (response.isSuccess && response.data != null) Result.success(response.data!!)
        else Result.failure(Exception(response.message ?: "获取失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun removeFriend(userId: Long, friendId: Long): Result<String> = try {
        val response = userApiService.removeFriend(RemoveFriendRequest(userId, friendId))
        if (response.isSuccess) Result.success(response.message ?: "已删除")
        else Result.failure(Exception(response.message ?: "删除失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun checkFriend(userId: Long, targetId: Long): Result<Boolean> = try {
        val response = userApiService.checkFriend(userId, targetId)
        if (response.isSuccess && response.data != null) Result.success(response.data!!.isFriend)
        else Result.failure(Exception(response.message ?: "检查失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
