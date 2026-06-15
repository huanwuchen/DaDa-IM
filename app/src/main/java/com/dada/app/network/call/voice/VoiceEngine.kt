package com.dada.app.network.call.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.dada.core.common.utils.LogUtil
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.iterator
import kotlin.concurrent.thread

/**
 * 语音通话引擎 - 基于 UDP 的实时音频传输
 *
 * 【功能说明】
 * 这是一个局域网语音通话引擎，使用 UDP 协议直接传输音频数据
 * 适用于同一 WiFi 网络内的设备间通话，无需中转服务器
 *
 * 【工作原理】
 * 1. 录音：通过 AudioRecord 采集麦克风音频
 * 2. 发送：将音频数据打包成 UDP 数据包发送给对方
 * 3. 接收：接收对方发来的 UDP 音频数据包
 * 4. 播放：通过 AudioTrack 播放接收到的音频
 *
 * 【数据包格式】
 * 每个 UDP 包结构：序号4字节 + 时间戳4字节 + 音频数据640字节
 * - 序号：用于检测丢包和乱序
 * - 时间戳：用于计算延迟
 * - 音频数据：PCM 格式的原始音频（16kHz, 单声道, 16位）
 *
 * 【使用方法】
 * val engine = VoiceEngine()
 * engine.start(targetIp, targetPort, recvSocket, context)  // 开始通话
 * engine.setMuted(true)                                     // 静音/取消静音
 * engine.stop()                                             // 结束通话
 */
class VoiceEngine {

