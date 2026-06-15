package com.dada.app.network.call.video.transport

/**
 * YUV 原始帧数据 + 尺寸信息
 *
 * 【为什么需要带尺寸？】
 * CameraView 不一定能输出我们指定的分辨率，会自动 fallback 到设备支持的尺寸
 * 编码器和 nv21ToNv12 必须用真实尺寸才能正确处理，否则数据错乱
 */
data class YuvFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int
)
