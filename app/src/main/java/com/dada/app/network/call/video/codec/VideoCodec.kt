package com.dada.app.network.call.video.codec

/**
 * 视频编解码器统一接口
 *
 * 【作用】
 * 让 JPEG 和 H.264 有相同的调用方式，方便切换
 * 主引擎只需要持有 VideoCodec 接口，不用关心具体实现
 *
 * 【实现类】
 * - JpegCodec: JPEG 图片编解码
 * - H264Codec: H.264 视频编解码
 */
interface VideoCodec {

    /**
     * 编码：YUV (NV21) → 压缩数据
     *
     * @param yuv NV21 格式的 YUV 数据
     * @param width 帧宽度（来自 CameraView 的真实尺寸）
     * @param height 帧高度
     * @return 编码后的数据，失败返回 null
     */
    fun encode(yuv: ByteArray, width: Int, height: Int): ByteArray?

    /**
     * 解码并显示：压缩数据 → 渲染到 Surface
     *
     * 【注意】
     * - JPEG: 解码为 Bitmap 后手动绘制到 Canvas
     * - H.264: 直接渲染到 Surface（硬件加速）
     *
     * @param encodedData 压缩数据
     */
    fun decodeAndRender(encodedData: ByteArray)

    /**
     * 释放资源
     */
    fun release()
}
