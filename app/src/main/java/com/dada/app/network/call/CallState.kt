package com.dada.app.network.call

/**
 * 通话状态枚举
 */
enum class CallState {
    IDLE,       // 空闲
    CALLING,    // 去电中（等待对方接听）
    RINGING,    // 来电中（等待本机接听）
    IN_CALL     // 通话中
}

/**
 * 通话类型
 */
enum class CallType {
    AUDIO,      // 语音通话
    VIDEO       // 视频通话
}

/**
 * 通话信息
 */
data class CallInfo(
    val targetUserId: Long = 0L,
    val targetUsername: String = "",
    val startTime: Long = 0L,
    val isOutgoing: Boolean = false,
    val isConnected: Boolean = false,
    val callType: CallType = CallType.AUDIO
)
