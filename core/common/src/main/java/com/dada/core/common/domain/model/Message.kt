package com.dada.core.common.domain.model

import java.util.UUID

/**
 * 消息领域模型
 *
 * 纯 Kotlin 数据类，不依赖 Room / Android。
 * 由 Repository 层从 Entity 转换而来，ViewModel 和 UI 层使用。
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val fromId: Long,
    val toId: Long,
    val content: String,
    val type: String = TYPE_TEXT,
    val time: Long = System.currentTimeMillis(),
    val thumbUrl: String? = null,
    val duration: Long = 0,
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val fileName: String? = null,
    val avatar: String? = null,
    val iconRes: Int = 0,
) {
    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
        const val TYPE_AUDIO = "audio"
        const val TYPE_FILE = "file"
        const val TYPE_CALL_HINT = "call_hint"
    }
}
