package com.dada.app.ui.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dada.core.ui.base.BaseActivity
import com.dada.app.databinding.ActivityCallBinding
import com.dada.app.network.call.CallState
import com.dada.app.network.call.video.EncodingMode
import dagger.hilt.android.AndroidEntryPoint

/**
 * 通话界面（语音 + 视频）
 *
 * 三种状态切换：
 *  - RINGING（来电）：显示「接听 / 拒绝」
 *  - CALLING（去电）：显示「取消」
 *  - IN_CALL（通话中）：显示「静音 / 挂断」
 *
 * 入口：使用 [startOutgoing] / [startIncoming] 启动，避免直接 new Intent
 */
@AndroidEntryPoint
class CallActivity : BaseActivity<ActivityCallBinding>() {

    private val viewModel: CallViewModel by viewModels()

    // ============================== 生命周期 ==============================

    override fun inflateBinding(): ActivityCallBinding =
        ActivityCallBinding.inflate(layoutInflater)

    override fun initView() {
        setupFullScreen()

        binding.tvUsername.text = viewModel.targetUsername

        if (viewModel.isVideoCall) {
            binding.videoContainer.visibility = View.VISIBLE
            binding.surfaceRemote.setZOrderMediaOverlay(false)
            viewModel.setupVideoSurfaces(
                localCameraView = binding.cameraLocal,
                remoteSurface = binding.surfaceRemote,
                lifecycleOwner = this,
                encodingMode = EncodingMode.H264,
            )
        } else {
            binding.videoContainer.visibility = View.GONE
        }

        // 来电：接听 / 拒绝
        binding.btnAccept.setOnClickListener { requestPermissionAndAccept() }
        binding.btnReject.setOnClickListener {
            viewModel.reject()
            finish()
        }

        // 通话中：挂断
        binding.btnHangup.setOnClickListener {
            viewModel.hangup()
            finish()
        }

        // 去电：取消
        binding.btnCancel.setOnClickListener {
            viewModel.hangup()
            finish()
        }

        // 切换前/后摄像头（仅视频通话有效）
        binding.btnSwitchCamera.setOnClickListener { viewModel.switchCamera() }

        // 静音切换
        binding.btnMute.setOnClickListener { viewModel.toggleMuted() }

        // 首次进入根据来电/去电展示对应 UI
        if (viewModel.isIncoming) showIncomingUI() else showOutgoingUI()
    }

    override fun initData() {
        bindBaseViewModel(viewModel)

        // 状态切换 -> 切换 UI
        observe(viewModel.callState) { state ->
            when (state) {
                CallState.IDLE -> finish()
                CallState.CALLING -> showOutgoingUI()
                CallState.RINGING -> showIncomingUI()
                CallState.IN_CALL -> showInCallUI()
            }
        }

        // 通话时长 -> 显示在状态栏文字
        observe(viewModel.callDuration) { seconds ->
            binding.tvStatus.text = String.format("%02d:%02d", seconds / 60, seconds % 60)
        }

        // 静音状态 -> 更新 UI
        observe(viewModel.isMuted) { muted ->
            binding.tvMuteLabel.text = if (muted) "取消静音" else "静音"
            binding.btnMute.alpha = if (muted) 0.6f else 1.0f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.clearVideoSurfaces()
        if (viewModel.isCallActive()) {
            viewModel.hangup()
        }
    }

    // ============================== 初始化 ==============================

    /**
     * 全屏 + 锁屏显示 + 屏幕常亮（通话过程中需要）
     */
    private fun setupFullScreen() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    // ============================== UI 状态 ==============================

    /** 来电界面 */
    private fun showIncomingUI() {
        binding.tvStatus.text = if (viewModel.isVideoCall) "视频来电..." else "来电中..."
        binding.layoutIncomingActions.visibility = View.VISIBLE
        binding.layoutOutgoingActions.visibility = View.GONE
        binding.layoutInCallActions.visibility = View.GONE
    }

    /** 去电界面 */
    private fun showOutgoingUI() {
        binding.tvStatus.text = if (viewModel.isVideoCall) "视频呼叫中..." else "呼叫中..."
        binding.layoutIncomingActions.visibility = View.GONE
        binding.layoutOutgoingActions.visibility = View.VISIBLE
        binding.layoutInCallActions.visibility = View.GONE
    }

    /** 通话中界面 */
    private fun showInCallUI() {
        binding.tvStatus.text = "00:00"
        binding.layoutIncomingActions.visibility = View.GONE
        binding.layoutOutgoingActions.visibility = View.GONE
        binding.layoutInCallActions.visibility = View.VISIBLE

        if (viewModel.isVideoCall) {
            binding.ivAvatar.visibility = View.GONE
            binding.tvUsername.visibility = View.GONE
            binding.btnSwitchCamera.visibility = View.VISIBLE
            binding.spaceSwitchCamera.visibility = View.VISIBLE
        } else {
            binding.btnSwitchCamera.visibility = View.GONE
            binding.spaceSwitchCamera.visibility = View.GONE
        }
    }

    // ============================== 权限处理 ==============================

    /**
     * 接听前先校验权限：语音通话只需麦克风；视频通话还需要摄像头
     */
    private fun requestPermissionAndAccept() {
        val needed = mutableListOf<String>().apply {
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (viewModel.isVideoCall && !hasPermission(Manifest.permission.CAMERA)) {
                add(Manifest.permission.CAMERA)
            }
        }

        if (needed.isEmpty()) {
            viewModel.accept()
        } else {
            ActivityCompat.requestPermissions(
                this, needed.toTypedArray(), REQUEST_AUDIO_PERMISSION
            )
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            val allGranted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                viewModel.accept()
            } else {
                Toast.makeText(this, "需要相关权限才能通话", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_TARGET_USER_ID = "extra_target_user_id"
        const val EXTRA_TARGET_USERNAME = "extra_target_username"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"
        const val EXTRA_IS_VIDEO = "extra_is_video"

        private const val REQUEST_AUDIO_PERMISSION = 0x3001

        fun startOutgoing(
            context: Context,
            targetUserId: Long,
            targetUsername: String,
            isVideo: Boolean = false,
        ) {
            startCall(context, targetUserId, targetUsername, isIncoming = false, isVideo = isVideo)
        }

        fun startIncoming(
            context: Context,
            targetUserId: Long,
            targetUsername: String,
            isVideo: Boolean = false,
        ) {
            startCall(context, targetUserId, targetUsername, isIncoming = true, isVideo = isVideo)
        }

        private fun startCall(
            context: Context,
            targetUserId: Long,
            targetUsername: String,
            isIncoming: Boolean,
            isVideo: Boolean,
        ) {
            val intent = Intent(context, CallActivity::class.java).apply {
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
                putExtra(EXTRA_TARGET_USERNAME, targetUsername)
                putExtra(EXTRA_IS_INCOMING, isIncoming)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
