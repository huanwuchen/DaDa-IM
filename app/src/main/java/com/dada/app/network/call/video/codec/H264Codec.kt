package com.dada.app.network.call.video.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.dada.core.common.utils.LogUtil
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import com.dada.app.network.call.video.VideoEngineConfig

/**
 * H.264 编解码器（基于硬件 MediaCodec）
 *
 * 【特点】
 * - 高压缩率（1:50-100）
 * - 带宽小（比 JPEG 节省 80-90%）
 * - 硬件加速（CPU 占用低）
 * - 直接渲染到 Surface（性能极高）
 *
 * 【关键设计】
 * 编码器使用 **CameraView 的真实输出尺寸** 来配置，
 * 而不是 VideoEngineConfig 里写死的目标尺寸。
 * 因为设备摄像头可能不支持你想要的分辨率（如 1920×1080），
 * 会自动 fallback 到 640×480 等。如果硬按 1920×1080 配置编码器，
 * 喂入的 460800 字节数据无法填满 3110400 字节的 Y 平面 → 报错黑屏。
 *
 * @param remoteSurface 用于显示视频的 SurfaceView
 */
class H264Codec(private val remoteSurface: SurfaceView) : VideoCodec {

    companion object {
        private const val TAG = "H264Codec"
        private const val DEQUEUE_TIMEOUT_US = 10_000L  // 10ms 超时
    }

    private var encoder: MediaCodec? = null
    private var decoder: MediaCodec? = null

    /** 是否已根据视频实际尺寸调整过 SurfaceView 大小 */
    private var videoSizeAdjusted = false

    // 编码器当前使用的尺寸（首帧到达后才确定）
    private var encoderWidth = 0
    private var encoderHeight = 0

    init {
        // 编码器延迟到首帧到达后再初始化（需要真实尺寸）
        initDecoder()
    }

    // ==================== 编码器初始化 ====================

    /**
     * 初始化 H.264 编码器（YUV → H.264）
     *
     * @param width 真实帧宽度（CameraView 实际输出的）
     * @param height 真实帧高度
     */
    private fun initEncoder(width: Int, height: Int) {
        try {
            // 已经按相同尺寸初始化过，不重复
            if (encoder != null && encoderWidth == width && encoderHeight == height) return

            // 如果尺寸变了，先释放旧的
            encoder?.let {
                try { it.stop(); it.release() } catch (_: Exception) {}
            }

            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                width,
                height
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, VideoEngineConfig.H264_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, VideoEngineConfig.FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VideoEngineConfig.H264_I_FRAME_INTERVAL)
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar  // 对应 NV12
                )
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            encoderWidth = width
            encoderHeight = height

