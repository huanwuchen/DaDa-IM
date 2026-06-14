package com.dada.app.network.call.video

/**
 * 视频引擎配置常量
 *
 * 【作用】
 * 集中管理所有配置参数，方便统一调整
 * 修改参数只需改这一个文件，不用到处找
 *
 * 【当前配置：1080P 高画质（局域网模式）】
 * 适合：同 WiFi 局域网测试、高画质需求
 * 不适合：移动网络、跨网传输
 */
object VideoEngineConfig {

    // ==================== 视频参数 ====================
    // ⚠️ 整条链路必须使用一致的分辨率：
    //   CameraView 输出 == 这里的配置 == MediaCodec 编码器配置
    //
    // 如果改这里的尺寸，请同步检查：
    //   1) CameraCapture 限制 CameraView 输出尺寸
    //   2) 设备摄像头是否支持该分辨率
    //   3) FRAME_TIMEOUT_MS 是否够大（高分辨率分片多，需更多时间收齐）
    //   4) H264_BITRATE 是否够大（高分辨率需要更高码率）
    const val WIDTH = 1920                   // 视频宽度：1080P
    const val HEIGHT = 1080                  // 视频高度：1080P
    const val FPS = 15                       // 帧率：30帧/秒（流畅度）

    // ==================== JPEG 编码参数 ====================
    // 注意：1080P JPEG 单帧 200-500KB，带宽 50+ Mbps，局域网勉强能跑
    // 推荐使用 H.264，画质更好且省带宽
    const val JPEG_QUALITY = 60              // JPEG 压缩质量：60%

    // ==================== H.264 编码参数 ====================
    // 1080P 30fps 推荐码率：3-6 Mbps（局域网完全够用）
    const val H264_BITRATE = 4_000_000       // H.264 码率：4Mbps（1080P 清晰画质）
    const val H264_I_FRAME_INTERVAL = 1      // I 帧间隔：1秒（每秒1个关键帧，便于丢包恢复）

    // ==================== 网络参数 ====================
    const val MAX_PACKET_SIZE = 1400         // UDP 最大包大小：1400字节（避免 IP 分片）
    const val HEADER_SIZE = 13               // 包头大小：1(编码类型)+4(序号)+4(时间戳)+2(总分片数)+2(分片索引)=13字节
    const val PAYLOAD_SIZE = MAX_PACKET_SIZE - HEADER_SIZE  // 每个包的有效载荷：1387字节

    // 帧超时：1080P 单帧可能 50-300 个分片，500ms 不够
    // 调大到 2 秒，保证局域网内丢包率较高时也能完整收齐
    const val FRAME_TIMEOUT_MS = 2000L

    // ==================== 队列参数 ====================
    // 1080P 数据量大，队列稍大一点可以缓冲网络抖动，但太大会增加延迟
    const val MAX_QUEUE_SIZE = 3
}

/**
 * 视频编码模式
 *
 * 【对比】
 * JPEG : 每帧独立，丢包友好，但带宽大
 * H.264: 高压缩率，带宽小，但依赖前后帧
 */
enum class EncodingMode(val code: Byte) {
    JPEG(1),    // JPEG 图片编码（每帧独立，弱网友好）
    H264(2);    // H.264 视频编码（高压缩率，推荐）

    companion object {
        /**
         * 根据 code 字节查找对应的编码模式
         */
        fun fromCode(code: Byte): EncodingMode {
            return values().firstOrNull { it.code == code } ?: H264
        }
    }
}
