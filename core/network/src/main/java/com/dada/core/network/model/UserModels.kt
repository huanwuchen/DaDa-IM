package com.dada.core.network.model

import com.google.gson.annotations.SerializedName

/**
 * 用户个人信息（根据后端接口调整）
 */
data class UserProfile(
    @SerializedName("id") val id: Long,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("username") val username: String,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("coverImage") val coverImage: String? = null,
    @SerializedName("createTime") val createTime: String? = null
)

/**
 * 修改个人信息请求
 */
data class UpdateProfileRequest(
    @SerializedName("userId") val userId: Long,
    @SerializedName("username") val username: String
)

/**
 * 头像上传响应
 */
data class AvatarUploadResponse(
    @SerializedName("url") val url: String
)

/**
 * 搜索用户响应
 */
data class SearchUserItem(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String,
    @SerializedName("avatar") val avatar: String? = null
)

/**
 * 好友请求
 */
data class FriendRequest(
    @SerializedName("id") val id: Long,
    @SerializedName("fromUserId") val fromUserId: Long,
    @SerializedName("toUserId") val toUserId: Long,
    @SerializedName("message") val message: String? = null,
    @SerializedName("status") val status: Int = 0, // 0-待处理 1-已同意 2-已拒绝
    @SerializedName("createTime") val createTime: String? = null,
    @SerializedName("fromUsername") val fromUsername: String? = null,
    @SerializedName("fromAvatar") val fromAvatar: String? = null
)

/**
 * 发送好友请求
 */
data class SendFriendRequestRequest(
    @SerializedName("fromUserId") val fromUserId: Long,
    @SerializedName("toUserId") val toUserId: Long,
    @SerializedName("message") val message: String? = null
)

/**
 * 处理好友请求
 */
data class HandleFriendRequestRequest(
    @SerializedName("requestId") val requestId: Long,
    @SerializedName("userId") val userId: Long
)

/**
 * 好友信息
 */
data class FriendInfo(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String,
    @SerializedName("avatar") val avatar: String? = null
)

/**
 * 删除好友请求
 */
data class RemoveFriendRequest(
    @SerializedName("userId") val userId: Long,
    @SerializedName("friendId") val friendId: Long
)

/**
 * 好友关系检查响应
 */
data class FriendCheckResponse(
    @SerializedName("isFriend") val isFriend: Boolean
)

/**
 * 在线用户响应
 */
data class OnlineUser(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("online") val online: Boolean = true
)

/**
 * 获取封面图响应
 */
data class CoverImageResponse(
    @SerializedName("coverImage") val coverImage: String? = null
)

/**
 * 上传封面图响应
 */
data class CoverUploadResponse(
    @SerializedName("url") val url: String
)

/**
 * 批量获取用户信息请求
 */
data class BatchUserInfoRequest(
    @SerializedName("userIds") val userIds: List<Long>
)

/**
 * 批量获取用户信息响应项
 */
data class UserInfoItem(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("coverImage") val coverImage: String? = null
)
