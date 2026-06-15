package com.dada.core.network.websocket

/**
 * WebSocket 事件监听器（用于 UI 层回调）
 */
interface WebSocketListener {

    /**
     * 连接成功
     */
    fun onConnected()

    /**
     * 连接断开
     */
    fun onDisconnected()

    /**
     * 收到新消息
     */
    fun onMessageReceived(message: MessageModel)

    /**
     * 连接失败
     */
    fun onConnectFailed(error: String)

    /**
     * 收到二进制帧（音视频数据）
     * 默认空实现，不影响现有监听器
     */
    fun onBinaryReceived(data: ByteArray) {}
}
