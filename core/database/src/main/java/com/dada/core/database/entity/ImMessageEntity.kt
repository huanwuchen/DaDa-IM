package com.dada.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * IM 单条消息 Entity
 *
 * 设计要点：
 *  - [conversationId] 等于「对方的 userId」（peerId），用作会话归属；
 *    插入消息时由 Repository 根据当前登录用户和发送/接收方计算
 *  - [id]（UUID 或服务端消息 id）作为唯一标识，避免重复插入
 *  - [seq] 由 Room 自增，作为二级排序键，保证同一毫秒多条消息也能按入库顺序展示，
 *    避免两端时钟不同步导致的消息错乱
 *  - 用 [Index] 加速按会话 + (timestamp, seq) 排序的查询
 *
 * 媒体字段（[type] ≠ text 时使用）：
 *  - content：媒体 URL（image/video/voice/file 都用这个字段）
 *  - thumbUrl：图片/视频缩略图（可选）
 *  - duration：语音/视频时长（毫秒）
 *  - size：文件字节数
 *  - width/height：图片/视频尺寸
 *  - fileName：原始文件名（file 类型展示）
 */
@Entity(
    tableName = "im_messages",
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["conversationId", "timestamp", "seq"]),
    ]
)
data class ImMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val seq: Long = 0,
    val id: String,
    val conversationId: Long,
    val fromId: Long,
    val toId: Long,
    val content: String,
    val type: String,
    val timestamp: Long,
    val isMine: Boolean,

    // ============================== 媒体扩展字段 ==============================
    val thumbUrl: String? = null,
    val duration: Long = 0,
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val fileName: String? = null,
    val avatar: String? = null,
    val iconRes: Int = 0,
)
