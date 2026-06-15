package com.dada.app.network.call.video

import com.dada.core.common.utils.LogUtil
import android.view.SurfaceView
import androidx.lifecycle.LifecycleOwner
import com.dada.app.network.call.video.capture.CameraCapture
import com.dada.app.network.call.video.codec.VideoCodec
import com.dada.app.network.call.video.codec.VideoCodecFactory
import com.dada.app.network.call.video.transport.FrameQueue
import com.dada.app.network.call.video.transport.PacketReceiver
import com.dada.app.network.call.video.transport.PacketSender
import com.dada.app.network.call.video.transport.VideoPacket
import com.dada.app.network.call.video.transport.YuvFrame
import com.otaliastudios.cameraview.CameraView
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * 视频通话引擎 - 基于 UDP 的实时视频传输
 *
 * 【架构概览】
 * 这是一个编排者类，负责协调以下组件：
 *
 *  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
 *  │ CameraCapture│ →  │  YUV Queue   │ →  │ Encode Loop  │
 *  │  (采集 YUV)  │    │  (生产消费)   │    │  (压缩编码)  │
 *  └──────────────┘    └──────────────┘    └──────┬───────┘
 *                                                  ↓
 *  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
 *  │ PacketSender │ ←  │ Encoded Queue│ ←  │  VideoCodec  │
 *  │  (分片发送)  │    │  (生产消费)   │    │ (JPEG/H.264) │
 *  └──────────────┘    └──────────────┘    └──────────────┘
 *
 *  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
 *  │PacketReceiver│ →  │  VideoCodec  │ →  │ SurfaceView  │
 *  │  (接收重组)  │    │ (解码渲染)   │    │  (显示画面)  │
 *  └──────────────┘    └──────────────┘    └──────────────┘
 *
 * 【拆分后的模块】
 * - VideoEngineConfig: 配置常量
 * - EncodingMode: 编码模式枚举
 * - capture/CameraCapture: 摄像头采集
 * - codec/VideoCodec: 编解码器接口
 * - codec/JpegCodec: JPEG 实现
 * - codec/H264Codec: H.264 实现
 * - transport/VideoPacket: 数据包结构
 * - transport/PacketSender: 分片发送
 * - transport/PacketReceiver: 接收重组
 * - transport/FrameQueue: 帧队列
 *
 * 【使用方法】
 * val engine = VideoEngine(lifecycleOwner)
 * engine.start(targetIp, targetPort, recvSocket, cameraView, remoteSurface)
 * engine.switchEncodingMode(EncodingMode.JPEG)  // 运行时切换
 * engine.stop()
 */
class VideoEngine(private val lifecycleOwner: LifecycleOwner) {

    companion object {
        private const val TAG = "VideoEngine"
    }

    // ==================== 状态 ====================
    @Volatile private var running = false
    @Volatile private var encodingMode = EncodingMode.H264

    // ==================== 组件 ====================
    private var capture: CameraCapture? = null
    private var codec: VideoCodec? = null
    private var sender: PacketSender? = null
    private var receiver: PacketReceiver? = null
    private var remoteSurface: SurfaceView? = null

    // 网络
    private var sendSocket: DatagramSocket? = null
    private var recvSocket: DatagramSocket? = null

    // WebSocket 模式
    private var wsMode = false
    private var wsSender: ((ByteArray) -> Boolean)? = null
    private var wsFrameSeq = AtomicInteger(0)

    // 数据队列
    // YUV 队列存 YuvFrame（数据 + 真实尺寸）
    private val yuvQueue = FrameQueue<YuvFrame>(VideoEngineConfig.MAX_QUEUE_SIZE)
    // 编码后的队列存原始字节数组
    private val encodedQueue = FrameQueue<ByteArray>(VideoEngineConfig.MAX_QUEUE_SIZE)

    // 工作线程
    private var encodeThread: Thread? = null
    private var sendThread: Thread? = null
    private var recvThread: Thread? = null

    // ==================== 公共接口 ====================

