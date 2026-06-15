package com.dada.app.ui.chat

import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dada.app.R
import com.dada.core.ui.base.BaseActivity
import com.dada.app.databinding.ActivityWxChatBinding
import com.dada.core.network.websocket.WebSocketState
import com.dada.app.network.call.NetworkCallRouter
import com.dada.app.ui.chat.helper.ChatCallHelper
import com.dada.app.ui.chat.helper.MediaOpenHelper
import com.dada.app.ui.chat.helper.VoiceRecordHelper
import com.dada.core.common.utils.AppForegroundTracker
import com.dada.core.database.UserPreferences
import com.dada.core.network.websocket.WebSocketManager
import com.dada.app.utils.media.VoicePlayer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 微信风格的一对一聊天页面
 *
 * 数据来源：
 *  - 消息列表：从 Room 订阅（[WxChatViewModel.messages]）
 *  - 发送消息：交给 ViewModel，内部走 Repository 同时入库 + 推送 WebSocket
 *
 * 主要能力：
 *  1. 文本/图片/视频/语音/文件 五类消息收发
 *  2. Emoji 表情面板 + 「+」扩展面板（图片/视频/文件）
 *  3. 按住录音按钮发送语音
 *  4. 语音/视频通话发起（跳转到 [CallActivity]）
 *  5. 标题栏实时反映 WebSocket 连接状态
 */
@AndroidEntryPoint
class WxChatActivity : BaseActivity<ActivityWxChatBinding>() {

    private val viewModel: WxChatViewModel by viewModels()
    private lateinit var adapter: WxChatAdapter

    /** 全局语音播放器（注入单例，多页面共用） */
    @Inject lateinit var voicePlayer: VoicePlayer

    @Inject lateinit var imageLoader: com.dada.core.imageloader.ImageLoader

    @Inject lateinit var callManager: com.dada.app.network.call.CallManager
    @Inject lateinit var webSocketManager: WebSocketManager
    @Inject lateinit var userPreferences: UserPreferences

    /** 按住说话录音助手（封装触摸交互 + 权限请求） */
    private lateinit var voiceRecordHelper: VoiceRecordHelper

    /** 媒体打开助手（图片预览 / 视频播放 / 文件打开 / 语音播放） */
    private lateinit var mediaOpenHelper: MediaOpenHelper

    /** 语音/视频通话助手（通话发起 + 权限管理） */
    private lateinit var chatCallHelper: ChatCallHelper

    // ============================== ActivityResult ==============================

