package com.dada.app.ui.welcome

import com.dada.app.network.tui.TuiLoginManager
import com.dada.core.common.base.BaseViewModel
import com.dada.core.common.utils.LogUtil
import com.dada.core.common.utils.NetworkErrorMapper
import com.dada.core.network.model.ImUser
import com.dada.domain.user.repository.ImUserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 欢迎页 ViewModel
 *
 * 职责：自动注册（调用 Repository -> 持久化由 Repository 内部完成）
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val userRepository: ImUserRepository,
    private val tuiLoginManager: TuiLoginManager,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    private var registering = false

    /**
     * 注册一个新用户
     *
     * 失败时只更新 error，UI 层会展示「重试」按钮再次调用本方法
     */
    fun register() {
        if (registering) return
        registering = true
        runAsync(showLoading = false, onError = { e ->
            registering = false
            _uiState.value = WelcomeUiState(error = NetworkErrorMapper.toMessage(e))
            LogUtil.e(TAG, "注册异常", e)
        }) {
            _uiState.value = WelcomeUiState(isLoading = true)

            userRepository.register(
                username = randomUsername(),
                avatar = null,
            ).onSuccess { user ->
                // 注册成功后立即登录 TUICallKit，确保可接收云端来电
                tuiLoginManager.login(user.id)
                registering = false
                _uiState.value = WelcomeUiState(user = user)
                LogUtil.d(TAG, "注册成功: userId=${user.id}, username=${user.username}")
            }.onFailure { e ->
                registering = false
                _uiState.value = WelcomeUiState(error = NetworkErrorMapper.toMessage(e))
                LogUtil.e(TAG, "注册失败", e)
            }
        }
    }

    private fun randomUsername(): String = "用户_${(1000..9999).random()}"

    companion object {
        private const val TAG = "WelcomeViewModel"
    }
}

/**
 * 欢迎页 UI 状态
 */
data class WelcomeUiState(
    val isLoading: Boolean = false,
    val user: ImUser? = null,
    val error: String? = null,
)
