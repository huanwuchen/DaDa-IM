package com.dada.domain.friend.repository

import com.dada.core.network.model.FriendInfo
import com.dada.core.network.model.FriendRequest

interface FriendRepository {

    suspend fun sendFriendRequest(
        fromUserId: Long,
        toUserId: Long,
        message: String?,
    ): Result<String>

    suspend fun getFriendRequests(userId: Long): Result<List<FriendRequest>>

    suspend fun acceptFriendRequest(requestId: Long, userId: Long): Result<String>

    suspend fun rejectFriendRequest(requestId: Long, userId: Long): Result<String>

    suspend fun getFriendList(userId: Long): Result<List<FriendInfo>>

    suspend fun removeFriend(userId: Long, friendId: Long): Result<String>

    suspend fun checkFriend(userId: Long, targetId: Long): Result<Boolean>
}
