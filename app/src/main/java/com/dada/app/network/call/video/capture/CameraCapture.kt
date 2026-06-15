package com.dada.app.network.call.video.capture

import com.dada.core.common.utils.LogUtil
import androidx.lifecycle.LifecycleOwner
import com.dada.app.network.call.video.VideoEngineConfig
import com.otaliastudios.cameraview.CameraView
import com.otaliastudios.cameraview.controls.Facing
import com.otaliastudios.cameraview.controls.Mode
import com.otaliastudios.cameraview.frame.Frame
import com.otaliastudios.cameraview.frame.FrameProcessor
import com.otaliastudios.cameraview.size.SizeSelectors

/**
 * 摄像头采集封装
 *
 * 【职责】
 * 1. 配置 CameraView
 * 2. 监听摄像头帧数据
 * 3. 通过回调输出 YUV 数据 + 真实尺寸
 *
 * 【为什么回调要带尺寸？】
 * CameraView 不一定能输出我们指定的目标尺寸（受设备硬件限制）
 * 例如：你要 1920×1080，但设备前置摄像头最大只支持 640×480
 * 此时 CameraView 会自动 fallback 到 640×480
 * → 下游编码器必须用真实尺寸 640×480 才能正确处理数据，否则数据错乱黑屏
 *
 * @param cameraView CameraView 对象
 * @param lifecycleOwner 生命周期（用于自动管理摄像头）
 * @param onYuvFrame YUV 数据回调（参数：yuv 数据, 宽度, 高度）
 */
class CameraCapture(
    private val cameraView: CameraView,
    private val lifecycleOwner: LifecycleOwner,
    private val onYuvFrame: (yuv: ByteArray, width: Int, height: Int) -> Unit
) {
    companion object {
        private const val TAG = "CameraCapture"
    }

    private var isRunning = false

    // 当前帧的真实尺寸（CameraView 实际输出的）
    @Volatile var actualWidth: Int = VideoEngineConfig.WIDTH
        private set
    @Volatile var actualHeight: Int = VideoEngineConfig.HEIGHT
        private set

    /**
     * 启动摄像头采集
     */
    fun start() {
        if (isRunning) return

        try {
            // ========== 步骤1：绑定生命周期 ==========
            cameraView.setLifecycleOwner(lifecycleOwner)

            // ========== 步骤2：配置摄像头参数 ==========
            cameraView.facing = Facing.FRONT       // 前置摄像头

            // 尝试让预览流选择接近目标分辨率
            // 注意：仅"提示"，CameraView 会从设备支持的尺寸里挑最接近的
            cameraView.setPreviewStreamSize(
                SizeSelectors.or(
                    SizeSelectors.and(
                        SizeSelectors.minWidth(VideoEngineConfig.WIDTH),
                        SizeSelectors.minHeight(VideoEngineConfig.HEIGHT),
                        SizeSelectors.smallest()
                    ),
                    SizeSelectors.biggest()  // fallback：用设备支持的最大尺寸
                )
            )

            // Mode.PICTURE = 流模式（支持 FrameProcessor 获取 YUV 帧）
            // Mode.VIDEO = 录像模式（只能录到文件，不能实时获取流）
            cameraView.mode = Mode.PICTURE

            // ========== 步骤3：设置帧处理器 ==========
            cameraView.addFrameProcessor(object : FrameProcessor {
                private var loggedFirstFrame = false

                override fun process(frame: Frame) {
                    if (!isRunning) return

                    // 从 frame 拿真实尺寸（CameraView 实际输出的）
                    val size = frame.size ?: return
                    val origW = size.width
                    val origH = size.height
                    val origYuv: ByteArray = frame.getData()

                    // ⚠️ 关键：摄像头传感器是横向的，需要旋转才能正确显示
                    // frame.rotationToView = 需要旋转多少度才能让画面在 View 上正向显示
                    // 必须把 YUV 旋转后再编码，对方才能看到正向画面
                    val rotation = frame.rotationToView

                    val rotatedYuv: ByteArray
                    val finalW: Int
                    val finalH: Int

                    if (rotation == 90 || rotation == 270) {
                        // 90° 或 270° 旋转，宽高互换
                        rotatedYuv = YuvRotator.rotate(origYuv, origW, origH, rotation)
                        finalW = origH
                        finalH = origW
                    } else if (rotation == 180) {
                        rotatedYuv = YuvRotator.rotate(origYuv, origW, origH, 180)
                        finalW = origW
                        finalH = origH
                    } else {
                        rotatedYuv = origYuv
                        finalW = origW
                        finalH = origH
                    }

                    // 前置摄像头需要水平镜像（CameraView 预览自动镜像，但原始帧不会）
                    val isFrontCamera = cameraView.facing == Facing.FRONT
                    val outputYuv = if (isFrontCamera) {
                        YuvRotator.mirror(rotatedYuv, finalW, finalH)
                    } else {
                        rotatedYuv
                    }

                    // 更新当前真实尺寸（旋转后的）
                    actualWidth = finalW
                    actualHeight = finalH

                    // 第一帧打印诊断信息
                    if (!loggedFirstFrame) {
                        loggedFirstFrame = true
                        LogUtil.d(TAG, "首帧诊断: 原始=${origW}x${origH}, 旋转角度=$rotation°, " +
                                "旋转后=${finalW}x${finalH}, 前置镜像=$isFrontCamera, " +
                                "原 YUV 字节数=${origYuv.size}, 输出字节数=${outputYuv.size}")
                    }

                    // 把处理后的 YUV + 新尺寸传出去
                    onYuvFrame(outputYuv, finalW, finalH)
                }
            })

            isRunning = true
            LogUtil.d(TAG, "摄像头采集已启动")

        } catch (e: Exception) {
            LogUtil.e(TAG, "启动摄像头采集失败: ${e.message}", e)
        }
    }

    /**
     * 停止摄像头采集
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        cameraView.close()
        LogUtil.d(TAG, "摄像头采集已停止")
    }

    /**
     * 切换前置/后置摄像头
     */
    fun switchCamera() {
        cameraView.toggleFacing()
        LogUtil.d(TAG, "摄像头已切换")
    }
}
