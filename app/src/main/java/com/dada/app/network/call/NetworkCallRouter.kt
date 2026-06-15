package com.dada.app.network.call

import android.app.Activity
import com.dada.app.BuildConfig
import com.dada.core.common.utils.LogUtil
import com.dada.core.database.UserPreferences
import com.dada.core.network.websocket.MessageModel
import com.dada.core.network.websocket.WebSocketListener
import com.dada.core.network.websocket.WebSocketManager
import com.dada.core.network.websocket.WebSocketState
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.qcloud.tuikit.tuicallkit.debug.GenerateTestUserSig
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuicore.interfaces.TUICallback
import com.tencent.qcloud.tuikit.tuicallengine.TUICallDefine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlin.coroutines.resume

/**
 * 通话路由器：自动检测局域网，决定走 P2P 还是 TUICallKit。
 *
 * 流程：
 *  1. 通过 WebSocket 查询对方设备的私有 IP
 *  2. 本机开一个临时 TCP ServerSocket，把 IP+端口发给对方
 *  3. 对方收到后尝试 TCP 连接一次
 *  4. 连接成功 → 同一局域网，走 P2P
 *  5. 连接超时 → 非局域网，走 TUICallKit
 */
class NetworkCallRouter(
    private val webSocketManager: WebSocketManager,
    private val userPreferences: UserPreferences,
) {

    companion object {
        private const val TAG = "NetworkCallRouter"
        private const val QUERY_TIMEOUT_MS = 3000L
        private const val PROBE_TIMEOUT_MS = 2000L
    }

    @Volatile
    private var tuiLoggedIn = false

    /**
     * 探测请求监听器。
     * 收到 probe-request 后，尝试 TCP 连接对方（作为探测的被探测方）。
     * 由 [start] 注册。
     */
    private val probeListener = object : WebSocketListener {
        override fun onConnected() {}
        override fun onDisconnected() {}
        override fun onConnectFailed(error: String) {}
        override fun onMessageReceived(message: MessageModel) {
            if (message.type != "probe-request") return
            val myId = userPreferences.getUserId()
            if (message.toId != myId) return

            val content = message.content
            if (content.isBlank()) return
            // 格式: "{queryId}:{ip}:{port}"
            val parts = content.split(":")
            if (parts.size < 3) return
            val peerIp = parts[1]
            val peerPort = parts[2].toIntOrNull() ?: return

            LogUtil.d(TAG, "收到探测请求: from=${message.fromId}, peer=$peerIp:$peerPort")
            // 在后台线程尝试 TCP 连接对方
            Thread {
                var socket: Socket? = null
                try {
                    socket = Socket()
                    socket.connect(InetSocketAddress(peerIp, peerPort), PROBE_TIMEOUT_MS.toInt())
                    LogUtil.d(TAG, "探测成功: 可连接 $peerIp:$peerPort")
                } catch (e: Exception) {
                    LogUtil.d(TAG, "探测失败: 无法连接 $peerIp:$peerPort, ${e.message}")
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                }
            }.start()
        }
    }

    /**
     * 启动探测功能：注册监听器 + 监听连接状态自动上报局域网 IP。
     * 在 Activity/Service 初始化时调用一次即可。
     */
    fun start(scope: CoroutineScope) {
        webSocketManager.addListener(probeListener)
        // 监听 WebSocket 连接状态，连接成功后自动上报本机局域网 IP
        scope.launch {
            webSocketManager.connectionState.collect { state ->
                if (state == WebSocketState.CONNECTED) {
                    reportLocalIp()
                }
            }
        }
    }

    /** 注销探测监听器 */
    fun unregisterProbeListener() {
        webSocketManager.removeListener(probeListener)
    }

    /**
     * 路由通话：检测局域网 → 选择通话方式。
     *
     * @return true  = 局域网，调用方应继续走 CallManager + CallActivity
     *         false = 非局域网，已通过 TUICallKit 发起通话（或 TUICallKit 失败）
     */
    suspend fun routeCall(
        activity: Activity,
        peerUserId: Long,
        peerUsername: String,
        isVideo: Boolean,
    ): Boolean {
        val onLan = isPeerOnLan(peerUserId)
        if (onLan) {
            LogUtil.d(TAG, "同一局域网，走 P2P 直连")
            return true
        }

        LogUtil.d(TAG, "非局域网，使用 TUICallKit 云端通话")
        val ok = startTuiCall(activity, peerUserId, isVideo)
        if (!ok) {
            LogUtil.e(TAG, "TUICallKit 通话发起失败")
        }
        return false
    }

    // ============================== 局域网探测 ==============================

    /**
     * WebSocket 连接成功后调用，向服务器上报本机局域网 IP。
     * 服务器存储 userId → localIp 映射，供其他设备查询。
     */
    fun reportLocalIp() {
        val localIp = getLocalIpAddress()
        if (localIp.isNullOrBlank()) {
            LogUtil.w(TAG, "无法获取本机 IP，跳过上报")
            return
        }
        val message = MessageModel(
            fromId = userPreferences.getUserId(),
            toId = 0,
            content = localIp,
            type = "report-local-ip",
        )
        webSocketManager.sendMessage(message)
        LogUtil.d(TAG, "上报局域网 IP: $localIp")
    }

    /**
     * 探测对方是否在同一局域网。
     *
     * 流程：
     *  1. 通过服务器查询对方的局域网 IP（服务器存储了每个设备上报的 IP）
     *  2. 本机开 TCP ServerSocket，把 IP+端口通过 WebSocket 发给对方
     *  3. 对方收到后尝试 TCP 连接
     *  4. 连接成功 → 同一局域网，走 P2P
     *  5. 连接超时 → 非局域网，走 TUICallKit
     */
    private suspend fun isPeerOnLan(peerUserId: Long): Boolean {
        val localIp = getLocalIpAddress()
        if (localIp.isNullOrBlank()) {
            LogUtil.w(TAG, "无法获取本机 IP，视为非局域网")
            return false
        }

        val peerIp = queryPeerIpAddress(peerUserId)
        if (peerIp.isNullOrBlank()) {
            LogUtil.d(TAG, "无法获取对方局域网 IP，视为非局域网")
            return false
        }
        LogUtil.d(TAG, "探测开始: local=$localIp, peer=$peerIp")

        // 在 IO 线程开 ServerSocket，通过 WebSocket 告诉对方来连
        return withContext(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(0) // 随机端口
                val probePort = serverSocket.localPort
                serverSocket.soTimeout = (PROBE_TIMEOUT_MS + 500).toInt() // 比对方超时多 500ms

                // 发送探测请求给对方
                val queryId = UUID.randomUUID().toString().take(8)
                sendProbeRequest(peerUserId, queryId, localIp, probePort)

                // 等待对方 TCP 连接进来（soTimeout 控制超时）
                val accepted = try {
                    serverSocket.accept()
                } catch (_: java.net.SocketTimeoutException) {
                    null
                }

                if (accepted != null) {
                    LogUtil.d(TAG, "探测成功: 对方 TCP 连接成功")
                    try { accepted.close() } catch (_: Exception) {}
                    true
                } else {
                    LogUtil.d(TAG, "探测超时: 对方无法 TCP 连接")
                    false
                }
            } catch (e: Exception) {
                LogUtil.d(TAG, "探测异常: ${e.message}")
                false
            } finally {
                try { serverSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    /**
     * 通过服务器查询对方的局域网 IP。
     * 服务器存储了每个设备连接时上报的 localIp。
     * 用 queryId 匹配请求-响应，避免旧消息干扰。
     */
    private suspend fun queryPeerIpAddress(peerUserId: Long): String? {
        val queryId = UUID.randomUUID().toString().take(8)
        val channel = Channel<String?>(Channel.CONFLATED)

        val listener = object : WebSocketListener {
            override fun onConnected() {}
            override fun onDisconnected() {}
            override fun onConnectFailed(error: String) {}
            override fun onMessageReceived(message: MessageModel) {
                if (message.type != "peer-ip-result") return
                val content = message.content
                if (content.isBlank()) {
                    channel.trySend(null)
                    return
                }
                // 格式: "{queryId}:{peerLanIp}"
                val parts = content.split(":", limit = 2)
                if (parts.size == 2 && parts[0] == queryId) {
                    channel.trySend(parts[1])
                }
            }
        }

        webSocketManager.addListener(listener)
        try {
            val message = MessageModel(
                fromId = userPreferences.getUserId(),
                toId = peerUserId,
                content = "$peerUserId:$queryId",
                type = "query-peer-ip",
            )
            webSocketManager.sendMessage(message)
            LogUtil.d(TAG, "查询对方 IP: to=$peerUserId, queryId=$queryId")

            return withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                channel.receive()
            }
        } finally {
            webSocketManager.removeListener(listener)
            channel.close()
        }
    }

    /** 通过 WebSocket 发送探测请求给对方 */
    private fun sendProbeRequest(peerUserId: Long, queryId: String, ip: String, port: Int) {
        val message = MessageModel(
            fromId = userPreferences.getUserId(),
            toId = peerUserId,
            content = "$queryId:$ip:$port",
            type = "probe-request",
        )
        webSocketManager.sendMessage(message)
        LogUtil.d(TAG, "发送探测请求: to=$peerUserId, $ip:$port")
    }

    // ============================== TUICallKit ==============================

    /**
     * 通过 TUICallKit 发起云端通话。
     * 自动完成 TUILogin（仅首次）。
     *
     * @return true 发起成功，false 发起失败
     */
    private suspend fun startTuiCall(activity: Activity, peerUserId: Long, isVideo: Boolean): Boolean {
        if (!ensureTuiLogin(activity)) {
            LogUtil.e(TAG, "TUICallKit 登录失败，无法发起通话")
            return false
        }
        val mediaType = if (isVideo) TUICallDefine.MediaType.Video else TUICallDefine.MediaType.Audio
        TUICallKit.createInstance(activity).call(peerUserId.toString(), mediaType)
        return true
    }

    /**
     * 确保 TUICallKit 已登录。首次调用时登录，后续直接返回。
     */
    private suspend fun ensureTuiLogin(activity: Activity): Boolean {
        if (tuiLoggedIn) return true
        if (BuildConfig.TUI_SDK_APP_ID <= 0) return false

        return suspendCancellableCoroutine { cont ->
            val userId = userPreferences.getUserId().toString()
            val userSig = GenerateTestUserSig.genTestUserSig(userId, BuildConfig.TUI_SDK_APP_ID, BuildConfig.TUI_SECRET_KEY)

            TUILogin.login(activity, BuildConfig.TUI_SDK_APP_ID, userId, userSig, object : TUICallback() {
                override fun onSuccess() {
                    tuiLoggedIn = true
                    LogUtil.d(TAG, "TUICallKit 登录成功: userId=$userId")
                    if (cont.isActive) cont.resume(true)
                }

                override fun onError(code: Int, message: String?) {
                    LogUtil.e(TAG, "TUICallKit 登录失败: code=$code, msg=$message")
                    if (cont.isActive) cont.resume(false)
                }
            })

            cont.invokeOnCancellation { /* TUILogin 无法取消，忽略 */ }
        }
    }

    // ============================== 工具方法 ==============================

    private fun getLocalIpAddress(): String? {
        return com.dada.app.network.call.voice.VoiceEngine.getLocalIpAddress()
    }
}
