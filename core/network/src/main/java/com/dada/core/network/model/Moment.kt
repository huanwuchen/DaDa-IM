package com.dada.core.network.model

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/**
 * 朋友圈作者简要信息
 */
data class MomentUser(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String = "",
    @SerializedName("avatar") val avatar: String? = null,
)

/**
 * 朋友圈点赞用户
 */
data class MomentLike(
    @SerializedName("userId") val userId: Long,
    @SerializedName("username") val username: String = "",
    @SerializedName("avatar") val avatar: String? = null,
)

/**
 * 朋友圈评论
 */
data class MomentComment(
    @SerializedName("id") val id: Long,
    @SerializedName("momentId") val momentId: Long = 0,
    @SerializedName("userId") val userId: Long,
    @SerializedName("username") val username: String = "",
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("content") val content: String = "",
    @SerializedName("replyToUserId") val replyToUserId: Long? = null,
    @SerializedName("replyToUsername") val replyToUsername: String? = null,
    @SerializedName("replyToAvatar") val replyToAvatar: String? = null,
    @SerializedName("createTime") val createTime: String? = null,
    @SerializedName("user") val user: MomentUser? = null,
)

/**
 * 朋友圈动态
 *
 * 服务端字段差异处理：
 *  - images：feed 返回数组、publish 返回 JSON 字符串。用 [MomentImagesAdapter] 统一为 [List]
 *  - likes / comments：可能为 null，类型用 nullable，UI 取值时统一 .orEmpty()
 *  - 服务端可能多返回 [likeCount] / [commentCount]，已声明字段按需用，不影响解析
 */
data class Moment(
    @SerializedName("id") val id: Long,
    @SerializedName("userId") val userId: Long,
    @SerializedName("content") val content: String = "",

    @JsonAdapter(MomentImagesAdapter::class)
    @SerializedName("images")
    val images: List<String> = emptyList(),

    @SerializedName("videoUrl") val videoUrl: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("createTime") val createTime: String? = null,
    @SerializedName("user") val user: MomentUser? = null,

    /** 服务端可能为 null —— 用 nullable 接收，UI 用 .orEmpty() */
    @SerializedName("likes") val likes: List<MomentLike>? = null,
    /** 服务端可能为 null —— 用 nullable 接收，UI 用 .orEmpty() */
    @SerializedName("comments") val comments: List<MomentComment>? = null,

    @SerializedName("likedByMe") val likedByMe: Boolean = false,

    /** 部分接口直接给汇总数 */
    @SerializedName("likeCount") val likeCount: Int = 0,
    @SerializedName("commentCount") val commentCount: Int = 0,
)

/**
 * 朋友圈列表响应
 */
data class MomentFeedResponse(
    @SerializedName("list") val list: List<Moment> = emptyList(),
    @SerializedName("page") val page: Int = 1,
    @SerializedName("size") val size: Int = 20,
    @SerializedName("total") val total: Int = 0,
)

/**
 * 发布朋友圈请求
 */
data class PublishMomentRequest(
    @SerializedName("userId") val userId: Long,
    @SerializedName("content") val content: String? = null,
    @SerializedName("images") val images: List<String>? = null,
    @SerializedName("videoUrl") val videoUrl: String? = null,
    @SerializedName("location") val location: String? = null,
)

/**
 * 点赞请求
 */
data class LikeRequest(
    @SerializedName("userId") val userId: Long,
)

/**
 * 点赞返回
 */
data class LikeResult(
    @SerializedName("liked") val liked: Boolean,
    @SerializedName("likeCount") val likeCount: Int = 0,
)

/**
 * 评论请求
 */
data class CommentRequest(
    @SerializedName("userId") val userId: Long,
    @SerializedName("content") val content: String,
    @SerializedName("replyToUserId") val replyToUserId: Long? = null,
    @SerializedName("replyToCommentId") val replyToCommentId: Long? = null,
)
