package com.dada.app.network.call.video.capture

/**
 * YUV (NV21) 数据旋转工具
 *
 * 【背景】
 * 摄像头传感器是横向的（landscape）：
 * - 前置摄像头：通常需要旋转 270° (或者说 -90°) + 水平镜像
 * - 后置摄像头：通常需要旋转 90°
 *
 * CameraView 给的 YUV 数据是传感器原始方向（横向），如果手机竖屏拿，
 * 直接传给编码器，对方收到的画面就是横躺着的。
 *
 * 【NV21 格式回顾】
 * - Y 平面：W*H 字节（每个像素 1 字节亮度）
 * - VU 平面：W*H/2 字节（每 2x2 像素共享一组 VU，交错排列）
 *
 * 【实现策略】
 * 旋转 90° / 180° / 270° 三种情况各自实现
 * 旋转后宽高互换（90° 和 270° 时）
 */
object YuvRotator {

    /**
     * 旋转 NV21 数据
     *
     * @param nv21 原始 NV21 数据
     * @param width 原始宽度
     * @param height 原始高度
     * @param rotation 旋转角度（必须是 0/90/180/270）
     * @return 旋转后的 NV21 数据（注意：90/270 时宽高已互换）
     */
    fun rotate(nv21: ByteArray, width: Int, height: Int, rotation: Int): ByteArray {
        return when (rotation) {
            0 -> nv21
            90 -> rotate90(nv21, width, height)
            180 -> rotate180(nv21, width, height)
            270 -> rotate270(nv21, width, height)
            else -> nv21
        }
    }

    /**
     * 旋转 90°（顺时针）
     * 原 (w, h) → 新 (h, w)
     */
    private fun rotate90(nv21: ByteArray, w: Int, h: Int): ByteArray {
        val output = ByteArray(nv21.size)
        val frameSize = w * h

        // Y 平面旋转
        var i = 0
        for (x in 0 until w) {
            for (y in h - 1 downTo 0) {
                output[i++] = nv21[y * w + x]
            }
        }

        // UV 平面旋转（NV21 的 VU 是交错的）
        i = frameSize
        var x = 0
        while (x < w) {
            var y = h / 2 - 1
            while (y >= 0) {
                output[i++] = nv21[frameSize + y * w + x]      // V
                output[i++] = nv21[frameSize + y * w + x + 1]  // U
                y--
            }
            x += 2
        }

        return output
    }

    /**
     * 旋转 180°
     * 宽高不变
     */
    private fun rotate180(nv21: ByteArray, w: Int, h: Int): ByteArray {
        val output = ByteArray(nv21.size)
        val frameSize = w * h

        // Y 平面反转
        var i = 0
        for (j in frameSize - 1 downTo 0) {
            output[i++] = nv21[j]
        }

        // UV 平面反转（保持 VU 对的顺序）
        i = frameSize
        var j = nv21.size - 2
        while (j >= frameSize) {
            output[i++] = nv21[j]
            output[i++] = nv21[j + 1]
            j -= 2
        }

        return output
    }

    /**
     * 旋转 270°（逆时针 90°）
     * 原 (w, h) → 新 (h, w)
     *
     * 前置摄像头常用此方向（手机竖屏时画面正向显示）
     */
    private fun rotate270(nv21: ByteArray, w: Int, h: Int): ByteArray {
        val output = ByteArray(nv21.size)
        val frameSize = w * h

        // Y 平面旋转
        var i = 0
        for (x in w - 1 downTo 0) {
            for (y in 0 until h) {
                output[i++] = nv21[y * w + x]
            }
        }

        // UV 平面旋转
        i = frameSize
        var x = w - 2
        while (x >= 0) {
            for (y in 0 until h / 2) {
                output[i++] = nv21[frameSize + y * w + x]      // V
                output[i++] = nv21[frameSize + y * w + x + 1]  // U
            }
            x -= 2
        }

        return output
    }

    /**
     * 水平镜像翻转 NV21 数据
     *
     * 前置摄像头传感器输出的画面是镜像的，
     * 本地预览由 CameraView 自动镜像（看起来自然），
     * 但发送给对方的原始帧数据需要手动镜像，否则对方看到的是反的。
     *
     * @param nv21 原始 NV21 数据
     * @param width 宽度
     * @param height 高度
     * @return 水平镜像后的 NV21 数据
     */
    fun mirror(nv21: ByteArray, width: Int, height: Int): ByteArray {
        val output = ByteArray(nv21.size)
        val frameSize = width * height

        // Y 平面：每行左右翻转
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                output[rowOffset + x] = nv21[rowOffset + (width - 1 - x)]
            }
        }

        // UV 平面：每行 VU 对左右翻转
        val uvHeight = height / 2
        for (y in 0 until uvHeight) {
            val uvRowOffset = frameSize + y * width
            for (x in 0 until width step 2) {
                val mirrorX = width - 2 - x
                output[uvRowOffset + x] = nv21[uvRowOffset + mirrorX]         // V
                output[uvRowOffset + x + 1] = nv21[uvRowOffset + mirrorX + 1] // U
            }
        }

        return output
    }
}
