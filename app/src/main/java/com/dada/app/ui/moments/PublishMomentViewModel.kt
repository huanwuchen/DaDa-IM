package com.dada.app.ui.moments

import android.net.Uri
import com.dada.core.common.base.BaseViewModel
import com.dada.core.network.model.PublishMomentRequest
import com.dada.core.database.UserPreferences
import com.dada.domain.moment.repository.MomentRepository
import com.dada.core.network.media.MediaUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 发布朋友圈 ViewModel
 *
 * 职责：
 *  - 管理本地选中的图片 Uri 列表
 *  - 发布时按顺序上传所有图片，再调用发布接口
 */
@HiltViewModel
class PublishMomentViewModel @Inject constructor(
    private val mediaUploader: MediaUploader,
    private val repository: MomentRepository,
    userPreferences: UserPreferences,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(PublishMomentUiState())
    val uiState: StateFlow<PublishMomentUiState> = _uiState.asStateFlow()

    private val myUserId: Long = userPreferences.getUserId()

    /**
     * 添加图片
     */
    fun addImages(uris: List<Uri>) {
        _uiState.update { state ->
            val merged = (state.imageUris + uris).distinct().take(MAX_IMAGES)
            state.copy(imageUris = merged)
        }
    }

    /**
     * 移除图片
     */
    fun removeImage(uri: Uri) {
        _uiState.update { state ->
            state.copy(imageUris = state.imageUris.filter { it != uri })
        }
    }

    /**
     * 发布
     */
    fun publish(content: String, location: String? = null) {
        if (myUserId <= 0L) {
            postError("请先登录")
            return
        }
        val state = _uiState.value
        if (content.isBlank() && state.imageUris.isEmpty()) {
            postError("请输入内容或选择图片")
            return
        }

        runAsync(showLoading = false, onError = { e ->
            _uiState.update { it.copy(isPublishing = false) }
            postError("发布失败: ${e.message}")
        }) {
            _uiState.update { it.copy(isPublishing = true) }

            // 1. 并行上传图片(保持原顺序)
            val urls = coroutineScope {
                state.imageUris
                    .map { uri -> async { mediaUploader.upload(uri).url } }
                    .awaitAll()
            }

            // 2. 发布动态
            repository.publish(
                PublishMomentRequest(
                    userId = myUserId,
                    content = content.takeIf { it.isNotBlank() },
                    images = urls.takeIf { it.isNotEmpty() },
                    videoUrl = null,
                    location = location,
                )
            ).getOrThrow()

            _uiState.update { it.copy(isPublishing = false, published = true) }
        }
    }

    companion object {
        private const val MAX_IMAGES = 9
    }
}

/**
 * 发布页 UI 状态
 */
data class PublishMomentUiState(
    val imageUris: List<Uri> = emptyList(),
    val isPublishing: Boolean = false,
    val published: Boolean = false,
)
