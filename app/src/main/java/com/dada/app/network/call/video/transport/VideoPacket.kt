package com.dada.app.network.call.video.transport

import com.dada.app.network.call.video.EncodingMode
import com.dada.app.network.call.video.VideoEngineConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * UDP 视频数据包结构
 *
 * 【数据包格式（13 字节包头 + 数据）】
 * ┌─────────┬─────────┬─────────┬──────────┬──────────┬─────────┐
 * │ 编码类型 │ 序号    │ 时间戳   │ 总分片数  │ 分片索引  │ 数据    │
 * │  1 字节  │ 4 字节  │ 4 字节  │  2 字节  │  2 字节  │  ...    │
 * └─────────┴─────────┴─────────┴──────────┴──────────┴─────────┘
 *
 * @property mode 编码类型（JPEG/H.264）
 * @property seq 帧序号（同一帧的所有分片序号相同）
 * @property timestamp 时间戳（毫秒）
 * @property totalChunks 总分片数
 * @property chunkIndex 当前分片索引（从0开始）
 * @property data 数据片段
 */
data class VideoPacket(
    val mode: EncodingMode,
    val seq: Int,
    val timestamp: Int,
    val totalChunks: Int,
    val chunkIndex: Int,
    val data: ByteArray,
    val dataOffset: Int = 0,
    val dataLength: Int = data.size
) {

    /**
     * 序列化为字节数组（用于发送）
     */
    fun toBytes(): ByteArray {
        val packet = ByteArray(VideoEngineConfig.HEADER_SIZE + dataLength)
        ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN).apply {
            put(mode.code)                      // 编码类型（1字节）
            putInt(seq)                         // 序号（4字节）
            putInt(timestamp)                   // 时间戳（4字节）
            putShort(totalChunks.toShort())     // 总分片数（2字节）
            putShort(chunkIndex.toShort())      // 当前分片索引（2字节）
            put(data, dataOffset, dataLength)   // 数据片段
        }
        return packet
    }

    companion object {
        /**
         * 从字节数组反序列化（用于接收）
         *
         * @param buffer 接收缓冲区
         * @param length 实际接收到的字节数
         * @return 解析出的 VideoPacket，如果包太小则返回 null
         */
        fun fromBytes(buffer: ByteArray, length: Int): VideoPacket? {
            if (length < VideoEngineConfig.HEADER_SIZE) return null

            val header = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN)
            val modeCode = header.get()                              // 编码类型
            val seq = header.int                                     // 序号
            val timestamp = header.int                               // 时间戳
            val totalChunks = header.short.toInt() and 0xFFFF        // 总分片数（无符号）
            val chunkIndex = header.short.toInt() and 0xFFFF         // 分片索引（无符号）

            val dataSize = length - VideoEngineConfig.HEADER_SIZE
            val data = ByteArray(dataSize)
            System.arraycopy(buffer, VideoEngineConfig.HEADER_SIZE, data, 0, dataSize)

            return VideoPacket(
                mode = EncodingMode.Companion.fromCode(modeCode),
                seq = seq,
                timestamp = timestamp,
                totalChunks = totalChunks,
                chunkIndex = chunkIndex,
                data = data
            )
        }
    }

    // ===== equals/hashCode（因为有 ByteArray 字段，需要自定义） =====
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VideoPacket) return false
        return mode == other.mode && seq == other.seq && chunkIndex == other.chunkIndex
    }

    override fun hashCode(): Int {
        var result = mode.hashCode()
        result = 31 * result + seq
        result = 31 * result + chunkIndex
        return result
    }
}
