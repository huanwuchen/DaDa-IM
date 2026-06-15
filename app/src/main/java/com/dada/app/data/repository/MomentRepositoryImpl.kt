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
        resp.data ?: throw IllegalStateException(resp.message)
    }

    override suspend fun publish(request: PublishMomentRequest): Result<Moment> = runCatching {
        val resp = api.publish(request)
        resp.data ?: throw IllegalStateException(resp.message)
    }

    override suspend fun toggleLike(momentId: Long, userId: Long): Result<LikeResult> = runCatching {
        val resp = api.toggleLike(momentId, LikeRequest(userId))
        resp.data ?: throw IllegalStateException(resp.message)
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
        resp.data ?: throw IllegalStateException(resp.message)
    }

    override suspend fun delete(momentId: Long, userId: Long): Result<Unit> = runCatching {
        val resp = api.delete(momentId, userId)
        if (!resp.isSuccess) throw IllegalStateException(resp.message)
        Unit
    }
}
