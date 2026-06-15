
package com.dada.app.network.call

import android.content.Context
import com.dada.core.common.utils.LogUtil
import android.view.SurfaceView
import androidx.lifecycle.LifecycleOwner
import com.dada.app.network.call.video.EncodingMode
import com.dada.app.network.call.video.VideoEngine
import com.dada.app.network.call.voice.VoiceEngine
import com.dada.core.network.websocket.MessageModel
import com.dada.core.network.websocket.WebSocketListener
import com.dada.core.network.websocket.WebSocketManager
import com.dada.core.database.UserPreferences
import com.dada.core.common.utils.GsonUtil
import com.otaliastudios.cameraview.CameraView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramSocket
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通话管理器
 *
 * 由 Hilt 注入（@Singleton），原 getInstance() 单例已废弃。
 * 信令: WebSocket (call-invite / call-accept / call-reject / call-hangup)
 * 音频: VoiceEngine (UDP 直传 PCM)
 * 视频: VideoEngine (UDP 直传 YUV)
 */
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext appContext: Context,
    private val userPreferences: UserPreferences,
    private val webSocketManager: WebSocketManager,
) {

    private var voiceEngine: VoiceEngine? = null
    private var videoEngine: VideoEngine? = null
    private val appContext: Context = appContext.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 当前通话 ID（每次通话唯一，防止旧消息干扰）
    private var currentCallId: String = ""

    // 通话状态
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState

    // 通话信息
    private val _callInfo = MutableStateFlow(CallInfo())
    val callInfo: StateFlow<CallInfo> = _callInfo

    // 通话计时
    private var callTimerJob: Job? = null
    private val _callDuration = MutableStateFlow(0L)
    val callDuration: StateFlow<Long> = _callDuration

    // 最近一次通话的时长和类型（reset 前保存，供 UI 插入通话提示消息）
    private var _lastCallDuration: Long = 0L
    val lastCallDuration: Long get() = _lastCallDuration
    private var _lastCallType: CallType = CallType.AUDIO
    val lastCallType: CallType get() = _lastCallType
    private var _lastIsOutgoing: Boolean = false
    val lastIsOutgoing: Boolean get() = _lastIsOutgoing

    // 预分配的本地 UDP socket
    private var pendingAudioRecvSocket: DatagramSocket? = null
    private var pendingVideoRecvSocket: DatagramSocket? = null

    // 视频 Surface
    // 本地预览：使用 CameraView（视频通话采集端）
    private var localCameraView: CameraView? = null
    // 远端显示：使用 SurfaceView（视频通话显示端）
    private var remoteSurfaceView: SurfaceView? = null
    // 生命周期所有者（用于 CameraView 自动管理生命周期）
    private var lifecycleOwner: LifecycleOwner? = null
    // 视频编码模式（默认 H.264，可由 UI 传入切换）
    private var videoEncodingMode: EncodingMode = EncodingMode.H264

    // 对端传输模式（"udp" 或 "websocket"）
    private var remoteTransport: String = "udp"

    // WebSocket 信令监听（在 init 块之前声明，避免初始化顺序问题）
    private val signalingListener = object : WebSocketListener {
        override fun onConnected() {}
        override fun onDisconnected() {}
        override fun onMessageReceived(message: MessageModel) {
            // 过滤自己发的消息（服务器会回显）
            val from = message.fromId ?: return
            if (from == userPreferences.getUserId()) return
            handleSignalingMessage(message)
        }
        override fun onConnectFailed(error: String) {}
        override fun onBinaryReceived(data: ByteArray) {
            handleBinaryMediaFrame(data)
        }
    }

    /**
     * 兼容旧调用：原 `init(...)` 现已无需手动调用，由 Hilt 完成依赖注入。
     * 保留空方法是为了不需要立刻清理 [com.dada.app.DaDaApp]。
     */
    @Deprecated("Hilt 已注入完成，无需手动 init", level = DeprecationLevel.WARNING)
    fun init(context: Context, userPreferences: UserPreferences, webSocketManager: WebSocketManager) {
        LogUtil.d(TAG, "CallManager.init() 已废弃；依赖通过 Hilt 注入完成")
    }

    init {
        webSocketManager.addListener(signalingListener)
        LogUtil.d(TAG, "CallManager 初始化完成（Hilt 注入）")
    }

    // ========== 信令处理 ==========

    private fun handleSignalingMessage(message: MessageModel) {
        when (message.type) {
            "call-invite" -> handleCallInvite(message)
            "call-accept" -> handleCallAccept(message)
            "call-reject" -> handleCallReject(message)
            "call-hangup" -> handleCallHangup(message)
        }
    }

    private fun handleCallInvite(message: MessageModel) {
        val fromId = message.fromId ?: return

        if (_callState.value != CallState.IDLE) {
            // 正在通话中，自动拒绝
            val rejectCallId = parseCallId(message.content)
            sendSignaling(fromId, CallPayload(rejectCallId, CallType.AUDIO, ""), "call-reject")
            LogUtil.d(TAG, "忙线中，自动拒绝: from=$fromId")
            return
        }

        val callId = parseCallId(message.content)
        val callType = parseCallType(message.content)
        val transport = parseTransport(message.content)
        if (callId.isBlank()) {
            LogUtil.w(TAG, "收到无 callId 的 invite，忽略")
            return
        }

        LogUtil.d(TAG, "收到来电: from=$fromId, callId=$callId, type=$callType, transport=$transport, content=${message.content}")
        currentCallId = callId
        remoteTransport = transport
        _callInfo.value = CallInfo(
            targetUserId = fromId,
            targetUsername = "用户$fromId",
            isOutgoing = false,
            callType = callType
        )
        _callState.value = CallState.RINGING

        // 提取邀请方的端点信息（为 accept 时启动引擎做准备）
        extractRemoteEndpoints(message.content)
    }

    private fun handleCallAccept(message: MessageModel) {
        if (_callState.value != CallState.CALLING) return
        val callId = parseCallId(message.content)
        if (callId != currentCallId) {
            LogUtil.d(TAG, "忽略旧通话的 accept: got=$callId, current=$currentCallId")
            return
        }
        val transport = parseTransport(message.content)
        LogUtil.d(TAG, "对方已接听, callId=$callId, transport=$transport, content=${message.content}")
        remoteTransport = transport
        _callInfo.value = _callInfo.value.copy(isConnected = true)
        _callState.value = CallState.IN_CALL
        startCallTimer()

        // 提取接听方的端点信息，启动引擎
        extractRemoteEndpointsAndStart(message.content)
    }

    private fun handleCallReject(message: MessageModel) {
        val callId = parseCallId(message.content)
        if (callId != currentCallId) {
            LogUtil.d(TAG, "忽略旧通话的 reject: got=$callId, current=$currentCallId")
            return
        }
        LogUtil.d(TAG, "对方拒接, callId=$callId")
        reset()
    }

    private fun handleCallHangup(message: MessageModel) {
        LogUtil.d(TAG, "对方挂断")
        reset()
    }

    // ========== 对外接口 ==========

    fun invite(targetUserId: Long, targetUsername: String, callType: CallType = CallType.AUDIO) {
        if (_callState.value != CallState.IDLE) {
            LogUtil.w(TAG, "当前不是空闲状态，无法发起通话")
            return
        }
        val callId = UUID.randomUUID().toString().take(8)
        currentCallId = callId
        LogUtil.d(TAG, "发起通话邀请: to=$targetUserId, callId=$callId, type=$callType")
        _callInfo.value = CallInfo(
            targetUserId = targetUserId,
            targetUsername = targetUsername,
            isOutgoing = true,
            callType = callType
        )
        _callState.value = CallState.CALLING

        // 预分配 UDP 端口，把地址嵌入 invite
        val localIp = VoiceEngine.getLocalIpAddress() ?: "0.0.0.0"

        val audioSocket = DatagramSocket(0)
        pendingAudioRecvSocket = audioSocket
        val audioPort = audioSocket.localPort

        val endpoints = if (callType == CallType.VIDEO) {
            val videoSocket = DatagramSocket(0)
            pendingVideoRecvSocket = videoSocket
            val videoPort = videoSocket.localPort
            LogUtil.d(TAG, "预分配 UDP 端口: audio=$audioPort, video=$videoPort, 本机 IP: $localIp")
            MediaEndpoints(localIp, audioPort, localIp, videoPort)
        } else {
            LogUtil.d(TAG, "预分配 UDP 端口: audio=$audioPort, 本机 IP: $localIp")
            MediaEndpoints(localIp, audioPort, null, null)
        }

        sendSignaling(targetUserId, CallPayload(callId, callType, GsonUtil.toJson(endpoints), "udp"), "call-invite")
    }

    fun accept() {
        if (_callState.value != CallState.RINGING) return
        val targetUserId = _callInfo.value.targetUserId
        val callType = _callInfo.value.callType
        LogUtil.d(TAG, "接听来电: from=$targetUserId, callId=$currentCallId, type=$callType, remoteTransport=$remoteTransport")

        if (remoteTransport == "websocket") {
            // Web 端发起的通话，不需要 UDP 端口
            sendSignaling(targetUserId, CallPayload(currentCallId, callType, "", "udp"), "call-accept")

            _callInfo.value = _callInfo.value.copy(isConnected = true)
            _callState.value = CallState.IN_CALL
            startCallTimer()

            // 启动 WebSocket 模式引擎
            startEnginesWebSocket()
        } else {
            // Android 端发起的通话，使用 UDP
            val localIp = VoiceEngine.getLocalIpAddress() ?: "0.0.0.0"

            val audioSocket = DatagramSocket(0)
            pendingAudioRecvSocket = audioSocket
            val audioPort = audioSocket.localPort

            val endpoints = if (callType == CallType.VIDEO) {
                val videoSocket = DatagramSocket(0)
                pendingVideoRecvSocket = videoSocket
                val videoPort = videoSocket.localPort
                LogUtil.d(TAG, "预分配 UDP 端口: audio=$audioPort, video=$videoPort, 本机 IP: $localIp")
                MediaEndpoints(localIp, audioPort, localIp, videoPort)
            } else {
                LogUtil.d(TAG, "预分配 UDP 端口: audio=$audioPort, 本机 IP: $localIp")
                MediaEndpoints(localIp, audioPort, null, null)
            }

            sendSignaling(targetUserId, CallPayload(currentCallId, callType, GsonUtil.toJson(endpoints), "udp"), "call-accept")

            _callInfo.value = _callInfo.value.copy(isConnected = true)
            _callState.value = CallState.IN_CALL
            startCallTimer()

            // 启动引擎（对方的 UDP 地址在收到 invite 时已提取）
            startEnginesIfReady()
        }

        _callInfo.value = _callInfo.value.copy(isConnected = true)
        _callState.value = CallState.IN_CALL
        startCallTimer()

        // 启动引擎（对方的 UDP 地址在收到 invite 时已提取）
        startEnginesIfReady()
    }

    fun reject() {
        if (_callState.value != CallState.RINGING) return
        val targetUserId = _callInfo.value.targetUserId
        LogUtil.d(TAG, "拒接来电: from=$targetUserId, callId=$currentCallId")
        sendSignaling(targetUserId, CallPayload(currentCallId, CallType.AUDIO, ""), "call-reject")
        reset()
    }

    fun hangup() {
        if (_callState.value == CallState.IDLE) return
        val targetUserId = _callInfo.value.targetUserId
        LogUtil.d(TAG, "挂断通话: to=$targetUserId")
        sendSignaling(targetUserId, "", "call-hangup")
        reset()
    }

    fun setMuted(muted: Boolean) {
        voiceEngine?.setMuted(muted)
    }

    /**
     * 设置视频通话的视图组件
     *
     * @param localCameraView 本地预览 CameraView（用于采集摄像头画面）
     * @param remoteSurface 远端显示 SurfaceView（用于显示对方画面）
     * @param lifecycleOwner 生命周期所有者（Activity/Fragment，用于 CameraView 自动管理）
     * @param encodingMode 视频编码模式（默认 H.264）
     */
    fun setVideoSurfaces(
        localCameraView: CameraView?,
        remoteSurface: SurfaceView?,
        lifecycleOwner: LifecycleOwner? = null,
        encodingMode: EncodingMode = EncodingMode.H264
    ) {
        this.localCameraView = localCameraView
        this.remoteSurfaceView = remoteSurface
        this.lifecycleOwner = lifecycleOwner
        this.videoEncodingMode = encodingMode
    }

    // ========== 引擎管理 ==========

    /**
     * 从信令消息的 data 字段提取对方端点信息（仅保存，不启动引擎）
     */
    private fun extractRemoteEndpoints(content: String) {
        try {
            val payload = GsonUtil.fromJson(content, CallPayload::class.java)
            val data = payload.data
            if (data.isNullOrBlank()) {
                LogUtil.w(TAG, "消息中没有端点信息")
                return
            }
            val endpoints = GsonUtil.fromJson(data, MediaEndpoints::class.java)
            LogUtil.d(TAG, "提取到对方端点: audio=${endpoints.audioIp}:${endpoints.audioPort}, video=${endpoints.videoIp}:${endpoints.videoPort}")
            remoteEndpoints = endpoints
        } catch (e: Exception) {
            LogUtil.e(TAG, "解析端点信息失败: ${e.message}")
        }
    }

    /**
     * 从信令消息的 data 字段提取对方端点信息并启动引擎
     */
    private fun extractRemoteEndpointsAndStart(content: String) {
        try {
            val payload = GsonUtil.fromJson(content, CallPayload::class.java)

            if (payload.transport == "websocket") {
                LogUtil.d(TAG, "对方使用 WebSocket 传输模式")
                startEnginesWebSocket()
                return
            }

            val data = payload.data
            if (data.isNullOrBlank()) {
                LogUtil.w(TAG, "call-accept 中没有端点信息")
                return
            }
            val endpoints = GsonUtil.fromJson(data, MediaEndpoints::class.java)
            LogUtil.d(TAG, "对方端点: audio=${endpoints.audioIp}:${endpoints.audioPort}, video=${endpoints.videoIp}:${endpoints.videoPort}")
            startEngines(endpoints)
        } catch (e: Exception) {
            LogUtil.e(TAG, "解析 call-accept 端点信息失败: ${e.message}", e)
        }
    }

    /**
     * 启动引擎（使用预分配的本地 socket + 对方地址）
     */
    private fun startEngines(remote: MediaEndpoints) {
        val context = appContext
        if (context == null) {
            LogUtil.w(TAG, "startEngines: appContext 为空")
            return
        }

        val callType = _callInfo.value.callType

        // 启动音频引擎
        val audioSocket = pendingAudioRecvSocket
        if (audioSocket != null) {
            pendingAudioRecvSocket = null
            LogUtil.d(TAG, "启动音频引擎: target=${remote.audioIp}:${remote.audioPort}, local=${audioSocket.localPort}")
            voiceEngine?.stop()
            val engine = VoiceEngine()
            voiceEngine = engine
            engine.start(remote.audioIp, remote.audioPort, audioSocket, context)
        } else {
            LogUtil.w(TAG, "startEngines: pendingAudioRecvSocket 为空")
        }

        // 启动视频引擎
        if (callType == CallType.VIDEO && remote.videoIp != null && remote.videoPort != null) {
            val videoSocket = pendingVideoRecvSocket
            val cameraView = localCameraView
            val remoteSurface = remoteSurfaceView
            val owner = lifecycleOwner

            if (videoSocket != null && cameraView != null && remoteSurface != null && owner != null) {
                pendingVideoRecvSocket = null
                LogUtil.d(TAG, "启动视频引擎: target=${remote.videoIp}:${remote.videoPort}, " +
                        "local=${videoSocket.localPort}, 编码=$videoEncodingMode")
                videoEngine?.stop()
                val engine = VideoEngine(owner)
                videoEngine = engine
                engine.start(
                    targetIp = remote.videoIp,
                    targetPort = remote.videoPort,
                    recvSocket = videoSocket,
                    cameraView = cameraView,
                    remoteSurface = remoteSurface,
                    mode = videoEncodingMode
                )
            } else {
                LogUtil.w(TAG, "startEngines: 视频资源不完整 socket=$videoSocket, " +
                        "camera=$cameraView, remote=$remoteSurface, owner=$owner")
            }
        }
    }

    /**
     * 启动 WebSocket 模式的引擎
     * 通过 WebSocket 二进制帧收发音视频数据，不使用 UDP
     */
    private fun startEnginesWebSocket() {
        val context = appContext
        if (context == null) {
            LogUtil.w(TAG, "startEnginesWebSocket: appContext 为空")
            return
        }

        val wsSender: (ByteArray) -> Boolean = { data ->
            webSocketManager.sendBinary(data)
        }

        // 启动音频引擎（WebSocket 模式）
        LogUtil.d(TAG, "启动 WebSocket 音频引擎")
        voiceEngine?.stop()
        val voice = VoiceEngine()
        voiceEngine = voice
        voice.startWebSocket(wsSender, context)

        // 启动视频引擎（WebSocket 模式，仅视频通话时）
        val callType = _callInfo.value.callType
        if (callType == CallType.VIDEO) {
            val cameraView = localCameraView
            val owner = lifecycleOwner
            if (cameraView != null && owner != null) {
                LogUtil.d(TAG, "启动 WebSocket 视频引擎, 编码=$videoEncodingMode")
                videoEngine?.stop()
                val video = VideoEngine(owner)
                videoEngine = video
                video.startWebSocket(
                    wsSender = wsSender,
                    cameraView = cameraView,
                    remoteSurface = remoteSurfaceView,
                    mode = videoEncodingMode
                )
            } else {
                LogUtil.w(TAG, "startEnginesWebSocket: 视频资源不完整 camera=$cameraView, owner=$owner")
            }
        }
    }

    /**
     * 处理通过 WebSocket 收到的二进制媒体帧
     * 由 signalingListener.onBinaryReceived 调用
     *
     * @param data 二进制帧数据（含类型字节前缀）
     */
    private fun handleBinaryMediaFrame(data: ByteArray) {
        if (data.isEmpty()) return
        if (_callState.value != CallState.IN_CALL) return

        val type = data[0]
        val payload = data.copyOfRange(1, data.size)

        when (type) {
            0x01.toByte() -> {
                // 视频帧
                videoEngine?.feedVideoFrame(payload)
            }
            0x02.toByte() -> {
                // 音频帧
                voiceEngine?.feedPcmPacket(payload)
            }
            else -> {
                LogUtil.w(TAG, "未知的二进制帧类型: $type")
            }
        }
    }

    /**
     * 运行时切换视频编码模式（JPEG ↔ H.264）
     *
     * 【使用场景】
     * - 网络变差时切换到 JPEG（更鲁棒）
     * - 网络恢复时切换到 H.264（更高效）
     */
    fun switchVideoEncodingMode(mode: EncodingMode) {
        videoEncodingMode = mode
        videoEngine?.switchEncodingMode(mode)
        LogUtil.d(TAG, "切换视频编码模式: $mode")
    }

    /**
     * 切换前置/后置摄像头
     */
    fun switchCamera() {
        videoEngine?.switchCamera()
    }

    /**
     * 启动引擎（使用之前提取的远程端点 + 预分配的本地 socket）
     */
    private fun startEnginesIfReady() {
        val endpoints = remoteEndpoints
        if (endpoints == null) {
            LogUtil.w(TAG, "startEnginesIfReady: 远程端点为空")
            return
        }
        remoteEndpoints = null
        startEngines(endpoints)
    }

    // 暂存的对方端点（invite 方在 RINGING 阶段提取，accept 时使用）
    private var remoteEndpoints: MediaEndpoints? = null

    // ========== 辅助方法 ==========

    private fun parseCallId(content: String): String {
        return try {
            val payload = GsonUtil.fromJson(content, CallPayload::class.java)
            payload.callId
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseCallType(content: String): CallType {
        return try {
            val payload = GsonUtil.fromJson(content, CallPayload::class.java)
            payload.callType
        } catch (e: Exception) {
            CallType.AUDIO
        }
    }

    private fun parseTransport(content: String): String {
        return try {
            val payload = GsonUtil.fromJson(content, CallPayload::class.java)
            payload.transport
        } catch (e: Exception) {
            "udp"
        }
    }

    private fun sendSignaling(toUserId: Long, payload: CallPayload, type: String) {
        val myId = userPreferences.getUserId()
        val json = GsonUtil.toJson(payload)
        LogUtil.d(TAG, "sendSignaling: type=$type, fromId=$myId, toId=$toUserId, content=$json")
        val message = MessageModel(
            fromId = myId,
            toId = toUserId,
            content = json,
            type = type
        )
        webSocketManager.sendMessage(message)
    }

    private fun sendSignaling(toUserId: Long, content: String, type: String) {
        val message = MessageModel(
            fromId = userPreferences.getUserId(),
            toId = toUserId,
            content = content,
            type = type
        )
        webSocketManager.sendMessage(message)
    }

    private fun startCallTimer() {
        _callDuration.value = 0L
        val startTime = System.currentTimeMillis()
        _callInfo.value = _callInfo.value.copy(startTime = startTime)
        callTimerJob?.cancel()
        callTimerJob = scope.launch {
            while (isActive) {
                _callDuration.value = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }
    }

    private fun reset() {
        callTimerJob?.cancel()
        callTimerJob = null
        // 保存通话信息供 UI 插入通话提示消息
        _lastCallDuration = _callDuration.value
        _lastCallType = _callInfo.value.callType
        _lastIsOutgoing = _callInfo.value.isOutgoing
        _callDuration.value = 0L
        voiceEngine?.stop()
        voiceEngine = null
        videoEngine?.stop()
        videoEngine = null
        pendingAudioRecvSocket?.close()
        pendingAudioRecvSocket = null
        pendingVideoRecvSocket?.close()
        pendingVideoRecvSocket = null
        remoteEndpoints = null
        remoteTransport = "udp"
        currentCallId = ""
        _callState.value = CallState.IDLE
        _callInfo.value = CallInfo()
    }

    fun release() {
        reset()
        webSocketManager.removeListener(signalingListener)
        scope.cancel()
    }

    companion object {
        private const val TAG = "CallManager"
    }
}

/**
 * 信令消息载荷
 * data 字段：call-invite/call-accept 时携带 MediaEndpoints JSON
 * transport 字段：传输模式 "udp" 或 "websocket"
 */
private data class CallPayload(
    val callId: String = "",
    val callType: CallType = CallType.AUDIO,
    val data: String? = null,
    val transport: String = "udp"
)

/**
 * 媒体端点信息（音频 + 视频）
 */
private data class MediaEndpoints(
    val audioIp: String,
    val audioPort: Int,
    val videoIp: String? = null,
    val videoPort: Int? = null
)
