package com.dada.core.network.websocket

/**
 * WebSocket 连接状态
 */
enum class WebSocketState {
    DISCONNECTED,   // 未连接
    CONNECTING,     // 连接中
    CONNECTED,      // 已连接
    RECONNECTING    // 重连中
}
