package com.dada.app.ui.welcome

import com.dada.app.BuildConfig
import com.dada.app.DaDaApp
import com.dada.core.common.utils.LogUtil
import com.dada.core.common.utils.NetworkErrorMapper
import com.dada.core.common.base.BaseViewModel
import com.dada.core.network.api.ImApiService
import com.dada.core.network.model.ImUser
import com.dada.core.network.model.RegisterRequest
import com.dada.core.database.UserPreferences
import com.dada.domain.user.repository.ImUserRepository
import com.tencent.qcloud.tuikit.tuicallkit.debug.GenerateTestUserSig
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuicore.interfaces.TUICallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 欢迎页 ViewModel
 *
 * 职责：自动注册（生成设备 ID + 随机用户名 -> 调接口 -> 持久化到 Room + MMKV）
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val userRepository: ImUserRepository,
    private val imApiService: ImApiService,
    private val userPreferences: UserPreferences,
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

            val request = RegisterRequest(
                deviceId = userPreferences.generateDeviceId(),
                username = randomUsername(),
                avatar = null,
            )
            val response = imApiService.register(request)
            val user = response.data

            if (response.isSuccess && user != null) {
                // 写入 Room + MMKV
                userRepository.save(
                    userId = user.id,
                    deviceId = user.deviceId,
                    username = user.username,
                    avatar = user.avatar,
                    coverImage = user.coverImage,
                )
                // 注册成功后立即登录 TUICallKit，确保可接收云端来电
                loginTui(user.id)
                registering = false
                _uiState.value = WelcomeUiState(user = user)
                LogUtil.d(TAG, "注册成功: userId=${user.id}, username=${user.username}")
            } else {
                registering = false
                _uiState.value = WelcomeUiState(error = response.message)
                LogUtil.e(TAG, "注册失败: ${response.message}")
            }
        }
    }

    private fun randomUsername(): String = "用户_${(1000..9999).random()}"

    /**
     * 登录 TUICallKit，使设备可接收云端音视频来电。
     * 失败不影响主流程，仅记日志。
     */
    private fun loginTui(userId: Long) {
        if (BuildConfig.TUI_SDK_APP_ID <= 0) return
        val userSig = GenerateTestUserSig.genTestUserSig(userId.toString(), BuildConfig.TUI_SDK_APP_ID, BuildConfig.TUI_SECRET_KEY)
        TUILogin.login(DaDaApp.instance, BuildConfig.TUI_SDK_APP_ID, userId.toString(), userSig, object : TUICallback() {
            override fun onSuccess() {
                LogUtil.d(TAG, "TUICallKit 登录成功: userId=$userId")
            }
            override fun onError(code: Int, message: String?) {
                LogUtil.w(TAG, "TUICallKit 登录失败: code=$code, msg=$message（不影响主流程）")
            }
        })
    }

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