    /**
     * 启动视频引擎
     *
     * @param targetIp 对方 IP
     * @param targetPort 对方端口
     * @param recvSocket 接收 Socket
     * @param cameraView 本地摄像头预览
     * @param remoteSurface 远端显示 View
     * @param mode 编码模式（默认 H.264）
     */
    fun start(
        targetIp: String,
        targetPort: Int,
        recvSocket: DatagramSocket,
        cameraView: CameraView,
        remoteSurface: SurfaceView,
        mode: EncodingMode = EncodingMode.H264
    ) {
        if (running) return
        LogUtil.d(TAG, ">>> 启动: $targetIp:$targetPort, 编码=$mode")

        this.encodingMode = mode
        this.recvSocket = recvSocket
        this.remoteSurface = remoteSurface
        val socket = DatagramSocket()
        this.sendSocket = socket

        // 调大 socket 缓冲区（1080P 数据量大，默认缓冲区会爆）
        // 2MB 足够容纳几帧 1080P 的数据
        try {
            socket.sendBufferSize = 2 * 1024 * 1024
            recvSocket.receiveBufferSize = 2 * 1024 * 1024
            LogUtil.d(TAG, "Socket 缓冲区: send=${socket.sendBufferSize}, " +
                    "recv=${recvSocket.receiveBufferSize}")
        } catch (e: Exception) {
            LogUtil.w(TAG, "调整 socket 缓冲区失败: ${e.message}")
        }

        // 初始化各组件
        codec = VideoCodecFactory.create(mode, remoteSurface)
        capture = CameraCapture(cameraView, lifecycleOwner) { yuv, w, h ->
            // 采集到的 YUV + 真实尺寸放入队列
            yuvQueue.offer(YuvFrame(yuv, w, h))
        }
        sender = PacketSender(socket, InetAddress.getByName(targetIp), targetPort)
        receiver = PacketReceiver(recvSocket) { frameMode, frameData ->
            handleReceivedFrame(frameMode, frameData)
        }

        // 启动
        running = true
        capture?.start()
        encodeThread = thread(name = "video-encode", isDaemon = true) { encodeLoop() }
        sendThread = thread(name = "video-send", isDaemon = true) { sendLoop() }
        recvThread = thread(name = "video-recv", isDaemon = true) { recvLoop() }

        LogUtil.d(TAG, "<<< 启动成功")
    }

    /**
     * 启动 WebSocket 模式的视频引擎
     * 不使用 UDP，通过 WebSocket 二进制帧收发视频数据
     *
     * @param wsSender WebSocket 发送函数，接收封装后的二进制帧
     * @param cameraView 本地摄像头预览
     * @param remoteSurface 远端显示 View（WebSocket 模式下可为 null，用 feedVideoFrame 直接渲染）
     * @param mode 编码模式
     */
    fun startWebSocket(
        wsSender: (ByteArray) -> Boolean,
        cameraView: CameraView,
        remoteSurface: SurfaceView?,
        mode: EncodingMode = EncodingMode.H264
    ) {
        if (running) return
        LogUtil.d(TAG, ">>> 启动 WebSocket 模式: 编码=$mode")

        this.encodingMode = mode
        this.remoteSurface = remoteSurface
        this.wsMode = true
        this.wsSender = wsSender
        this.wsFrameSeq = AtomicInteger(0)

        codec = if (remoteSurface != null) {
            VideoCodecFactory.create(mode, remoteSurface)
        } else {
            null
        }
        capture = CameraCapture(cameraView, lifecycleOwner) { yuv, w, h ->
            yuvQueue.offer(YuvFrame(yuv, w, h))
        }

        running = true
        capture?.start()
        encodeThread = thread(name = "video-encode-ws", isDaemon = true) { encodeLoop() }
        sendThread = thread(name = "video-send-ws", isDaemon = true) { sendLoopWebSocket() }
        // 不启动 recvThread，接收通过 feedVideoFrame 回调

        LogUtil.d(TAG, "<<< WebSocket 视频引擎启动成功")
    }

