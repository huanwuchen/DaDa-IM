package com.dada.core.network.websocket

import com.dada.core.common.utils.LogUtil
import com.dada.core.common.utils.GsonUtil
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * OkHttp WebSocketListener 实现
 */
class WebSocketListenerImpl(
    private val onOpen: () -> Unit,
    private val onMessage: (MessageModel) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onClosed: () -> Unit,
    private val onBinary: ((ByteArray) -> Unit)? = null,
    /** 原始文本帧回调（不做解析），用于 MessageManager 协议层处理。 */
    private val onRawText: ((String) -> Unit)? = null,
) : WebSocketListener() {

    override fun onOpen(webSocket: WebSocket, response: Response) {
        LogUtil.d(TAG, "WebSocket 连接成功: ${response.message}")
        onOpen.invoke()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        LogUtil.d(TAG, "收到消息: $text")

        // 先透出原始文本（MessageManager 用）
        try { onRawText?.invoke(text) } catch (e: Exception) {
            LogUtil.e(TAG, "onRawText 回调异常: ${e.message}")
        }

        // 旧路径：分发给旧监听器（需要拆 Frame 信封，控制帧直接忽略）
        try {
            val business = extractBusinessMessage(text) ?: return
            onMessage.invoke(business)
        } catch (e: Exception) {
            LogUtil.e(TAG, "消息解析失败: ${e.message}")
        }
    }

    /**
     * 把服务端帧拆出业务 MessageModel：
     *  - {type: ack|heartbeat|heartbeat_ack, ...}            → null（旧监听器不关心）
     *  - {type: text|image|..., payload: MessageModel}        → payload
     *  - 裸 MessageModel（旧服务端兼容）                       → 直接返回
     */
    private fun extractBusinessMessage(text: String): MessageModel? {
        val frame = runCatching { GsonUtil.fromJson(text, Frame::class.java) }.getOrNull()
        if (frame != null) {
            if (frame.isAck || frame.isHeartbeat || frame.isHeartbeatAck) return null
            frame.payload?.let { return it }
        }
        return runCatching { GsonUtil.fromJson(text, MessageModel::class.java) }.getOrNull()
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        LogUtil.d(TAG, "收到二进制帧: ${bytes.size} 字节")
        try {
            onBinary?.invoke(bytes.toByteArray())
        } catch (e: Exception) {
            LogUtil.e(TAG, "二进制帧处理失败: ${e.message}")
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        LogUtil.e(TAG, "WebSocket 连接失败: ${t.message}")
        onFailure.invoke(t.message ?: "未知错误")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        LogUtil.d(TAG, "WebSocket 连接关闭: code=$code, reason=$reason")
        onClosed.invoke()
    }

    companion object {
        private const val TAG = "WebSocketListener"
    }
}
