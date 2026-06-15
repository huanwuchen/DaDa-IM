package com.dada.app.network.call.video.codec

import android.view.SurfaceView
import com.dada.app.network.call.video.EncodingMode

/**
 * 编解码器工厂
 *
 * 【作用】
 * 根据编码模式创建对应的编解码器，对外屏蔽具体实现
 */
object VideoCodecFactory {

    /**
     * 创建编解码器
     *
     * @param mode 编码模式
     * @param remoteSurface 显示视频的 SurfaceView
     */
    fun create(mode: EncodingMode, remoteSurface: SurfaceView): VideoCodec {
        return when (mode) {
            EncodingMode.JPEG -> JpegCodec(remoteSurface)
            EncodingMode.H264 -> H264Codec(remoteSurface)
        }
    }
}
