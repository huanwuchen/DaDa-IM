package com.dada.core.network.websocket

import com.dada.core.common.utils.LogUtil
import com.dada.core.common.utils.GsonUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket 管理器。
 *
 * 角色变化（可靠消息升级）：
 *  - 旧：连接 + 心跳 + 消息发送 + 监听器分发，一锅端
 *  - 新：仅负责连接生命周期与原始字节收发；可靠性、心跳 ack、重发交由 [MessageManager]
 *
 * 向后兼容：旧的 [sendMessage] / [addListener] / [connectionState] / 自带心跳 仍然保留，
 * 现有调用方无需立即改动。新代码请使用 [richState] + [incomingRaw] + [sendRaw]。
 */
@Singleton
class WebSocketManager @Inject constructor() {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // 长连接，禁用读超时
            .writeTimeout(10, TimeUnit.SECONDS)
            // 不再依赖 OkHttp 自带心跳，应用层心跳由 MessageManager 接管
            .build()
    }

    private var webSocket: WebSocket? = null
    private var currentUserId: Long? = null
    private var currentBaseUrl: String = DEFAULT_BASE_URL
    private var reconnectJob: Job? = null
    private var reconnectCount = 0

    /** 旧版心跳，新代码不要用。保留是为了让没切换到 MessageManager 的旧调用方继续可用。 */
    private val legacyHeartBeat = HeartBeatManager { msg -> sendRaw(msg) }

    // ───────────── 旧 API（向后兼容） ─────────────
    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState

    private val listeners = mutableListOf<WebSocketListener>()

    // ───────────── 新 API（供 MessageManager 使用） ─────────────
    private val _richState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val richState: StateFlow<ConnectionState> = _richState.asStateFlow()

    /** 原始入站文本帧。MessageManager 订阅后做协议解析与去重。 */
    private val _incomingRaw = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 256,
    )
    val incomingRaw: SharedFlow<String> = _incomingRaw.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 是否启用旧心跳。MessageManager 接管后传 false。 */
    @Volatile var legacyHeartbeatEnabled: Boolean = true

    /**
     * 连接 WebSocket。
     * @param userId  用户 ID
     * @param baseUrl 服务器 base，默认 [DEFAULT_BASE_URL]
     */
    fun connect(userId: Long, baseUrl: String = DEFAULT_BASE_URL) {
        if (_richState.value is ConnectionState.Connected ||
            _richState.value is ConnectionState.Connecting) {
            LogUtil.w(TAG, "WebSocket 已在连接中/已连接，忽略 connect")
            return
        }
        currentUserId = userId
        currentBaseUrl = baseUrl
        doConnect()
    }

    private fun doConnect() {
        val userId = currentUserId ?: return
        _richState.value = ConnectionState.Connecting
        _connectionState.value = WebSocketState.CONNECTING

        val url = "$currentBaseUrl/ws/$userId"
        LogUtil.d(TAG, "开始连接 WebSocket: $url")

        val request = Request.Builder().url(url).build()
        val listener = WebSocketListenerImpl(
            onOpen = {
                reconnectCount = 0
                _richState.value = ConnectionState.Connected
                _connectionState.value = WebSocketState.CONNECTED
                if (legacyHeartbeatEnabled) legacyHeartBeat.start()
                notifyConnected()
            },
            onMessage = { message ->
                // 兼容旧分发路径
                notifyMessageReceived(message)
            },
            onRawText = { text ->
                // 新路径：把原始文本透出给 MessageManager
                _incomingRaw.tryEmit(text)
            },
            onFailure = { error ->
                _connectionState.value = WebSocketState.DISCONNECTED
                legacyHeartBeat.stop()
                notifyConnectFailed(error)
                scheduleReconnect(error)
            },
            onClosed = {
                _connectionState.value = WebSocketState.DISCONNECTED
                legacyHeartBeat.stop()
                notifyDisconnected()
                scheduleReconnect("closed")
            },
            onBinary = { data ->
                notifyBinaryReceived(data)
            }
        )
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    private fun scheduleReconnect(reason: String) {
        if (reconnectCount >= MAX_RECONNECT_COUNT) {
            LogUtil.e(TAG, "重连次数达到上限，进入 Dead")
            _richState.value = ConnectionState.Dead(IllegalStateException(reason))
            return
        }
        reconnectCount++
        _richState.value = ConnectionState.Reconnecting(reconnectCount)
        _connectionState.value = WebSocketState.RECONNECTING

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY)
            LogUtil.d(TAG, "重连 WebSocket，第 $reconnectCount 次")
            doConnect()
        }
    }

    /** 旧 API：发送结构化消息。内部走 [sendRaw]。 */
    fun sendMessage(message: MessageModel): Boolean {
        if (_richState.value !is ConnectionState.Connected) {
            LogUtil.e(TAG, "WebSocket 未连接，sendMessage 失败")
            return false
        }
        return sendRaw(GsonUtil.toJson(message))
    }

    /** 二进制（音视频）。 */
    fun sendBinary(data: ByteArray): Boolean {
        if (_richState.value !is ConnectionState.Connected) return false
        return try {
            webSocket?.send(data.toByteString()) ?: false
        } catch (e: Exception) {
            LogUtil.e(TAG, "sendBinary 异常: ${e.message}")
            false
        }
    }

    /**
     * 新 API：发送原始文本帧。供 [MessageManager] 调用。
     * 返回值仅表示是否进入 OkHttp 发送缓冲，不代表对端收到。
     */
    fun sendRaw(text: String): Boolean {
        return try {
            val ok = webSocket?.send(text) ?: false
            if (!ok) LogUtil.e(TAG, "sendRaw 失败（缓冲已满或未连接）")
            ok
        } catch (e: Exception) {
            LogUtil.e(TAG, "sendRaw 异常: ${e.message}")
            false
        }
    }

    /** 应用层判定为"假连接"时调用：撕掉 socket 并触发重连。 */
    fun forceReconnect(reason: String) {
        LogUtil.w(TAG, "forceReconnect: $reason")
        try { webSocket?.cancel() } catch (_: Exception) {}
        webSocket = null
        legacyHeartBeat.stop()
        scheduleReconnect(reason)
    }

    fun disconnect() {
        LogUtil.d(TAG, "主动断开 WebSocket")
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectCount = 0
        legacyHeartBeat.stop()
        webSocket?.close(1000, "client disconnect")
        webSocket = null
        _connectionState.value = WebSocketState.DISCONNECTED
        _richState.value = ConnectionState.Idle
    }

    // ───────────── 旧监听器系统（保留） ─────────────
    fun addListener(listener: WebSocketListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }
    fun removeListener(listener: WebSocketListener) { listeners.remove(listener) }
    fun clearListeners() { listeners.clear() }

    private fun notifyConnected() = scope.launch(Dispatchers.Main) {
        listeners.forEach { it.onConnected() }
    }
    private fun notifyDisconnected() = scope.launch(Dispatchers.Main) {
        listeners.forEach { it.onDisconnected() }
    }
    private fun notifyMessageReceived(message: MessageModel) = scope.launch(Dispatchers.Main) {
        listeners.forEach { it.onMessageReceived(message) }
    }
    private fun notifyConnectFailed(error: String) = scope.launch(Dispatchers.Main) {
        listeners.forEach { it.onConnectFailed(error) }
    }
    private fun notifyBinaryReceived(data: ByteArray) = scope.launch(Dispatchers.Main) {
        listeners.forEach { it.onBinaryReceived(data) }
    }

    fun release() {
        disconnect()
        legacyHeartBeat.release()
        clearListeners()
        scope.cancel()
    }

    companion object {
        private const val TAG = "WebSocketManager"
        private val DEFAULT_BASE_URL: String
            get() = com.dada.core.network.BuildConfig.SERVER_WS_URL
        private const val RECONNECT_DELAY = 3000L
        private const val MAX_RECONNECT_COUNT = 5
    }
}
