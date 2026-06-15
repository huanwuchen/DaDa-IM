package com.dada.core.network.api

import com.dada.core.network.model.ApiResponse
import com.dada.core.network.model.CommentRequest
import com.dada.core.network.model.LikeRequest
import com.dada.core.network.model.LikeResult
import com.dada.core.network.model.Moment
import com.dada.core.network.model.MomentComment
import com.dada.core.network.model.MomentFeedResponse
import com.dada.core.network.model.PublishMomentRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 朋友圈 API 服务
 *
 * 与服务端约定（详见接口文档）：
 *  - 列表：GET /api/moment/feed
 *  - 发布：POST /api/moment
 *  - 删除：DELETE /api/moment/{id}
 *  - 点赞：POST /api/moment/{id}/like
 *  - 评论：POST /api/moment/{id}/comment
 */
interface MomentApiService {

    /**
     * 朋友圈列表（所有人）
     */
    @GET("/api/moment/feed")
    suspend fun getFeed(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("userId") userId: Long,
    ): ApiResponse<MomentFeedResponse>

    /**
     * 某个用户的朋友圈
     */
    @GET("/api/moment/user/{userId}")
    suspend fun getUserMoments(
        @Path("userId") userId: Long,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("viewerId") viewerId: Long,
    ): ApiResponse<MomentFeedResponse>

    /**
     * 发布动态
     */
    @POST("/api/moment")
    suspend fun publish(@Body request: PublishMomentRequest): ApiResponse<Moment>

    /**
     * 删除动态
     */
    @DELETE("/api/moment/{momentId}")
    suspend fun delete(
        @Path("momentId") momentId: Long,
        @Query("userId") userId: Long,
    ): ApiResponse<Unit>

    /**
     * 点赞 / 取消点赞（切换）
     */
    @POST("/api/moment/{momentId}/like")
    suspend fun toggleLike(
        @Path("momentId") momentId: Long,
        @Body request: LikeRequest,
    ): ApiResponse<LikeResult>

    /**
     * 发表评论
     */
    @POST("/api/moment/{momentId}/comment")
    suspend fun comment(
        @Path("momentId") momentId: Long,
        @Body request: CommentRequest,
    ): ApiResponse<MomentComment>

    /**
     * 评论列表
     */
    @GET("/api/moment/{momentId}/comments")
    suspend fun getComments(@Path("momentId") momentId: Long): ApiResponse<List<MomentComment>>

    /**
     * 删除评论
     */
    @DELETE("/api/moment/comment/{commentId}")
    suspend fun deleteComment(
        @Path("commentId") commentId: Long,
        @Query("userId") userId: Long,
    ): ApiResponse<Unit>
}
