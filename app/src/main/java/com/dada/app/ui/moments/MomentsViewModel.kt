package com.dada.app.ui.moments

import androidx.lifecycle.viewModelScope
import com.dada.core.common.base.BaseViewModel
import com.dada.core.database.dao.ImContactDao
import com.dada.core.network.model.Moment
import com.dada.core.database.UserPreferences
import com.dada.domain.moment.repository.MomentRepository
import com.dada.domain.user.repository.UserProfileRepository
import com.dada.core.common.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject

/**
 * 朋友圈主页 ViewModel
 *
 * 职责：
 *  - 拉取列表（首次 / 下拉刷新 / 上拉加载更多）
 *  - 点赞 / 评论 / 删除（操作成功后局部更新对应 Moment）
 */
@HiltViewModel
class MomentsViewModel @Inject constructor(
    private val repository: MomentRepository,
    private val userProfileRepository: UserProfileRepository,
    private val contactDao: ImContactDao,
    private val userPreferences: UserPreferences,
) : BaseViewModel() {

    val myUserId: Long = userPreferences.getUserId()
    val myUsername: String = userPreferences.getUserName()

    private val _uiState = MutableStateFlow(MomentsUiState())
    val uiState: StateFlow<MomentsUiState> = _uiState.asStateFlow()

    private val _coverUrl = MutableStateFlow<String?>(null)
    val coverUrl: StateFlow<String?> = _coverUrl.asStateFlow()

    init {
        // 先从本地读取封面图
        _coverUrl.value = userPreferences.getCoverImage()
        refresh()
        // 异步从网络更新封面图
        loadCoverFromNetwork()
    }

    /**
     * 下拉刷新（重置到第一页）
     */
    fun refresh() {
        if (myUserId <= 0L) {
            postError("用户信息无效，请重新登录")
            return
        }
        loadPage(page = 1)
    }

    /**
     * 上拉加载下一页
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return
        loadPage(page = state.page + 1)
    }

    private fun loadPage(page: Int) {
        runAsync(showLoading = false, onError = { e ->
            _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false) }
            postError("加载失败: ${e.message}")
        }) {
            if (page == 1) _uiState.update { it.copy(isRefreshing = true) }
            else _uiState.update { it.copy(isLoadingMore = true) }

            val resp = repository.getFeed(myUserId, page).getOrThrow()

            _uiState.update { state ->
                val merged = if (page == 1) resp.list else state.list + resp.list
                state.copy(
                    list = merged,
                    page = resp.page,
                    isRefreshing = false,
                    isLoadingMore = false,
                    hasMore = resp.list.size >= resp.size,
                )
            }
        }
    }

    /**
     * 点赞 / 取消点赞（乐观更新）
     */
    fun toggleLike(moment: Moment) {
        runAsync(showLoading = false, onError = { e ->
            postError("操作失败: ${e.message}")
        }) {
            val result = repository.toggleLike(moment.id, myUserId).getOrThrow()
            // 局部更新该 Moment 的 likedByMe + likes
            _uiState.update { state ->
                val newList = state.list.map { item ->
                    if (item.id != moment.id) item
                    else item.copy(
                        likedByMe = result.liked,
                        likes = if (result.liked) {
                            item.likes.orEmpty() + com.dada.core.network.model.MomentLike(myUserId, myUsername)
                        } else {
                            item.likes.orEmpty().filter { it.userId != myUserId }
                        }
                    )
                }
                state.copy(list = newList)
            }
        }
    }

    /**
     * 发表评论
     */
    fun comment(
        moment: Moment,
        content: String,
        replyToUserId: Long? = null,
        replyToCommentId: Long? = null,
    ) {
        if (content.isBlank()) return
        runAsync(showLoading = false, onError = { e ->
            postError("评论失败: ${e.message}")
        }) {
            val newComment = repository.comment(
                moment.id, myUserId, content, replyToUserId, replyToCommentId
            ).getOrThrow()
            _uiState.update { state ->
                val newList = state.list.map { item ->
                    if (item.id != moment.id) item
                    else item.copy(comments = item.comments.orEmpty() + newComment)
                }
                state.copy(list = newList)
            }
        }
    }

    /**
     * 删除动态
     */
    fun delete(moment: Moment) {
        runAsync(showLoading = false, onError = { e ->
            postError("删除失败: ${e.message}")
        }) {
            repository.delete(moment.id, myUserId).getOrThrow()
            _uiState.update { state ->
                state.copy(list = state.list.filter { it.id != moment.id })
            }
            postToast("已删除")
        }
    }

    /**
     * 从网络加载封面图并更新本地
     */
    private fun loadCoverFromNetwork() {
        if (myUserId <= 0L) return
        runAsync(showLoading = false) {
            val cover = userProfileRepository.getCoverImage(myUserId).getOrNull() ?: return@runAsync
            persistCover(cover)
        }
    }

    /**
     * 上传封面图
     */
    fun uploadCover(imageFile: File) {
        if (myUserId <= 0L) return
        runAsync(onError = { e ->
            postError("上传封面图失败: ${e.message}")
        }) {
            val url = userProfileRepository.uploadCoverImage(myUserId, imageFile).getOrThrow()
            persistCover(url)
            postToast("封面图已更新")
        }
    }

    private suspend fun persistCover(rawUrl: String) {
        val resolved = Constants.resolveUrl(rawUrl) ?: rawUrl
        _coverUrl.value = resolved
        userPreferences.saveCoverImage(resolved)
        contactDao.getById(myUserId)?.let { contactDao.upsert(it.copy(coverImage = resolved)) }
    }
}

/**
 * 朋友圈主页 UI 状态
 */
data class MomentsUiState(
    val list: List<Moment> = emptyList(),
    val page: Int = 1,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
)
