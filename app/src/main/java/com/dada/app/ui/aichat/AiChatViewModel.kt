package com.dada.app.ui.aichat

import android.os.Handler
import android.os.Looper
import com.dada.core.common.base.BaseViewModel
import com.dada.domain.aichat.repository.AiChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val repository: AiChatRepository,
) : BaseViewModel() {

    companion object {
        private const val UI_THROTTLE_MS = 60L
    }

    // ============================== State ==============================

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<AiChatEffect>(extraBufferCapacity = 4)
    val effect: SharedFlow<AiChatEffect> = _effect.asSharedFlow()

    // ============================== 内部状态 ==============================

    private val messages = mutableListOf<ChatMessage>()
    private var historyLoaded = false

    // 节流
    private val handler = Handler(Looper.getMainLooper())
    private var pendingContent: String? = null
    private var pendingReasoning: String? = null
    private var uiUpdateScheduled = false

    init {
        loadHistory()
    }

    // ============================== 加载历史 ==============================

    private fun loadHistory() {
        launch {
            repository.observeMessages().collect { entities ->
                if (historyLoaded) return@collect
                historyLoaded = true

                if (entities.isEmpty()) {
                    val welcome = ChatMessage("assistant", "你好！我是 AI 智能助手，有什么可以帮你的吗？", isUser = false)
                    messages.add(welcome)
                    launchIO { repository.insertMessage(welcome.toEntity()) }
                } else {
                    entities.forEach { messages.add(ChatMessage.fromEntity(it)) }
                }
                emitState()
            }
        }
    }

    // ============================== 状态发射 ==============================

    private fun emitState() {
        _uiState.value = AiChatUiState(
            messages = messages.toList(),
            isStreaming = _uiState.value.isStreaming,
        )
    }

    // ============================== Public API ==============================

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // 本地立即添加用户消息（乐观更新）
        val userMsg = ChatMessage("user", text, isUser = true)
        messages.add(userMsg)
        emitState()

        // 本地添加 AI 占位（显示"正在思考..."直到收到第一个 chunk）
        val aiMsg = ChatMessage("assistant", "正在思考...", isUser = false)
        messages.add(aiMsg)
        _uiState.value = AiChatUiState(messages = messages.toList(), isStreaming = true)

        // 通过 Repository 发送，收集流式回复
        launch {
            try {
                repository.sendMessage(text).collect { chunk ->
                    throttledUpdate(chunk.content, chunk.reasoning.takeIf { it.isNotEmpty() })
                }
                // 流结束
                flushFinalUpdate()
            } catch (e: Exception) {
                handleStreamError("请求失败: ${e.message}")
            }
        }
    }

    fun cancelRequest() {
        repository.cancelRequest()
    }

    // ============================== 节流 UI 更新 ==============================

    private fun throttledUpdate(content: String, reasoning: String?) {
        pendingContent = content
        pendingReasoning = reasoning
        if (!uiUpdateScheduled) {
            uiUpdateScheduled = true
            handler.postDelayed(flushUiRunnable, UI_THROTTLE_MS)
        }
    }

    private val flushUiRunnable = Runnable {
        uiUpdateScheduled = false
        val content = pendingContent ?: return@Runnable

        val lastIndex = messages.size - 1
        if (lastIndex >= 0 && !messages[lastIndex].isUser) {
            messages[lastIndex].content = content
            messages[lastIndex].reasoningContent = pendingReasoning
        }

        _uiState.value = AiChatUiState(messages = messages.toList(), isStreaming = true)
    }

    private fun flushFinalUpdate() {
        handler.removeCallbacks(flushUiRunnable)
        uiUpdateScheduled = false

        val content = pendingContent
        val lastIndex = messages.size - 1
        if (lastIndex >= 0 && !messages[lastIndex].isUser) {
            if (content != null) {
                messages[lastIndex].content = content
                messages[lastIndex].reasoningContent = pendingReasoning
            } else if (messages[lastIndex].content == "正在思考...") {
                messages[lastIndex].content = ""
            }
        }

        pendingContent = null
        pendingReasoning = null

        _uiState.value = AiChatUiState(messages = messages.toList(), isStreaming = false)
    }

    private fun handleStreamError(message: String) {
        handler.removeCallbacks(flushUiRunnable)
        uiUpdateScheduled = false
        pendingContent = null
        pendingReasoning = null

        val lastIndex = messages.size - 1
        if (lastIndex >= 0 && !messages[lastIndex].isUser) {
            messages[lastIndex].content = message
        }

        _uiState.value = AiChatUiState(messages = messages.toList(), isStreaming = false)
    }

    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacksAndMessages(null)
        repository.cancelRequest()
    }
}
