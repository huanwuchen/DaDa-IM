package com.dada.core.network.websocket

import com.dada.core.common.utils.GsonUtil
import com.dada.core.common.utils.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

/**
 * 可靠消息管理器。
 *
 * 职责：
 *  - 发送：每条消息维护 pending，等待对端 ack；超时按 [retryPolicy] 重发
 *  - 接收：去重 → 落库 → 回 ack → 分发给业务
 *  - 心跳：周期 ping/pong，超时触发 [WebSocketManager.forceReconnect]
 *  - 状态：融合心跳裁决，对外暴露 [effectiveState]
 *
 * 线程模型：所有内部状态变更都在 [scope] 协程内串行执行，状态表用 ConcurrentHashMap。
 */
class MessageManager(
    private val conn: WebSocketManager,
    private val store: MessageStore = MessageStore.Noop,
    private val retryPolicy: RetryPolicy = RetryPolicy(maxAttempts = 5),
    private val ackTimeoutMs: Long = 8_000,
    private val heartbeatIntervalMs: Long = 25_000,
    private val heartbeatTimeoutMs: Long = 8_000,
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private data class Pending(
        val message: MessageModel,
        var attempt: Int = 0,
        var job: Job? = null,
    )

    private val pending = ConcurrentHashMap<String, Pending>()
    private val ackManager = AckManager()
    private val dedup = LruDedup(capacity = 2048)

    /** 收到的业务消息（已去重、已 ack、已落库）。 */
    private val _inbound = MutableSharedFlow<MessageModel>(extraBufferCapacity = 256)
    val inbound: SharedFlow<MessageModel> = _inbound.asSharedFlow()

    /** 业务可见的有效连接状态（心跳异常会进入 HalfOpen）。 */
    private val _effectiveState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val effectiveState: StateFlow<ConnectionState> = _effectiveState.asStateFlow()

    private var heartbeatJob: Job? = null
    private val heartbeatAckChannel = Channel<String>(Channel.CONFLATED)

    @Volatile private var started = false

    /**
     * 启动。会订阅底层状态、收消息流，并接管心跳。
     * 调用前请确保 [WebSocketManager.legacyHeartbeatEnabled] = false。
     */
    fun start() {
        if (started) return
        started = true
        conn.legacyHeartbeatEnabled = false

        // 1) 透传底层状态 → 有效状态
        scope.launch {
            conn.richState.collect { s ->
                _effectiveState.value = s
                when (s) {
                    is ConnectionState.Connected -> {
                        startHeartbeat()
                        recoverUnackedFromStore()
                        // 已有 pending 的协程会自己 await Connected 后继续发送
                    }
                    is ConnectionState.Reconnecting,
                    is ConnectionState.Dead,
                    ConnectionState.HalfOpen,
                    ConnectionState.Connecting,
                    ConnectionState.Idle -> stopHeartbeat()
                }
            }
        }

        // 2) 入站文本
        scope.launch {
            conn.incomingRaw.collect { text -> handleInbound(text) }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        stopHeartbeat()
        ackManager.cancelAll(CancellationException("MessageManager stopped"))
        pending.values.forEach { it.job?.cancel() }
        pending.clear()
        scope.cancel()
    }

    // ───────────────────────── 发送路径 ─────────────────────────

    /**
     * 发送消息。返回的 Deferred：
     *  - await() 成功 = 收到对端 ack
     *  - await() 抛异常 = 超出重试次数 / 取消
     */
    fun send(message: MessageModel): Deferred<Unit> {
        // 先落库（status=SENDING），保证崩溃恢复
        scope.launch { store.insertOutgoing(message) }

        val ackDeferred = ackManager.register(message.id)
        val entry = Pending(message)
        pending[message.id] = entry
        entry.job = scope.launch { attemptSendLoop(entry) }
        return scope.async { ackDeferred.await() }
    }

    private suspend fun attemptSendLoop(entry: Pending) {
        val json = GsonUtil.toJson(entry.message)
        while (scope.isActive) {
            // 等待连接就绪（重连期间挂起）
            conn.richState.first { it is ConnectionState.Connected }

            val wrote = conn.sendRaw(json)
            if (!wrote) {
                delay(500)
                continue
            }

            val ackDeferred = ackManager.register(entry.message.id)
            val acked = withTimeoutOrNull(ackTimeoutMs) {
                runCatching { ackDeferred.await() }.isSuccess
            } ?: false

            if (acked) {
                pending.remove(entry.message.id)
                store.markSent(entry.message.id)
                return
            }

            entry.attempt++
            if (retryPolicy.shouldGiveUp(entry.attempt)) {
                LogUtil.e(TAG, "消息超出重试次数: ${entry.message.id}")
                pending.remove(entry.message.id)
                ackManager.cancel(entry.message.id, TimeoutException("max retries reached"))
                store.markFailed(entry.message.id)
                return
            }
            val backoff = retryPolicy.nextDelay(entry.attempt)
            LogUtil.w(TAG, "ack 超时，${backoff}ms 后重发 (#${entry.attempt}): ${entry.message.id}")
            delay(backoff)
        }
    }

    private fun recoverUnackedFromStore() {
        scope.launch {
            val unacked = runCatching { store.loadUnacked() }.getOrDefault(emptyList())
            for (msg in unacked) {
                if (pending.containsKey(msg.id)) continue
                val entry = Pending(msg)
                pending[msg.id] = entry
                entry.job = scope.launch { attemptSendLoop(entry) }
            }
        }
    }

    // ───────────────────────── 接收路径 ─────────────────────────

    private fun handleInbound(text: String) {
        val frame = runCatching { GsonUtil.fromJson(text, Frame::class.java) }
            .getOrNull()
            ?: run {
                // 兼容旧服务端：尝试按 MessageModel 解析
                runCatching { GsonUtil.fromJson(text, MessageModel::class.java) }
                    .getOrNull()
                    ?.let { handleBusinessMessage(it) }
                return
            }

        when {
            frame.isAck -> {
                val id = frame.ackId ?: return
                ackManager.complete(id)
                scope.launch { store.markSent(id) }
            }
            frame.isHeartbeatAck -> {
                val id = frame.ackId ?: return
                heartbeatAckChannel.trySend(id)
            }
            frame.isHeartbeat -> {
                // 服务端发心跳，回 ack
                conn.sendRaw(GsonUtil.toJson(Frame.heartbeatAck(frame.id)))
            }
            else -> {
                // 业务消息可能放在 payload 或外层（看服务端实现）
                val msg = frame.payload
                    ?: runCatching { GsonUtil.fromJson(text, MessageModel::class.java) }.getOrNull()
                if (msg != null) handleBusinessMessage(msg)
            }
        }
    }

    private fun handleBusinessMessage(message: MessageModel) {
        // Gson 通过 Unsafe 构造对象会绕过 Kotlin non-null 检查，必须在边界防御
        @Suppress("USELESS_ELVIS")
        val id: String = message.id ?: run {
            LogUtil.w(TAG, "丢弃无 id 业务消息: from=${message.fromId} type=${message.type}")
            return
        }
        if (!dedup.putIfAbsent(id)) {
            LogUtil.d(TAG, "丢弃重复消息: $id")
            return
        }
        scope.launch {
            val isNew = runCatching { store.insertIncoming(message) }.getOrDefault(true)
            // 无论新旧都回 ack（让服务端停止重发）
            conn.sendRaw(GsonUtil.toJson(Frame.ack(id)))
            if (isNew) _inbound.tryEmit(message)
        }
    }

    // ───────────────────────── 心跳 ─────────────────────────

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            // 清空残留 ack
            while (heartbeatAckChannel.tryReceive().isSuccess) {
                /* drain */
            }

            while (isActive && conn.richState.value is ConnectionState.Connected) {
                delay(heartbeatIntervalMs)
                val ping = Frame.heartbeat()
                val sent = conn.sendRaw(GsonUtil.toJson(ping))
                if (!sent) continue

                _effectiveState.value = ConnectionState.HalfOpen
                val ok = withTimeoutOrNull(heartbeatTimeoutMs) {
                    // 等待匹配本次 pingId 的 ack（其他 id 跳过）
                    var hit = false
                    while (isActive && !hit) {
                        if (heartbeatAckChannel.receive() == ping.id) hit = true
                    }
                    hit
                } == true

                if (ok) {
                    _effectiveState.value = ConnectionState.Connected
                } else {
                    LogUtil.w(TAG, "心跳超时，触发 forceReconnect")
                    conn.forceReconnect(IOException("heartbeat timeout").message ?: "heartbeat timeout")
                    break
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    companion object {
        private const val TAG = "MessageManager"
    }
}