    /** 选图（PickVisualMedia，自动适配 Android 13+ Photo Picker） */
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.sendImage(it) } }

    /** 选视频 */
    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.sendVideo(it) } }

    /** 选文件（OpenDocument 拿到的 Uri 永久可读） */
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.sendFile(it) } }

    // ============================== 生命周期 ==============================

    override fun inflateBinding(): ActivityWxChatBinding =
        ActivityWxChatBinding.inflate(layoutInflater)

    override fun initView() {
        if (viewModel.targetUserId <= 0L) {
            Toast.makeText(this, "用户信息错误", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 创建 Helper 实例
        mediaOpenHelper = MediaOpenHelper(this, voicePlayer)
        voiceRecordHelper = VoiceRecordHelper(this, binding) { file, ms ->
            viewModel.sendVoice(file, ms)
        }
        val networkCallRouter = NetworkCallRouter(webSocketManager, userPreferences)
        networkCallRouter.start(lifecycleScope)
        chatCallHelper = ChatCallHelper(this, binding, callManager, networkCallRouter).apply {
            targetUserId = viewModel.targetUserId
            targetUsername = viewModel.targetUsername
        }

        setupToolbar()
        setupMessageList()
        setupInputArea()
        setupExtraPanel()
        voiceRecordHelper.setup()
        chatCallHelper.setup()
    }

    override fun initData() {
        // 标记当前聊天对象（用于全局消息通知判断是否免打扰）
        AppForegroundTracker.setCurrentChat(viewModel.targetUserId)
        bindBaseViewModel(viewModel)

        // 3.4 单一 UiState 订阅：所有 UI 数据来自 viewModel.uiState
        observe(viewModel.uiState) { state ->
            // 消息列表
            adapter.submitList(state.messages) {
                if (state.messages.isNotEmpty()) {
                    binding.rvMessages.scrollToPosition(state.messages.lastIndex)
                }
            }
            // 标题栏（结合连接状态）
            binding.tvTitle.text = when (state.connectionState) {
                WebSocketState.CONNECTED -> state.peer.username
                WebSocketState.DISCONNECTED -> "${state.peer.username} (未连接)"
                WebSocketState.CONNECTING -> "${state.peer.username} (连接中...)"
                WebSocketState.RECONNECTING -> "${state.peer.username} (重连中...)"
            }
        }

        // 一次性副作用
        observe(viewModel.effect) { eff ->
            when (eff) {
                is WxChatEffect.ShowToast -> Toast.makeText(this, eff.message, Toast.LENGTH_SHORT).show()
                is WxChatEffect.SendFailed -> Toast.makeText(this, "发送失败：${eff.reason}", Toast.LENGTH_SHORT).show()
                WxChatEffect.FinishPage -> finish()
            }
        }

        viewModel.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        AppForegroundTracker.clearCurrentChat()
        mediaOpenHelper.stopVoice()
    }

    // ============================== 初始化 ==============================

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvTitle.text = viewModel.targetUsername
    }

    private fun setupMessageList() {
        adapter = WxChatAdapter(
            myUserId = viewModel.myUserId,
            imageLoader = imageLoader,
            callbacks = WxChatAdapter.Callbacks(
                // 图片：跳到 ImagePreviewActivity（带起始位置过渡动画）
                onImageClick = { msg, imageView ->
                    mediaOpenHelper.openImagePreview(msg.content, imageView, adapter.currentList)
                },
                // 视频：跳到视频播放页
                onVideoClick = { msg, _ ->
                    mediaOpenHelper.openVideo(msg.content, viewModel.targetUsername)
                },
                // 语音：内置播放器
                onVoiceClick = { msg, _ ->
                    mediaOpenHelper.playVoice(msg.content)
                },
                // 文件：用系统能力打开
                onFileClick = { msg, _ ->
                    mediaOpenHelper.openExternalUrl(msg.content)
                },
            )
        )
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@WxChatActivity).apply {
                stackFromEnd = true
            }
            adapter = this@WxChatActivity.adapter
        }
    }

    private fun setupInputArea() {
        // 文本框监听：有内容时显示发送按钮，空时显示「+」扩展
        binding.etInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                binding.btnSend.visibility = if (hasText) View.VISIBLE else View.GONE
                binding.ivPlus.visibility = if (hasText) View.GONE else View.VISIBLE
            }
        })

        binding.btnSend.setOnClickListener { sendCurrentText() }
        binding.etInput.setOnEditorActionListener { _, _, _ ->
            sendCurrentText()
            true
        }

        // Emoji 按钮：切换表情面板
        binding.emojiPanel.bindEditText(binding.etInput)
        binding.ivEmoji.setOnClickListener {
            togglePanel(showEmoji = binding.emojiPanel.visibility != View.VISIBLE, showExtra = false)
        }

        // 「+」按钮：切换扩展面板
        binding.ivPlus.setOnClickListener {
            togglePanel(showEmoji = false, showExtra = binding.extraPanel.visibility != View.VISIBLE)
        }

        // 切换「文本输入 / 按住说话」
        binding.ivVoice.setOnClickListener {
            val showVoice = binding.btnHoldToTalk.visibility != View.VISIBLE
            binding.btnHoldToTalk.visibility = if (showVoice) View.VISIBLE else View.GONE
            binding.etInput.visibility = if (showVoice) View.GONE else View.VISIBLE
            binding.ivVoice.setImageResource(
                if (showVoice) R.drawable.ic_chat_keyboard else R.drawable.ic_chat_voice
            )
            togglePanel(showEmoji = false, showExtra = false)
        }
    }

    private fun setupExtraPanel() {
        // 图片选择
        binding.btnPickImage.setOnClickListener {
            togglePanel(showEmoji = false, showExtra = false)
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        // 视频选择
        binding.btnPickVideo.setOnClickListener {
            togglePanel(showEmoji = false, showExtra = false)
            pickVideoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }
        // 文件选择
        binding.btnPickFile.setOnClickListener {
            togglePanel(showEmoji = false, showExtra = false)
            pickFileLauncher.launch(arrayOf("*/*"))
        }
    }

    /**
     * 同时控制 Emoji 面板 和 「+」扩展面板（同时只允许其中一个可见）
     */
    private fun togglePanel(showEmoji: Boolean, showExtra: Boolean) {
        binding.emojiPanel.visibility = if (showEmoji) View.VISIBLE else View.GONE
        binding.extraPanel.visibility = if (showExtra) View.VISIBLE else View.GONE
    }

    private fun sendCurrentText() {
        val content = binding.etInput.text.toString().trim()
        if (content.isBlank()) {
            Toast.makeText(this, "请输入消息", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.sendTextMessage(content)
        binding.etInput.setText("")
    }

    // ============================== 权限（转发给对应 Helper） ==============================

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        voiceRecordHelper.onRequestPermissionsResult(requestCode, grantResults)
        chatCallHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        const val EXTRA_TARGET_USER_ID = "extra_target_user_id"
        const val EXTRA_TARGET_USERNAME = "extra_target_username"
        const val EXTRA_TARGET_AVATAR = "extra_target_avatar"
    }
}
