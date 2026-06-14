package com.dada.domain.moment.repository

import com.dada.core.network.model.LikeResult
import com.dada.core.network.model.Moment
import com.dada.core.network.model.MomentComment
import com.dada.core.network.model.MomentFeedResponse
import com.dada.core.network.model.PublishMomentRequest

interface MomentRepository {

    suspend fun getFeed(userId: Long, page: Int = 1, size: Int = 20): Result<MomentFeedResponse>

    suspend fun publish(request: PublishMomentRequest): Result<Moment>

    suspend fun toggleLike(momentId: Long, userId: Long): Result<LikeResult>

    suspend fun comment(
        momentId: Long,
        userId: Long,
        content: String,
        replyToUserId: Long? = null,
        replyToCommentId: Long? = null,
    ): Result<MomentComment>

    suspend fun delete(momentId: Long, userId: Long): Result<Unit>
}
