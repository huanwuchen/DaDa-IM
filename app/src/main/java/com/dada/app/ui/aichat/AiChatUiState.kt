package com.dada.app.ui.aichat

/**
 * AI 聊天页面 UI 状态（单一数据源）
 */
data class AiChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
)

/**
 * AI 聊天页面一次性副作用
 */
sealed interface AiChatEffect {
    data class ShowToast(val message: String) : AiChatEffect
}
