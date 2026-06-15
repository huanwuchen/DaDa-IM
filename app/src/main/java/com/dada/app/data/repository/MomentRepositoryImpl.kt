package com.dada.app.data.repository

import com.dada.core.network.api.MomentApiService
import com.dada.core.network.model.CommentRequest
import com.dada.core.network.model.LikeRequest
import com.dada.core.network.model.LikeResult
import com.dada.core.network.model.Moment
import com.dada.core.network.model.MomentComment
import com.dada.core.network.model.MomentFeedResponse
import com.dada.core.network.model.PublishMomentRequest
import com.dada.domain.moment.repository.MomentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomentRepositoryImpl @Inject constructor(
    private val api: MomentApiService,
) : MomentRepository {

    override suspend fun getFeed(
        userId: Long,
        page: Int,
        size: Int,
    ): Result<MomentFeedResponse> = runCatching {
        val resp = api.getFeed(page = page, size = size, userId = userId)
        resp.data ?: error(resp.message ?: "获取动态失败")
    }

    override suspend fun publish(request: PublishMomentRequest): Result<Moment> = runCatching {
        val resp = api.publish(request)
        resp.data ?: error(resp.message ?: "发布失败")
    }

    override suspend fun toggleLike(momentId: Long, userId: Long): Result<LikeResult> = runCatching {
        val resp = api.toggleLike(momentId, LikeRequest(userId))
        resp.data ?: error(resp.message ?: "点赞失败")
    }

    override suspend fun comment(
        momentId: Long,
        userId: Long,
        content: String,
        replyToUserId: Long?,
        replyToCommentId: Long?,
    ): Result<MomentComment> = runCatching {
        val resp = api.comment(
            momentId,
            CommentRequest(userId, content, replyToUserId, replyToCommentId)
        )
        resp.data ?: error(resp.message ?: "评论失败")
    }

    override suspend fun delete(momentId: Long, userId: Long): Result<Unit> = runCatching {
        val resp = api.delete(momentId, userId)
        if (!resp.isSuccess) error(resp.message ?: "删除失败")
    }
}