    /**
     * 接收远端通过 WebSocket 发来的视频帧数据
     * 由 WebSocketListenerImpl 调用
     *
     * @param videoPacketData 视频分片包数据（13字节头 + payload，不含 0x01 类型字节）
     */
    fun feedVideoFrame(videoPacketData: ByteArray) {
        if (!running) return
        try {
            // 解析 VideoPacket
            val packet = VideoPacket.Companion.fromBytes(videoPacketData, videoPacketData.size) ?: return

            // 使用与 PacketReceiver 相同的重组逻辑
            feedBuffer.getOrPut(packet.seq) { ConcurrentHashMap() }
                .put(packet.chunkIndex, packet.data)
            feedFrameInfo[packet.seq] = Pair(packet.totalChunks, System.currentTimeMillis())

            val chunks = feedBuffer[packet.seq]
            if (chunks != null && chunks.size == packet.totalChunks) {
                // 重组完整帧
                var totalSize = 0
                for (i in 0 until packet.totalChunks) {
                    totalSize += chunks[i]?.size ?: 0
                }
                val completeFrame = ByteArray(totalSize)
                var offset = 0
                for (i in 0 until packet.totalChunks) {
                    val chunk = chunks[i]
                    if (chunk != null) {
                        System.arraycopy(chunk, 0, completeFrame, offset, chunk.size)
                        offset += chunk.size
                    }
                }

                // 处理编码模式同步
                if (packet.mode != encodingMode) {
                    LogUtil.d(TAG, "对方编码模式变更: $encodingMode → ${packet.mode}")
                    switchEncodingMode(packet.mode)
                }

                // 解码渲染
                codec?.decodeAndRender(completeFrame)

                // 清理
                feedBuffer.remove(packet.seq)
                feedFrameInfo.remove(packet.seq)
                feedLastRenderedSeq = packet.seq
            }

            // 清理超时帧
            val now = System.currentTimeMillis()
            feedFrameInfo.entries.removeIf { (s, info) ->
                if (now - info.second > VideoEngineConfig.FRAME_TIMEOUT_MS) {
                    feedBuffer.remove(s)
                    true
                } else false
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "feedVideoFrame 处理失败: ${e.message}")
        }
    }

    // WebSocket 接收端分片缓冲
    private val feedBuffer = ConcurrentHashMap<Int, ConcurrentHashMap<Int, ByteArray>>()
    private val feedFrameInfo = ConcurrentHashMap<Int, Pair<Int, Long>>()
    private var feedLastRenderedSeq = 0

    /**
     * 停止视频引擎
     */
    fun stop() {
        if (!running) return
        LogUtil.d(TAG, ">>> 停止: 已发送=${sender?.getSentCount()}, 已接收=${receiver?.getRecvCount()}")

        // 1. 设置停止标志
        running = false

        // 2. 唤醒所有等待的线程
        yuvQueue.notifyWaiting()
        encodedQueue.notifyWaiting()

        // 3. 关闭采集
        capture?.stop()

        // 4. 关闭网络（会中断阻塞的 receive）
        recvSocket?.close()
        sendSocket?.close()

        // 5. 等待线程结束
        encodeThread?.join(1000)
        sendThread?.join(1000)
        recvThread?.join(1000)

        // 6. 释放编解码器
        codec?.release()

        // 7. 清空队列和引用
        yuvQueue.clear()
        encodedQueue.clear()
        capture = null
        codec = null
        sender = null
        receiver = null
        sendSocket = null
        recvSocket = null
        wsSender = null
        wsMode = false
        feedBuffer.clear()
        feedFrameInfo.clear()
        feedLastRenderedSeq = 0
        encodeThread = null
        sendThread = null
        recvThread = null

        LogUtil.d(TAG, "<<< 已停止")
    }

    /**
     * 切换前置/后置摄像头
     */
    fun switchCamera() {
        capture?.switchCamera()
    }

    /**
     * 运行时切换编码模式（JPEG ↔ H.264）
     */
    fun switchEncodingMode(mode: EncodingMode) {
        if (encodingMode == mode) return
        LogUtil.d(TAG, "切换编码模式: $encodingMode → $mode")

        synchronized(this) {
            codec?.release()
            remoteSurface?.let {
                codec = VideoCodecFactory.create(mode, it)
            }
            encodingMode = mode
        }
    }

    // ==================== 内部工作线程 ====================

