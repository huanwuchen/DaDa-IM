package com.dada.core.network.websocket

import com.dada.core.common.utils.LogUtil
import com.dada.core.common.utils.GsonUtil
import kotlinx.coroutines.*

/**
 * 心跳管理器
 * 每 15 秒发送一次 ping 消息，保持连接活跃
 */
class HeartBeatManager(
    private val sendMessage: (String) -> Boolean
) {
    private var heartBeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 启动心跳
     */
    fun start() {
        stop() // 先停止之前的心跳

        heartBeatJob = scope.launch {
            while (isActive) {
                delay(HEART_BEAT_INTERVAL)

                val pingMessage = PingMessage()
                val json = GsonUtil.toJson(pingMessage)

                val success = sendMessage(json)
                if (success) {
                    LogUtil.d(TAG, "心跳发送成功")
                } else {
                    LogUtil.e(TAG, "心跳发送失败")
                }
            }
        }
    }

    /**
     * 停止心跳
     */
    fun stop() {
        heartBeatJob?.cancel()
        heartBeatJob = null
        LogUtil.d(TAG, "心跳已停止")
    }

    /**
     * 释放资源
     */
    fun release() {
        stop()
        scope.cancel()
    }

    companion object {
        private const val TAG = "HeartBeatManager"
        private const val HEART_BEAT_INTERVAL = 15_000L // 15 秒
    }
}
