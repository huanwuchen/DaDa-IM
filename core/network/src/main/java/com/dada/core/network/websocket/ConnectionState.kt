package com.dada.core.network.websocket

/**
 * 细粒度连接状态（升级版）。
 *
 * 与旧 [WebSocketState] enum 并存：
 *  - [WebSocketState] 仅描述物理 TCP/WebSocket 链路（旧调用方继续使用）
 *  - [ConnectionState] 由 MessageManager 维护，融合心跳裁决，是业务可见的"有效"状态
 */
sealed interface ConnectionState {
    object Idle : ConnectionState
    object Connecting : ConnectionState
    object Connected : ConnectionState
    /** 心跳超时但 socket 尚未确认断开，正在裁决中 */
    object HalfOpen : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState
    data class Dead(val reason: Throwable?) : ConnectionState
}
