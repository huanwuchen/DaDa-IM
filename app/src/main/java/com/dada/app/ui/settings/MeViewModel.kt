package com.dada.app.ui.settings

import androidx.lifecycle.viewModelScope
import com.dada.core.common.base.BaseViewModel
import com.dada.core.database.UserPreferences
import com.dada.core.database.entity.ImUserProfileEntity
import com.dada.domain.user.repository.ImUserRepository
import com.dada.domain.user.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「我」页面 ViewModel
 *
 * 数据来源优先级：
 *  - Room（im_user_profile）— 来自注册成功后入库
 *  - 若 Room 没有（老版本升级场景），用 MMKV 内的旧数据兜底
 */
@HiltViewModel
class MeViewModel @Inject constructor(
    private val userRepository: ImUserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val userPreferences: UserPreferences,
) : BaseViewModel() {

    /**
     * 当前用户资料；未登录或未缓存时为 null
     */
    val profile: StateFlow<ImUserProfileEntity?> =
        userRepository.observe()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // 如果 Room 没有但 MMKV 有，回写一份到 Room（迁移兼容）
        launch {
            val existing = userRepository.get()
            if (existing == null && userPreferences.isLoggedIn()) {
                userRepository.save(
                    userId = userPreferences.getUserId(),
                    deviceId = userPreferences.getDeviceId(),
                    username = userPreferences.getUserName(),
                    avatar = userPreferences.getUserAvatar(),
                    coverImage = userPreferences.getCoverImage(),
                )
            }
        }

        // 从网络获取最新的用户信息
        refreshUserProfile()
    }

    /**
     * 从网络获取用户信息并更新本地
     */
    fun refreshUserProfile() {
        val userId = userPreferences.getUserId()
        if (userId <= 0) return

        launch {
            userProfileRepository.getUserProfile(userId)
                .onSuccess { profile ->
                    // 更新 Room + MMKV
                    userRepository.save(
                        userId = profile.id,
                        deviceId = profile.deviceId ?: userPreferences.getDeviceId(),
                        username = profile.username,
                        avatar = profile.avatar,
                        coverImage = profile.coverImage,
                    )
                }
                .onFailure { error ->
                    // 网络错误，使用本地缓存
                    postError(error.message ?: "获取用户信息失败")
                }
        }
    }

    /**
     * 登出（清空 Room + MMKV）
     */
    fun logout() {
        launch { userRepository.logout() }
    }
}
