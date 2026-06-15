package com.dada.app.ui.welcome

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dada.core.ui.base.BaseActivity
import com.dada.app.databinding.ActivityWelcomeBinding
import com.dada.app.network.websocket.WebSocketService
import com.dada.app.push.PushManager
import com.dada.app.ui.MainActivity
import com.dada.core.database.UserPreferences
import com.dada.app.ui.main.ImMainActivity
import com.dada.core.common.utils.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 欢迎页（兼任启动入口）
 *
 * 流程：
 *  1. 已登录 -> 直接进入主页
 *  2. 未登录 -> 自动注册（生成随机用户名 + 设备 ID），成功后展示用户信息再进入主页
 *  3. 注册失败 -> 显示「重试」按钮
 *
 * 进入主页前会做两件事：
 *  - 启动后台 WebSocket Service（保持长连接）
 *  - 申请通知权限（Android 13+）
 */
@AndroidEntryPoint
class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {

    private val viewModel: WelcomeViewModel by viewModels()

    /** 极光推送管理器（用于绑定/解绑别名 + 上报 token） */
    @Inject lateinit var pushManager: PushManager

    @Inject lateinit var userPreferences: UserPreferences

    override fun inflateBinding(): ActivityWelcomeBinding {
        return ActivityWelcomeBinding.inflate(layoutInflater)
    }

    override fun initView() {
        binding.btnEnter.setOnClickListener { enterChat() }
    }

    override fun initData() {
        // 已登录：跳过注册，直接进入主页
        if (userPreferences.isLoggedIn()) {
            enterChat()
            return
        }

        // 未登录：观察注册流程并发起注册
        observeViewModel()
        viewModel.register()
    }

    // ============================== 状态观察 ==============================

    /**
     * 观察 UI 状态：加载中 / 成功 / 失败
     *
     * 注意：此处使用 lifecycleScope.launch（非 repeatOnLifecycle），
     * 因为欢迎页生命周期较短，且需要在后台时也能完成注册回调
     */
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when {
                    state.isLoading -> showLoading()
                    state.user != null -> showSuccess(state.user.username, state.user.id)
                    state.error != null -> showError(state.error)
                }
            }
        }
    }

    // ============================== UI 状态 ==============================

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvLoading.visibility = View.VISIBLE
        binding.cardUserInfo.visibility = View.GONE
        binding.btnEnter.visibility = View.GONE
    }

    private fun showSuccess(username: String, userId: Long) {
        binding.progressBar.visibility = View.GONE
        binding.tvLoading.visibility = View.GONE
        binding.cardUserInfo.visibility = View.VISIBLE
        binding.btnEnter.visibility = View.VISIBLE

        binding.tvUsername.text = username
        binding.tvUserId.text = "ID: $userId"
    }

    private fun showError(error: String) {
        binding.progressBar.visibility = View.GONE
        binding.tvLoading.visibility = View.GONE
        ToastUtil.show(error)

        binding.btnEnter.apply {
            visibility = View.VISIBLE
            text = "重试"
            setOnClickListener { viewModel.register() }
        }
    }

    // ============================== 进入主页 ==============================

    /**
     * 进入主页前的统一处理：
     *  - 申请通知权限
     *  - 启动后台 WebSocket Service
     *  - 绑定 JPush 别名（让服务端能定向推送）
     *  - 跳转到 ImMainActivity 并 finish 自己
     */
    private fun enterChat() {
        requestNotificationPermissionIfNeeded()
        WebSocketService.start(this)

        // 绑定极光别名 = userId，让后端可以靠 userId 定向推送
        pushManager.onUserLogin(userPreferences.getUserId())

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * Android 13+ 需要运行时申请 POST_NOTIFICATIONS
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATION
            )
        }
    }

    companion object {
        private const val REQ_NOTIFICATION = 0x2001
    }
}
