package com.dada.app.ui.call

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import com.dada.core.common.base.BaseViewModel
import com.dada.app.network.call.CallInfo
import com.dada.app.network.call.CallManager
import com.dada.app.network.call.CallState
import com.dada.app.network.call.CallType
import com.dada.app.network.call.video.EncodingMode
import com.otaliastudios.cameraview.CameraView
import android.view.SurfaceView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 通话页 ViewModel
 *
 * 包装 [CallManager] 单例，对外暴露通话状态与操作方法。
 * Intent 参数通过 [SavedStateHandle] 读取，与 WxChatViewModel 保持一致。
 */
@HiltViewModel
class CallViewModel @Inject constructor(
    private val callManager: CallManager,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

    // ============================== Intent 参数 ==============================

    val targetUserId: Long =
        savedStateHandle.get<Long>(CallActivity.EXTRA_TARGET_USER_ID) ?: 0L

    val targetUsername: String =
        savedStateHandle.get<String>(CallActivity.EXTRA_TARGET_USERNAME) ?: "未知用户"

    val isIncoming: Boolean =
        savedStateHandle.get<Boolean>(CallActivity.EXTRA_IS_INCOMING) ?: false

    /** 是否视频通话：Intent 传入 或 CallManager 中已有通话信息 */
    val isVideoCall: Boolean
        get() = _isVideoCall

    private val _isVideoCall: Boolean =
        savedStateHandle.get<Boolean>(CallActivity.EXTRA_IS_VIDEO) == true ||
                callManager.callInfo.value.callType == CallType.VIDEO

    // ============================== 通话状态透传 ==============================

    val callState: StateFlow<CallState> = callManager.callState
    val callDuration: StateFlow<Long> = callManager.callDuration
    val callInfo: StateFlow<CallInfo> = callManager.callInfo

    // ============================== 本地 UI 状态 ==============================

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // ============================== 业务方法 ==============================

    fun accept() {
        callManager.accept()
    }

    fun reject() {
        callManager.reject()
    }

    fun hangup() {
        callManager.hangup()
        postToast("通话结束")
    }

    fun toggleMuted() {
        val newValue = !_isMuted.value
        _isMuted.value = newValue
        callManager.setMuted(newValue)
    }

    fun switchCamera() {
        callManager.switchCamera()
    }

    /**
     * 绑定视频通话 Surface
     *
     * 必须在 Activity 的 initView 中调用，传入 View 和 LifecycleOwner。
     */
    fun setupVideoSurfaces(
        localCameraView: CameraView?,
        remoteSurface: SurfaceView?,
        lifecycleOwner: LifecycleOwner?,
        encodingMode: EncodingMode = EncodingMode.H264,
    ) {
        callManager.setVideoSurfaces(
            localCameraView = localCameraView,
            remoteSurface = remoteSurface,
            lifecycleOwner = lifecycleOwner,
            encodingMode = encodingMode,
        )
    }

    /**
     * 解除视频 Surface 绑定
     */
    fun clearVideoSurfaces() {
        callManager.setVideoSurfaces(
            localCameraView = null,
            remoteSurface = null,
            lifecycleOwner = null,
        )
    }

    /**
     * 通话是否处于活跃状态（非 IDLE）
     */
    fun isCallActive(): Boolean = callManager.callState.value != CallState.IDLE

    override fun onCleared() {
        super.onCleared()
        clearVideoSurfaces()
        if (callManager.callState.value != CallState.IDLE) {
            callManager.hangup()
        }
    }
}
