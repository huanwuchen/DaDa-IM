package com.dada.app.network.call.video.transport

import com.dada.core.common.utils.LogUtil
import com.dada.app.network.call.video.EncodingMode
import com.dada.app.network.call.video.VideoEngineConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * UDP 数据包接收重组器
 *
 * 【职责】
 * 1. 接收 UDP 分片
 * 2. 按序号和分片索引组织
 * 3. 收齐后重组为完整帧
 * 4. 通过回调通知完整帧
 *
 * 【关键设计】
 * - 丢弃过期帧（seq <= lastRenderedSeq）：避免显示旧画面
 * - 超时清理：500ms 未收齐自动丢弃
 *
 * @param recvSocket 接收 UDP 数据的 Socket
 * @param onFrameReceived 完整帧接收完成的回调（编码模式, 帧数据）
 */
class PacketReceiver(
    private val recvSocket: DatagramSocket,
    private val onFrameReceived: (EncodingMode, ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "PacketReceiver"
    }

    // 已接收的完整帧数
    private val recvCount = AtomicInteger(0)

    // 分片缓冲区：序号 → (分片索引 → 数据)
    private val frameBuffer = ConcurrentHashMap<Int, ConcurrentHashMap<Int, ByteArray>>()

    // 帧信息：序号 → (总分片数, 接收时间)
    private val frameInfo = ConcurrentHashMap<Int, Pair<Int, Long>>()

    // 帧的编码类型映射：序号 → 编码模式
    private val frameModeMap = ConcurrentHashMap<Int, EncodingMode>()

    // 最后渲染的帧序号（用于丢弃过期帧）
    private var lastRenderedSeq = 0

    // 接收缓冲区
    private val buffer = ByteArray(VideoEngineConfig.MAX_PACKET_SIZE)

    /**
     * 接收一个 UDP 数据包并处理
     *
     * @return 如果收齐了一帧并触发了回调，返回 true
     */
    fun receiveOne(): Boolean {
        try {
            // ========== 步骤1：接收 UDP 包 ==========
            val datagram = DatagramPacket(buffer, buffer.size)
            recvSocket.receive(datagram)  // 阻塞等待

            // ========== 步骤2：解析数据包 ==========
            val packet = VideoPacket.fromBytes(buffer, datagram.length) ?: return false

            // ========== 步骤3：丢弃过期帧 ==========
            if (packet.seq <= lastRenderedSeq) return false

            // ========== 步骤4：记录编码类型 ==========
            frameModeMap[packet.seq] = packet.mode

            // ========== 步骤5：存储分片 ==========
            frameBuffer.getOrPut(packet.seq) { ConcurrentHashMap() }[packet.chunkIndex] = packet.data
            frameInfo[packet.seq] = Pair(packet.totalChunks, System.currentTimeMillis())

            // ========== 步骤6：检查是否收齐 ==========
            val chunks = frameBuffer[packet.seq]
            if (chunks != null && chunks.size == packet.totalChunks) {
                // 重组完整帧
                val completeFrame = reassembleFrame(chunks, packet.totalChunks)

                // 触发回调
                onFrameReceived(packet.mode, completeFrame)

                // 更新状态
                lastRenderedSeq = packet.seq
                val count = recvCount.incrementAndGet()

                // 日志输出
                if (count <= 3 || count % 30 == 0) {
                    LogUtil.d(TAG, "接收 #$count [${packet.mode}] ← ${datagram.address?.hostAddress}:" +
                            "${datagram.port}, seq=${packet.seq}, ${completeFrame.size}字节")
                }

                // 清理已渲染的帧
                cleanFrame(packet.seq)
                return true
            }

            // ========== 步骤7：清理过期的不完整帧 ==========
            cleanupExpiredFrames()

        } catch (e: Exception) {
            // Socket 关闭等异常向上抛出
            throw e
        }

        return false
    }

    /**
     * 重组完整帧
     */
    private fun reassembleFrame(
        chunks: ConcurrentHashMap<Int, ByteArray>,
        totalChunks: Int
    ): ByteArray {
        // 计算总大小
        var frameSize = 0
        for (i in 0 until totalChunks) {
            frameSize += chunks[i]?.size ?: 0
        }

        // 拼接所有分片
        val completeFrame = ByteArray(frameSize)
        var offset = 0
        for (i in 0 until totalChunks) {
            val chunk = chunks[i]
            if (chunk != null) {
                System.arraycopy(chunk, 0, completeFrame, offset, chunk.size)
                offset += chunk.size
            }
        }
        return completeFrame
    }

    /**
     * 清理一个已渲染的帧
     */
    private fun cleanFrame(seq: Int) {
        frameBuffer.remove(seq)
        frameInfo.remove(seq)
        frameModeMap.remove(seq)
    }

    /**
     * 清理超时未收齐的帧（避免内存泄漏）
     */
    private fun cleanupExpiredFrames() {
        val now = System.currentTimeMillis()
        frameInfo.entries.removeIf { (s, info) ->
            if (now - info.second > VideoEngineConfig.FRAME_TIMEOUT_MS) {
                frameBuffer.remove(s)
                frameModeMap.remove(s)
                true
            } else false
        }
    }

    /**
     * 获取已接收的帧数
     */
    fun getRecvCount(): Int = recvCount.get()

    /**
     * 重置接收状态
     */
    fun reset() {
        recvCount.set(0)
        lastRenderedSeq = 0
        frameBuffer.clear()
        frameInfo.clear()
        frameModeMap.clear()
    }
}