    /**
     * 编码线程：从 yuvQueue 取 YUV，编码后放入 encodedQueue
     *
     * 编码使用 YuvFrame 自带的真实尺寸（CameraView 实际输出的）
     * 避免和 VideoEngineConfig 的目标尺寸不一致导致数据错乱
     */
    private fun encodeLoop() {
        LogUtil.d(TAG, "编码线程已启动")
        while (running) {
            try {
                val frame = yuvQueue.take() ?: continue
                val encoded = codec?.encode(frame.data, frame.width, frame.height)
                if (encoded != null && encoded.isNotEmpty()) {
                    encodedQueue.offer(encoded)
                }
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                if (running) LogUtil.e(TAG, "编码异常: ${e.message}")
            }
        }
        LogUtil.d(TAG, "编码线程已退出")
    }

    /**
     * 发送线程：从 encodedQueue 取数据，分片发送
     */
    private fun sendLoop() {
        LogUtil.d(TAG, "发送线程已启动")
        while (running) {
            try {
                val frame = encodedQueue.take() ?: continue
                sender?.sendFrame(frame, encodingMode)
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                if (running) LogUtil.e(TAG, "发送异常: ${e.message}")
                if (!running) break
            }
        }
        LogUtil.d(TAG, "发送线程已退出")
    }

    /**
     * WebSocket 模式发送线程
     * 从 encodedQueue 取数据，分片后通过 WebSocket 发送
     *
     * 帧格式: [0x01] + [encodingType(1)] + [seq(4)] + [timestamp(4)] + [totalChunks(2)] + [chunkIndex(2)] + [data(N)]
     */
    private fun sendLoopWebSocket() {
        LogUtil.d(TAG, "WebSocket 发送线程已启动")
        while (running) {
            try {
                val frame = encodedQueue.take() ?: continue
                sendFrameWebSocket(frame, encodingMode)
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                if (running) LogUtil.e(TAG, "WS发送异常: ${e.message}")
                if (!running) break
            }
        }
        LogUtil.d(TAG, "WebSocket 发送线程已退出")
    }

    /**
     * 通过 WebSocket 发送一帧视频数据
     * 复用 PacketSender 的分片逻辑，只是最终发送改为 WebSocket
     */
    private fun sendFrameWebSocket(frameData: ByteArray, mode: EncodingMode) {
        val payloadSize = VideoEngineConfig.PAYLOAD_SIZE
        val totalChunks = (frameData.size + payloadSize - 1) / payloadSize
        val seq = wsFrameSeq.incrementAndGet()
        val timestamp = System.currentTimeMillis().toInt()

        for (chunkIndex in 0 until totalChunks) {
            val offset = chunkIndex * payloadSize
            val length = minOf(payloadSize, frameData.size - offset)

            // 构造: [0x01] + VideoPacket.toBytes()
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
            val packetBytes = videoPacket.toBytes()
            val wsFrame = ByteArray(1 + packetBytes.size)
            wsFrame[0] = 0x01  // 类型: 视频
            System.arraycopy(packetBytes, 0, wsFrame, 1, packetBytes.size)

            wsSender?.invoke(wsFrame)

            // 节流
            if (chunkIndex > 0 && chunkIndex % 32 == 0) {
                // 视频分片发送线程为专用 Thread，避免协程调度对实时编码造成抖动
                Thread.sleep(1)
            }
        }

        if (seq <= 3 || seq % 30 == 0) {
            LogUtil.d(TAG, "WS发送 #$seq [$mode], ${frameData.size}字节, ${totalChunks}个分片")
        }
    }

    /**
     * 接收线程：接收 UDP 分片，重组后调用 handleReceivedFrame
     */
    private fun recvLoop() {
        LogUtil.d(TAG, "接收线程已启动")
        while (running) {
            try {
                receiver?.receiveOne()
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                if (!running) break
                if (running) LogUtil.e(TAG, "接收异常: ${e.message}")
            }
        }
        LogUtil.d(TAG, "接收线程已退出")
    }

    /**
     * 处理接收到的完整帧
     *
     * - 如果对方编码模式变了，自动同步切换我方解码器
     * - 调用编解码器解码渲染
     */
    private fun handleReceivedFrame(frameMode: EncodingMode, frameData: ByteArray) {
        // 自动同步对方的编码模式
        if (frameMode != encodingMode) {
            LogUtil.d(TAG, "检测到对方编码模式变更: $encodingMode → $frameMode")
            switchEncodingMode(frameMode)
        }

        // 解码并渲染
        codec?.decodeAndRender(frameData)
    }
}