    // ==================== 常量定义 ====================
    companion object {
        private const val TAG = "VoiceEngine"

        // 音频参数配置
        private const val SAMPLE_RATE = 16000                           // 采样率：16kHz（电话音质，适合语音通话）
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO      // 录音：单声道（减少数据量）
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO    // 播放：单声道
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // 音频格式：16位 PCM（每个采样2字节）

        // 数据包参数
        private const val FRAME_SIZE = 640        // 每帧音频数据大小：640字节 = 320个采样点 × 2字节 = 20毫秒音频
        private const val HEADER_SIZE = 8         // 包头大小：4字节序号 + 4字节时间戳
        private const val PACKET_SIZE = HEADER_SIZE + FRAME_SIZE  // 完整数据包大小：648字节

        /**
         * 获取本机局域网 IP 地址
         *
         * 【作用】用于显示给用户，让对方知道要连接的 IP
         * 【返回】IPv4 地址字符串，如 "192.168.1.100"，失败返回 null
         */
        fun getLocalIpAddress(): String? {
            try {
                // 遍历所有网络接口（WiFi、以太网等）
                for (intf in NetworkInterface.getNetworkInterfaces()) {
                    // 跳过回环接口（127.0.0.1）和未启用的接口
                    if (intf.isLoopback || !intf.isUp) continue

                    // 遍历该接口的所有 IP 地址
                    for (addr in intf.inetAddresses) {
                        if (addr.isLoopbackAddress) continue
                        val ip = addr.hostAddress ?: continue

                        // 只返回 IPv4 地址（包含"."且不是127开头）
                        if (ip.contains(".") && !ip.startsWith("127.")) {
                            return ip
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "获取本地 IP 失败: ${e.message}")
            }
            return null
        }
    }

    // ==================== 状态变量 ====================

    // 运行状态控制
    @Volatile private var running = false  // 引擎是否正在运行（volatile 保证多线程可见性）

    // 网络组件
    private var sendSocket: DatagramSocket? = null      // 发送 UDP 数据包的 Socket
    private var recvSocket: DatagramSocket? = null      // 接收 UDP 数据包的 Socket
    private var targetAddress: InetAddress? = null      // 对方的 IP 地址
    private var targetPort: Int = 0                     // 对方的端口号
    private var localPort: Int = 0                      // 本地监听端口号

    // WebSocket 模式
    private var wsMode = false                         // 是否使用 WebSocket 传输
    private var wsSender: ((ByteArray) -> Boolean)? = null  // WebSocket 发送函数

    // 音频组件
    private var audioRecord: AudioRecord? = null        // 录音器：采集麦克风音频
    private var audioTrack: AudioTrack? = null          // 播放器：播放接收到的音频
    private var audioManager: AudioManager? = null      // 音频管理器：控制音频模式和扬声器
    private var originalAudioMode: Int = AudioManager.MODE_NORMAL  // 保存原始音频模式，用于恢复

    // 工作线程
    private var sendThread: Thread? = null              // 发送线程：持续读取麦克风并发送
    private var recvThread: Thread? = null              // 接收线程：持续接收并播放音频

    // 功能控制
    @Volatile private var isMuted = false               // 是否静音（true=发送静音数据）

    // 统计信息
    private val sentCount = AtomicInteger(0)            // 已发送的数据包数量（原子操作保证线程安全）
    private val recvCount = AtomicInteger(0)            // 已接收的数据包数量

    // ==================== 公共接口 ====================

    /**
     * 启动语音引擎，开始通话
     *
     * 【参数说明】
     * @param targetIp 对方的 IP 地址，如 "192.168.1.100"
     * @param targetPort 对方的端口号，如 8888
     * @param recvSocket 用于接收数据的 UDP Socket（已绑定本地端口）
     * @param context Android 上下文，用于获取音频服务
     *
     * 【执行流程】
     * 1. 初始化网络：创建发送 Socket，保存对方地址
     * 2. 配置音频模式：切换到通话模式，开启扬声器
     * 3. 初始化录音器：创建 AudioRecord 用于采集麦克风
     * 4. 初始化播放器：创建 AudioTrack 用于播放音频
     * 5. 启动工作线程：开启发送和接收线程
     *
     * 【注意事项】
     * - 如果引擎已在运行，重复调用会被忽略
     * - 需要 RECORD_AUDIO 权限，否则会失败
     * - 会自动开启扬声器，方便免提通话
     */
    fun start(targetIp: String, targetPort: Int, recvSocket: DatagramSocket, context: Context) {
        // 防止重复启动
        if (running) return
        LogUtil.d(TAG, ">>> 开始启动语音引擎: 目标=$targetIp:$targetPort, 本地端口=${recvSocket.localPort}")

        // ========== 第1步：初始化网络连接 ==========
        this.targetAddress = InetAddress.getByName(targetIp)  // 解析对方 IP 地址
        this.targetPort = targetPort                          // 保存对方端口
        this.recvSocket = recvSocket                          // 使用传入的接收 Socket
        this.localPort = recvSocket.localPort                 // 记录本地端口
        sendSocket = DatagramSocket()                         // 创建发送 Socket（系统自动分配端口）

        // ========== 第2步：配置音频模式 ==========
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.let { am ->
            originalAudioMode = am.mode  // 保存原始模式，用于通话结束后恢复
            am.mode = AudioManager.MODE_IN_COMMUNICATION  // 切换到通话模式（优化回声消除和降噪）
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = true   // 开启扬声器（免提通话）
            am.isMicrophoneMute = false  // 确保麦克风未静音
            LogUtil.d(TAG, "音频模式已设置: 通话模式 + 扬声器开启")
        }

        // ========== 第3步：初始化录音器 (AudioRecord) ==========
        // 计算最小缓冲区大小
        val minRecBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
        LogUtil.d(TAG, "AudioRecord 最小缓冲区: $minRecBuf 字节")

        // 检查是否支持该音频配置
        if (minRecBuf == AudioRecord.ERROR || minRecBuf == AudioRecord.ERROR_BAD_VALUE) {
            LogUtil.e(TAG, "AudioRecord 不支持该音频配置: $minRecBuf")
            return
        }

        // 使用2倍最小缓冲区，避免录音溢出（至少能容纳4帧数据）
        val recBuf = maxOf(minRecBuf * 2, FRAME_SIZE * 4)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // 音频源：通话（自动启用回声消除）
            SAMPLE_RATE,      // 采样率：16kHz
            CHANNEL_IN,       // 声道：单声道
            AUDIO_FORMAT,     // 格式：16位 PCM
            recBuf            // 缓冲区大小
        )

        // 检查录音器是否初始化成功
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            LogUtil.e(TAG, "AudioRecord 初始化失败, state=${audioRecord?.state}")
            audioRecord?.release()
            audioRecord = null
            return
        }

        // ========== 第4步：初始化播放器 (AudioTrack) ==========
        // 计算最小缓冲区大小
        val minPlayBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
        LogUtil.d(TAG, "AudioTrack 最小缓冲区: $minPlayBuf 字节")

        // 检查是否支持该音频配置
        if (minPlayBuf == AudioTrack.ERROR || minPlayBuf == AudioTrack.ERROR_BAD_VALUE) {
            LogUtil.e(TAG, "AudioTrack 不支持该音频配置: $minPlayBuf")
            return
        }

        // 使用2倍最小缓冲区，避免播放卡顿（至少能容纳4帧数据）
        val playBuf = maxOf(minPlayBuf * 2, FRAME_SIZE * 4)
        @Suppress("DEPRECATION")
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,  // 音频流类型：音乐（配合 MODE_IN_COMMUNICATION 使用）
            SAMPLE_RATE,                // 采样率：16kHz
            CHANNEL_OUT,                // 声道：单声道
            AUDIO_FORMAT,               // 格式：16位 PCM
            playBuf,                    // 缓冲区大小
            AudioTrack.MODE_STREAM      // 流模式：持续播放
        )

