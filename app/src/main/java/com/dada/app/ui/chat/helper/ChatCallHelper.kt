package com.dada.app.ui.chat.helper

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.dada.app.databinding.ActivityWxChatBinding
import com.dada.app.network.call.CallManager
import com.dada.app.network.call.CallState
import com.dada.app.network.call.CallType
import com.dada.app.network.call.NetworkCallRouter
import com.dada.app.ui.call.CallActivity
import kotlinx.coroutines.launch

/**
 * 语音/视频通话助手
 *
 * 封装通话发起、权限检查、状态校验，
 * Activity 只需调用 [setup] 和转发 [onRequestPermissionsResult]。
 *
 * [callManager] 由 Activity 透传（Hilt 注入到 Activity 后传入）。
 */
class ChatCallHelper(
    private val activity: Activity,
    private val binding: ActivityWxChatBinding,
    private val callManager: CallManager,
    private val networkCallRouter: NetworkCallRouter,
) {

    private val lifecycleScope = (activity as LifecycleOwner).lifecycleScope
    private var pendingCallType: PendingCallType? = null

    /**
     * 绑定通话按钮点击事件
     */
    fun setup() {
        binding.ivCall.setOnClickListener {
            startVoiceCall()
        }
        binding.ivVideoCall.setOnClickListener {
            startVideoCall()
        }
    }

    /**
     * 发起语音通话（自动检测局域网，选择 P2P 或 TUICallKit）
     */
    fun startVoiceCall() {
        if (!ensurePermissions(REQUEST_AUDIO_PERMISSION, Manifest.permission.RECORD_AUDIO)) return
        if (!ensureCallIdle()) return

        lifecycleScope.launch {
            val isLan = networkCallRouter.routeCall(activity, targetUserId, targetUsername, isVideo = false)
            if (isLan) {
                CallActivity.startOutgoing(activity, targetUserId, targetUsername, isVideo = false)
                callManager.invite(targetUserId, targetUsername, CallType.AUDIO)
            }
        }
    }

    /**
     * 发起视频通话（自动检测局域网，选择 P2P 或 TUICallKit）
     */
    fun startVideoCall() {
        if (!ensurePermissions(
                REQUEST_VIDEO_PERMISSION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
            )
        ) return
        if (!ensureCallIdle()) return

        lifecycleScope.launch {
            val isLan = networkCallRouter.routeCall(activity, targetUserId, targetUsername, isVideo = true)
            if (isLan) {
                CallActivity.startOutgoing(activity, targetUserId, targetUsername, isVideo = true)
                callManager.invite(targetUserId, targetUsername, CallType.VIDEO)
            }
        }
    }

    /**
     * 转发权限请求结果（由 Activity 的 onRequestPermissionsResult 调用）
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        val allGranted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        when (requestCode) {
            REQUEST_AUDIO_PERMISSION -> {
                if (allGranted) startVoiceCall()
                else Toast.makeText(activity, "需要麦克风权限才能语音通话", Toast.LENGTH_SHORT).show()
            }
            REQUEST_VIDEO_PERMISSION -> {
                if (allGranted) startVideoCall()
                else Toast.makeText(activity, "需要麦克风和摄像头权限才能视频通话", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============================== 通话参数（由 Activity 设置） ==============================

    var targetUserId: Long = 0L
    var targetUsername: String = ""

    // ============================== 内部方法 ==============================

    private fun ensurePermissions(requestCode: Int, vararg perms: String): Boolean {
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true
        ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
        return false
    }

    private fun ensureCallIdle(): Boolean {
        if (callManager.callState.value != CallState.IDLE) {
            Toast.makeText(activity, "正在通话中", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    companion object {
        const val REQUEST_AUDIO_PERMISSION = 0x3002
        const val REQUEST_VIDEO_PERMISSION = 0x3003
    }

    private enum class PendingCallType { AUDIO, VIDEO }
}
