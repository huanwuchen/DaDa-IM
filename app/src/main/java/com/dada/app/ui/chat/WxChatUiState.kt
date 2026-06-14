package com.dada.app.ui.chat

import com.dada.core.common.domain.model.Message
import com.dada.core.network.websocket.WebSocketState

/**
 * 聊天页面 UI 状态（单一数据源 / SSOT）
 *
 * 由 [WxChatViewModel] 通过 combine 多条数据源（Room 消息流、WebSocket 连接状态、
 * 通话状态）聚合产出。UI 层只订阅 [WxChatViewModel.uiState] 一个 Flow。
 *
 * 3.4 重构产物：替代以前散落的 messages / connectionState / isLoading 等多个 Flow。
 */
data class WxChatUiState(
    val peer: PeerHeader = PeerHeader(),
    val messages: List<Message> = emptyList(),
    val connectionState: WebSocketState = WebSocketState.DISCONNECTED,
    val isInputEnabled: Boolean = true,
    val isLoading: Boolean = false,
) {
    /** 对方信息（用于标题栏） */
    data class PeerHeader(
        val id: Long = 0L,
        val username: String = "",
        val avatar: String? = null,
    )

    val isConnected: Boolean get() = connectionState == WebSocketState.CONNECTED
    val isEmpty: Boolean get() = messages.isEmpty()
}

/**
 * 聊天页面一次性副作用（导航、Toast、Snackbar 等）
 *
 * UI 通过订阅 effect Flow 来响应，每个事件只消费一次。
 */
sealed interface WxChatEffect {
    data class ShowToast(val message: String) : WxChatEffect
    data class SendFailed(val reason: String) : WxChatEffect
    object FinishPage : WxChatEffect
}
