package com.dada.core.network.api

import com.dada.core.network.model.*
import okhttp3.MultipartBody
import retrofit2.http.*

/**
 * 用户和好友相关 API（根据后端接口文档）
 */
interface UserApiService {

    /**
     * 上传头像
     */
    @Multipart
    @POST("/api/user/avatar")
    suspend fun uploadAvatar(
        @Query("userId") userId: Long,
        @Part file: MultipartBody.Part
    ): ApiResponse<AvatarUploadResponse>

    /**
     * 获取用户个人信息
     */
    @GET("/api/user/profile")
    suspend fun getUserProfile(
        @Query("userId") userId: Long
    ): ApiResponse<UserProfile>

    /**
     * 修改个人信息
     */
    @PUT("/api/user/profile")
    suspend fun updateProfile(
        @Body request: UpdateProfileRequest
    ): ApiResponse<String>

    /**
     * 搜索用户
     */
    @GET("/api/user/search")
    suspend fun searchUsers(
        @Query("keyword") keyword: String,
        @Query("userId") userId: Long
    ): ApiResponse<List<SearchUserItem>>

    /**
     * 获取在线用户
     */
    @GET("/api/user/online")
    suspend fun getOnlineUsers(): ApiResponse<OnlineUsersResponse>

    /**
     * 发送好友请求
     */
    @POST("/api/friend/request")
    suspend fun sendFriendRequest(
        @Body request: SendFriendRequestRequest
    ): ApiResponse<String>

    /**
     * 获取收到的好友请求
     */
    @GET("/api/friend/requests")
    suspend fun getFriendRequests(
        @Query("userId") userId: Long
    ): ApiResponse<List<FriendRequest>>

    /**
     * 同意好友请求
     */
    @POST("/api/friend/accept")
    suspend fun acceptFriendRequest(
        @Body request: HandleFriendRequestRequest
    ): ApiResponse<String>

    /**
     * 拒绝好友请求
     */
    @POST("/api/friend/reject")
    suspend fun rejectFriendRequest(
        @Body request: HandleFriendRequestRequest
    ): ApiResponse<String>

    /**
     * 获取好友列表
     */
    @GET("/api/friend/list")
    suspend fun getFriendList(
        @Query("userId") userId: Long
    ): ApiResponse<List<FriendInfo>>

    /**
     * 删除好友
     */
    @POST("/api/friend/remove")
    suspend fun removeFriend(
        @Body request: RemoveFriendRequest
    ): ApiResponse<String>

    /**
     * 检查是否是好友
     */
    @GET("/api/friend/check")
    suspend fun checkFriend(
        @Query("userId") userId: Long,
        @Query("targetId") targetId: Long
    ): ApiResponse<FriendCheckResponse>

    /**
     * 获取朋友圈封面图
     */
    @GET("/api/user/cover")
    suspend fun getCoverImage(
        @Query("userId") userId: Long
    ): ApiResponse<CoverImageResponse>

    /**
     * 上传朋友圈封面图
     */
    @Multipart
    @POST("/api/user/cover")
    suspend fun uploadCoverImage(
        @Query("userId") userId: Long,
        @Part file: MultipartBody.Part
    ): ApiResponse<CoverUploadResponse>

    /**
     * 批量获取用户信息（用于聊天列表刷新对方头像/昵称）
     */
    @POST("/api/user/batch-info")
    suspend fun getBatchUserInfo(
        @Body request: BatchUserInfoRequest
    ): ApiResponse<List<UserInfoItem>>
}
