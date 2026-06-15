package com.dada.app.network.call.video.transport

import com.dada.core.common.utils.LogUtil
import com.dada.app.network.call.video.EncodingMode
import com.dada.app.network.call.video.VideoEngineConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * UDP 数据包发送器
 *
 * 【职责】
 * 1. 将编码后的视频帧分片
 * 2. 为每个分片构造包头
 * 3. 通过 UDP 发送
 *
 * 【为什么需要分片】
 * - UDP 单包最大 1500 字节（MTU 限制）
 * - 视频帧通常远大于这个值
 * - 需要分成多个小包发送
 */
class PacketSender(
    private val sendSocket: DatagramSocket,
    private val targetAddress: InetAddress,
    private val targetPort: Int
) {
    companion object {
        private const val TAG = "PacketSender"
    }

    // 已发送的帧数（递增的序号）
    private val sentCount = AtomicInteger(0)

    /**
     * 发送一帧编码后的视频数据
     *
     * 【执行流程】
     * 1. 计算分片数量
     * 2. 为每个分片构造数据包
     * 3. 通过 UDP 发送
     *
     * @param frameData 编码后的帧数据（JPEG 或 H.264）
     * @param mode 编码模式
     * @return 发送的序号
     */
    fun sendFrame(frameData: ByteArray, mode: EncodingMode): Int {
        // 计算分片数量
        val totalChunks = (frameData.size + VideoEngineConfig.PAYLOAD_SIZE - 1) / VideoEngineConfig.PAYLOAD_SIZE
        val seq = sentCount.incrementAndGet()
        val timestamp = System.currentTimeMillis().toInt()

        // 分片发送
        for (chunkIndex in 0 until totalChunks) {
            val offset = chunkIndex * VideoEngineConfig.PAYLOAD_SIZE
            val length = minOf(VideoEngineConfig.PAYLOAD_SIZE, frameData.size - offset)

            // 构造数据包
            val videoPacket = VideoPacket(
                mode = mode,
                seq = seq,
                timestamp = timestamp,
                totalChunks = totalChunks,
                chunkIndex = chunkIndex,
                data = frameData,
                dataOffset = offset,
                dataLength = length
            )

            try {
                val bytes = videoPacket.toBytes()
                val datagram = DatagramPacket(bytes, bytes.size, targetAddress, targetPort)
                sendSocket.send(datagram)

                // 节流：每 32 个分片让出 CPU 让接收端有机会处理
                // 1080P 单帧可能有几百个分片，瞬间打满 socket 缓冲区会导致大量丢包
                if (chunkIndex > 0 && chunkIndex % 32 == 0) {
                    Thread.sleep(1)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return seq
            } catch (e: Exception) {
                LogUtil.e(TAG, "发送分片失败: seq=$seq, chunk=$chunkIndex, ${e.message}")
                return seq
            }
        }

        // 日志输出（避免刷屏）
        if (seq <= 3 || seq % 30 == 0) {
            LogUtil.d(TAG, "发送 #$seq [$mode] → ${targetAddress.hostAddress}:$targetPort, " +
                    "${frameData.size}字节, ${totalChunks}个分片")
        }

        return seq
    }

    /**
     * 获取已发送的帧数
     */
    fun getSentCount(): Int = sentCount.get()

    /**
     * 重置发送计数
     */
    fun reset() {
        sentCount.set(0)
    }
}