            LogUtil.d(TAG, "H.264 编码器初始化成功: ${width}x${height}")

        } catch (e: Exception) {
            LogUtil.e(TAG, "H.264 编码器初始化失败: ${e.message}", e)
            encoder = null
        }
    }

    /**
     * 初始化 H.264 解码器（H.264 → 直接渲染到 Surface）
     */
    private fun initDecoder() {
        try {
            val surface: Surface = remoteSurface.holder.surface
            if (!surface.isValid) {
                // Surface 未就绪，注册回调等待
                LogUtil.w(TAG, "Surface 尚未就绪，延迟初始化解码器")
                remoteSurface.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        if (decoder == null) initDecoder()
                    }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) {}
                })
                return
            }

            // 解码器初始化时尺寸只是"建议值"，解码器会根据 SPS/PPS 自动调整
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                VideoEngineConfig.WIDTH,
                VideoEngineConfig.HEIGHT
            )

            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, surface, null, 0)
                start()
            }

            LogUtil.d(TAG, "H.264 解码器初始化成功")

        } catch (e: Exception) {
            LogUtil.e(TAG, "H.264 解码器初始化失败: ${e.message}", e)
            decoder = null
        }
    }

    // ==================== 编码 ====================

    /**
     * YUV (NV21) → H.264
     *
     * 关键：使用传入的真实 width/height，不是 VideoEngineConfig 里写死的值
     */
    override fun encode(yuv: ByteArray, width: Int, height: Int): ByteArray? {
        // 首次进入或尺寸变化时初始化编码器
        if (encoder == null || encoderWidth != width || encoderHeight != height) {
            initEncoder(width, height)
        }
        val codec = encoder ?: return null

        try {
            // 步骤1：NV21 → NV12（用真实尺寸）
            val nv12 = nv21ToNv12(yuv, width, height)

            // 步骤2：送入编码器
            val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(nv12)
                codec.queueInputBuffer(
                    inputIndex, 0, nv12.size,
                    System.nanoTime() / 1000, 0
                )
            }

            // 步骤3：取出 H.264 数据
            val bufferInfo = MediaCodec.BufferInfo()
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                val h264Data = ByteArray(bufferInfo.size)
                outputBuffer?.get(h264Data)
                codec.releaseOutputBuffer(outputIndex, false)
                return h264Data
            }

        } catch (e: Exception) {
            LogUtil.e(TAG, "H.264 编码异常: ${e.message}")
        }

        return null
    }

    // ==================== 解码 ====================

    /**
     * H.264 → 直接渲染到 Surface（硬件加速）
     */
    override fun decodeAndRender(encodedData: ByteArray) {
        val codec = decoder ?: return

        try {
            val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(encodedData)
                codec.queueInputBuffer(
                    inputIndex, 0, encodedData.size,
                    System.nanoTime() / 1000, 0
                )
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            while (outputIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    codec.releaseOutputBuffer(outputIndex, false)
                    break
                }
                if (!videoSizeAdjusted) {
                    val fmt = codec.outputFormat
                    val w = fmt.getInteger(MediaFormat.KEY_WIDTH)
                    val h = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    if (w > 0 && h > 0) {
                        adjustSurfaceToAspectRatio(w, h)
                        videoSizeAdjusted = true
                    }
                }
                codec.releaseOutputBuffer(outputIndex, true)
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }

        } catch (e: Exception) {
            LogUtil.e(TAG, "H.264 解码异常: ${e.message}")
        }
    }

    // ==================== 资源释放 ====================

    override fun release() {
        encoder?.let {
            try {
                it.stop()
                it.release()
                LogUtil.d(TAG, "H.264 编码器已释放")
            } catch (e: Exception) {
                LogUtil.e(TAG, "释放编码器失败: ${e.message}")
            }
        }
        encoder = null

        decoder?.let {
            try {
                it.stop()
                it.release()
                LogUtil.d(TAG, "H.264 解码器已释放")
            } catch (e: Exception) {
                LogUtil.e(TAG, "释放解码器失败: ${e.message}")
            }
        }
        decoder = null
    }

    // ==================== 工具方法 ====================

    /**
     * 根据视频实际宽高比调整 SurfaceView 尺寸，Center Crop 铺满全屏
     *
     * MediaCodec 会将解码后的视频缩放填满整个 Surface。
     * 让 Surface 比屏幕大（溢出部分被 FrameLayout 裁切），
     * 从而实现「铺满屏幕 + 保持宽高比 + 裁切多余部分」的效果。
     */
    private fun adjustSurfaceToAspectRatio(videoWidth: Int, videoHeight: Int) {
        remoteSurface.post {
            val parent = remoteSurface.parent as? ViewGroup ?: return@post
            val parentWidth = parent.width
            val parentHeight = parent.height
            if (parentWidth == 0 || parentHeight == 0) return@post

            val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
            val parentRatio = parentWidth.toFloat() / parentHeight.toFloat()

            val surfaceWidth: Int
            val surfaceHeight: Int

            if (videoRatio > parentRatio) {
                // 视频更宽 → 以高度为准，宽度溢出裁切左右
                surfaceHeight = parentHeight
                surfaceWidth = (parentHeight * videoRatio).toInt()
            } else {
                // 视频更高 → 以宽度为准，高度溢出裁切上下
                surfaceWidth = parentWidth
                surfaceHeight = (parentWidth / videoRatio).toInt()
            }

            val lp = FrameLayout.LayoutParams(surfaceWidth, surfaceHeight).apply {
                gravity = Gravity.CENTER
            }
            remoteSurface.layoutParams = lp
            LogUtil.d(TAG, "SurfaceView CenterCrop: ${surfaceWidth}x${surfaceHeight}（视频 ${videoWidth}x${videoHeight}）")
        }
    }

    /**
     * NV21 → NV12 格式转换
     *
     * NV21: YYYY...YYYY VUVU...VUVU （Camera 默认输出）
     * NV12: YYYY...YYYY UVUV...UVUV （H.264 编码器需要）
     *
     * 关键：用真实尺寸计算 Y 平面大小，否则 arraycopy 越界
     */
    private fun nv21ToNv12(nv21: ByteArray, width: Int, height: Int): ByteArray {
        val nv12 = ByteArray(nv21.size)
        val ySize = width * height  // ⬅️ 必须用真实尺寸

        // Y 平面直接拷贝
        System.arraycopy(nv21, 0, nv12, 0, ySize)

        // UV 平面交换顺序
        var i = ySize
        while (i < nv21.size - 1) {
            nv12[i] = nv21[i + 1]      // U
            nv12[i + 1] = nv21[i]      // V
            i += 2
        }

        return nv12
    }
}