        // 检查播放器是否初始化成功
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            LogUtil.e(TAG, "AudioTrack 初始化失败, state=${audioTrack?.state}")
            audioTrack?.release()
            audioTrack = null
            return
        }

        // ========== 第5步：启动工作线程 ==========
        running = true                  // 设置运行标志
        sentCount.set(0)                // 重置发送计数
        recvCount.set(0)                // 重置接收计数
        audioRecord?.startRecording()   // 开始录音
        audioTrack?.play()              // 开始播放
        LogUtil.d(TAG, "录音状态=${audioRecord?.recordingState}, 播放状态=${audioTrack?.playState}")

        // 创建并启动发送线程（守护线程，主线程结束时自动退出）
        sendThread = thread(name = "voice-send", isDaemon = true) { sendLoop() }
        // 创建并启动接收线程
        recvThread = thread(name = "voice-recv", isDaemon = true) { recvLoop() }

        LogUtil.d(TAG, "<<< 语音引擎启动成功! 本地端口=${this.localPort}")
    }

    /**
     * 启动 WebSocket 模式的语音引擎
     * 不使用 UDP，通过 WebSocket 二进制帧收发音频数据
     *
     * @param wsSender WebSocket 发送函数，接收封装后的二进制帧
     * @param context Android 上下文
     */
    fun startWebSocket(wsSender: (ByteArray) -> Boolean, context: Context) {
        if (running) return
        LogUtil.d(TAG, ">>> 启动 WebSocket 模式语音引擎")

        this.wsMode = true
        this.wsSender = wsSender

        // 配置音频模式
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.let { am ->
            originalAudioMode = am.mode
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = true
            am.isMicrophoneMute = false
            LogUtil.d(TAG, "音频模式已设置: 通话模式 + 扬声器开启")
        }

        // 初始化录音器
        val minRecBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
        if (minRecBuf == AudioRecord.ERROR || minRecBuf == AudioRecord.ERROR_BAD_VALUE) {
            LogUtil.e(TAG, "AudioRecord 不支持该音频配置: $minRecBuf")
            return
        }
        val recBuf = maxOf(minRecBuf * 2, FRAME_SIZE * 4)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT, recBuf
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            LogUtil.e(TAG, "AudioRecord 初始化失败")
            audioRecord?.release()
            audioRecord = null
            return
        }

        // 初始化播放器
        val minPlayBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
        if (minPlayBuf == AudioTrack.ERROR || minPlayBuf == AudioTrack.ERROR_BAD_VALUE) {
            LogUtil.e(TAG, "AudioTrack 不支持该音频配置")
            return
        }
        val playBuf = maxOf(minPlayBuf * 2, FRAME_SIZE * 4)
        @Suppress("DEPRECATION")
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT, playBuf, AudioTrack.MODE_STREAM
        )
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            LogUtil.e(TAG, "AudioTrack 初始化失败")
            audioTrack?.release()
            audioTrack = null
            return
        }

        // 启动
        running = true
        sentCount.set(0)
        recvCount.set(0)
        audioRecord?.startRecording()
        audioTrack?.play()

        // 只启动发送线程，接收通过 feedPcmPacket 回调
        sendThread = thread(name = "voice-send-ws", isDaemon = true) { sendLoopWebSocket() }

        LogUtil.d(TAG, "<<< WebSocket 语音引擎启动成功")
    }

    /**
     * 接收远端通过 WebSocket 发来的音频 PCM 包
     * 由 WebSocketListenerImpl 调用
     *
     * @param pcmData PCM 音频数据（640 字节）
     */
    fun feedPcmPacket(pcmData: ByteArray) {
        if (!running) return
        if (pcmData.size >= FRAME_SIZE) {
            audioTrack?.write(pcmData, 0, FRAME_SIZE)
            recvCount.incrementAndGet()
        }
    }

    /**
     * 停止语音引擎，结束通话
     *
     * 【执行流程】
     * 1. 停止运行标志，通知工作线程退出
     * 2. 关闭网络 Socket
     * 3. 停止并释放录音器和播放器
     * 4. 恢复原始音频模式
     * 5. 等待工作线程结束
     *
     * 【注意事项】
     * - 可以安全地重复调用
     * - 会自动恢复通话前的音频模式
     * - 最多等待1秒让线程退出
     */
    fun stop() {
        // 如果未运行且没有音频组件，直接返回
        if (!running && audioRecord == null) return
        LogUtil.d(TAG, ">>> 开始停止语音引擎, 已发送=${sentCount.get()}包, 已接收=${recvCount.get()}包")

        // ========== 第1步：停止运行标志 ==========
        running = false  // 通知工作线程退出循环

        // ========== 第2步：关闭网络连接 ==========
        recvSocket?.close()  // 关闭接收 Socket（会中断 receive() 阻塞）
        sendSocket?.close()  // 关闭发送 Socket

        // ========== 第3步：停止并释放录音器 ==========
        audioRecord?.let {
            runCatching { it.stop() }     // 停止录音（可能抛异常，用 runCatching 捕获）
            runCatching { it.release() }  // 释放资源
        }
        audioRecord = null

        // ========== 第4步：停止并释放播放器 ==========
        audioTrack?.let {
            runCatching { it.pause() }    // 暂停播放
            runCatching { it.flush() }    // 清空缓冲区
            runCatching { it.release() }  // 释放资源
        }
        audioTrack = null

        // ========== 第5步：恢复音频模式 ==========
        audioManager?.let { am ->
            am.mode = originalAudioMode  // 恢复到通话前的模式
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false  // 关闭扬声器
            LogUtil.d(TAG, "音频模式已恢复: $originalAudioMode")
        }
        audioManager = null

        // ========== 第6步：清理资源 ==========
        sendSocket = null
        recvSocket = null
        wsSender = null
        wsMode = false

        // ========== 第7步：等待工作线程结束 ==========
        sendThread?.join(1000)  // 最多等待1秒
        recvThread?.join(1000)
        sendThread = null
        recvThread = null

        LogUtil.d(TAG, "<<< 语音引擎已停止")
    }

    /**
     * 设置静音状态
     *
     * 【功能说明】
     * - true：发送静音数据（全0），对方听不到声音，但通话连接保持
     * - false：正常发送麦克风音频
     *
     * 【使用场景】
     * 临时静音（如接听其他电话、避免背景噪音等）
     *
     * @param muted true=静音, false=取消静音
     */
    fun setMuted(muted: Boolean) {
        isMuted = muted
        LogUtil.d(TAG, "静音状态: ${if (muted) "已开启" else "已关闭"}")
    }

    // ==================== 内部工作线程 ====================

    /**
     * 发送线程：持续采集麦克风音频并发送给对方
     *
     * 【工作流程】
     * 1. 从 AudioRecord 读取一帧音频数据（640字节）
     * 2. 如果静音，将数据清零
     * 3. 构造数据包：[序号][时间戳][音频数据]
     * 4. 通过 UDP 发送给对方
     * 5. 循环执行，直到 running 变为 false
     *
     * 【数据包结构】
     * - 字节 0-3：序号（int，大端序）
     * - 字节 4-7：时间戳（int，大端序，毫秒）
     * - 字节 8-647：PCM 音频数据（640字节）
     */
    private fun sendLoop() {
        val buffer = ByteArray(FRAME_SIZE)    // 音频数据缓冲区（640字节）
        val packet = ByteArray(PACKET_SIZE)   // 完整数据包缓冲区（648字节）
        LogUtil.d(TAG, "发送线程已启动")

        while (running) {
            try {
                // ========== 步骤1：读取麦克风音频 ==========
                val read = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: -1
                if (read <= 0) {
                    // 读取失败，可能是录音器未就绪，稍等后重试
                    LogUtil.w(TAG, "AudioRecord.read 返回 $read，等待重试")
                    Thread.sleep(10)
                    continue
                }

                // ========== 步骤2：处理静音 ==========
                if (isMuted) {
                    buffer.fill(0, 0, read)  // 静音时填充0（静音数据）
                }

                // ========== 步骤3：构造数据包 ==========
                val seq = sentCount.incrementAndGet()  // 递增序号（从1开始）
                val ts = System.currentTimeMillis()    // 当前时间戳
                ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN).apply {
                    putInt(seq)              // 写入序号（4字节）
                    putInt(ts.toInt())       // 写入时间戳（4字节）
                    put(buffer, 0, read)     // 写入音频数据（640字节）
                }

                // ========== 步骤4：发送 UDP 数据包 ==========
                val datagram = DatagramPacket(packet, PACKET_SIZE, targetAddress, targetPort)
                sendSocket?.send(datagram)

                // ========== 步骤5：日志输出（避免刷屏，只打印前5包和每100包） ==========
                if (seq <= 5 || seq % 100 == 0) {
                    LogUtil.d(TAG, "发送 #$seq → ${targetAddress?.hostAddress}:$targetPort, $read 字节")
                }

            } catch (_: InterruptedException) {
                // 线程被中断，退出循环
                break
            } catch (e: Exception) {
                // 其他异常（如网络错误）
                if (running) LogUtil.e(TAG, "发送异常: ${e.message}")
                if (!running) break  // 如果已停止，退出循环
            }
        }
        LogUtil.d(TAG, "发送线程已退出")
    }

    /**
     * WebSocket 模式发送线程
     * 采集麦克风音频，通过 WebSocket 二进制帧发送
     *
     * 帧格式: [0x02] + [seq(4)] + [timestamp(4)] + [pcmData(640)]
     */
    private fun sendLoopWebSocket() {
        val buffer = ByteArray(FRAME_SIZE)
        val packet = ByteArray(1 + PACKET_SIZE)  // 1字节类型 + 8字节头 + 640字节PCM
        LogUtil.d(TAG, "WebSocket 发送线程已启动")

        while (running) {
            try {
                val read = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: -1
                if (read <= 0) {
                    Thread.sleep(10)
                    continue
                }

                if (isMuted) {
                    buffer.fill(0, 0, read)
                }

                val seq = sentCount.incrementAndGet()
                val ts = System.currentTimeMillis()

                // 构造: [0x02] + [seq(4)] + [ts(4)] + [pcm(640)]
                ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN).apply {
                    put(0x02.toByte())           // 类型: 音频
                    putInt(seq)
                    putInt(ts.toInt())
                    put(buffer, 0, read)
                }

                wsSender?.invoke(packet.copyOf(1 + HEADER_SIZE + read))

                if (seq <= 5 || seq % 100 == 0) {
                    LogUtil.d(TAG, "WS发送 #$seq, $read 字节")
                }

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
     * 接收线程：持续接收对方发来的音频并播放
     *
     * 【工作流程】
     * 1. 从 UDP Socket 接收数据包
     * 2. 解析包头：提取序号和时间戳
     * 3. 提取音频数据部分
     * 4. 写入 AudioTrack 播放
     * 5. 循环执行，直到 running 变为 false
     *
     * 【注意事项】
     * - receive() 是阻塞调用，会一直等待直到收到数据或 Socket 关闭
     * - 丢包是正常现象（UDP 不保证可靠传输），音频会有轻微卡顿
     * - 乱序包会导致音频不连贯，但影响较小（20ms一帧）
     */
    private fun recvLoop() {
        val buffer = ByteArray(PACKET_SIZE)  // 数据包缓冲区（648字节）
        LogUtil.d(TAG, "接收线程已启动，等待数据...")

        while (running) {
            try {
                // ========== 步骤1：接收 UDP 数据包 ==========
                val packet = DatagramPacket(buffer, buffer.size)
                recvSocket?.receive(packet)  // 阻塞等待，直到收到数据

                // ========== 步骤2：检查数据包大小 ==========
                if (packet.length < HEADER_SIZE) continue  // 包太小，丢弃

                // ========== 步骤3：解析包头 ==========
                val header = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN)
                val seq = header.int       // 读取序号
                @Suppress("UNUSED_VARIABLE")
                val ts = header.int        // 读取时间戳（暂未使用，可用于计算延迟）

                // ========== 步骤4：提取并播放音频数据 ==========
                val pcmSize = packet.length - HEADER_SIZE  // 音频数据大小
                if (pcmSize > 0) {
                    // 将音频数据写入播放器（从第8字节开始）
                    val written = audioTrack?.write(buffer, HEADER_SIZE, pcmSize) ?: -1
                    val count = recvCount.incrementAndGet()

                    // ========== 步骤5：日志输出 ==========
                    if (count <= 5 || count % 100 == 0) {
                        LogUtil.d(TAG, "接收 #$count ← ${packet.address?.hostAddress}:${packet.port}, seq=$seq, pcm=${pcmSize}字节, written=$written")
                    }
                }

            } catch (_: InterruptedException) {
                // 线程被中断，退出循环
                break
            } catch (e: Exception) {
                // Socket 关闭或其他异常
                if (!running) break  // 如果已停止，正常退出
                if (running) LogUtil.e(TAG, "接收异常: ${e.message}")
            }
        }
        LogUtil.d(TAG, "接收线程已退出")
    }
}