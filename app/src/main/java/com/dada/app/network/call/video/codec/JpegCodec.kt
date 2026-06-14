package com.dada.app.network.call.video.codec

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import com.dada.core.common.utils.LogUtil
import android.view.SurfaceView
import com.dada.app.network.call.video.VideoEngineConfig
import java.io.ByteArrayOutputStream

/**
 * JPEG 编解码器
 *
 * 【特点】
 * - 每帧独立编码（不依赖前后帧）
 * - 丢包友好（丢一帧不影响其他帧）
 * - 实现简单
 * - 压缩率中等（1:5-10）
 *
 * 【适用场景】
 * - 弱网络环境
 * - 低帧率应用
 * - 学习/原型项目
 *
 * @param remoteSurface 用于显示视频的 SurfaceView
 */
class JpegCodec(private val remoteSurface: SurfaceView) : VideoCodec {

    companion object {
        private const val TAG = "JpegCodec"
    }

    /**
     * YUV → JPEG 压缩
     *
     * 使用 Android 内置的 YuvImage 直接压缩，简单高效
     * 使用从 CameraView 传来的真实尺寸（不是配置文件里的目标尺寸）
     */
    override fun encode(yuv: ByteArray, width: Int, height: Int): ByteArray {
        val yuvImage = YuvImage(
            yuv,
            ImageFormat.NV21,
            width,
            height,
            null
        )
        val baos = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, width, height),
            VideoEngineConfig.JPEG_QUALITY,
            baos
        )
        return baos.toByteArray()
    }

    /**
     * JPEG → Bitmap → 绘制到 Surface
     *
     * 【执行流程】
     * 1. 解码 JPEG 为 Bitmap
     * 2. 锁定 Canvas
     * 3. 计算 Center Crop 区域（保持宽高比）
     * 4. 绘制并提交
     * 5. 释放 Bitmap
     */
    override fun decodeAndRender(encodedData: ByteArray) {
        try {
            // ========== 步骤1：检查 Surface ==========
            val holder = remoteSurface.holder
            if (!holder.surface.isValid) return

            // ========== 步骤2：JPEG → Bitmap ==========
            val bitmap = BitmapFactory.decodeByteArray(encodedData, 0, encodedData.size) ?: return

            // ========== 步骤3：绘制到 Surface ==========
            val canvas: Canvas? = holder.lockCanvas()
            if (canvas != null) {
                try {
                    drawCenterCrop(canvas, bitmap)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }

            // ========== 步骤4：释放 Bitmap ==========
            bitmap.recycle()

        } catch (e: Exception) {
            LogUtil.e(TAG, "JPEG 解码渲染失败: ${e.message}")
        }
    }

    /**
     * Center Crop 绘制（保持宽高比，填满整个 Surface，超出部分裁切）
     */
    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap) {
        val surfaceWidth = canvas.width
        val surfaceHeight = canvas.height
        val videoWidth = bitmap.width.toFloat()
        val videoHeight = bitmap.height.toFloat()

        val videoRatio = videoWidth / videoHeight
        val surfaceRatio = surfaceWidth.toFloat() / surfaceHeight.toFloat()

        val srcRect: Rect
        val dstRect = Rect(0, 0, surfaceWidth, surfaceHeight)

        if (videoRatio > surfaceRatio) {
            // 视频更宽 → 裁切左右
            val cropWidth = (videoHeight * surfaceRatio).toInt()
            val cropX = ((videoWidth - cropWidth) / 2).toInt()
            srcRect = Rect(cropX, 0, cropX + cropWidth, videoHeight.toInt())
        } else {
            // 视频更高 → 裁切上下
            val cropHeight = (videoWidth / surfaceRatio).toInt()
            val cropY = ((videoHeight - cropHeight) / 2).toInt()
            srcRect = Rect(0, cropY, videoWidth.toInt(), cropY + cropHeight)
        }

        canvas.drawBitmap(bitmap, srcRect, dstRect, null)
    }

    override fun release() {
        // JPEG 不需要释放资源
    }
}
