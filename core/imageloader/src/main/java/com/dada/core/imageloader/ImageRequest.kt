package com.dada.core.imageloader

import androidx.annotation.DrawableRes

/**
 * 图片加载请求参数。
 *
 * [data] 接受：
 *  - String URL
 *  - android.net.Uri
 *  - java.io.File
 *  - @DrawableRes Int
 *  - ByteArray
 *  - null（表示清空 / 显示占位图）
 */
data class ImageRequest(
    val data: Any?,
    @DrawableRes val placeholder: Int? = null,
    @DrawableRes val error: Int? = null,
    val transform: Transform = Transform.None,
    val asCircle: Boolean = false,
    val cornerRadiusPx: Int = 0,
    val skipMemoryCache: Boolean = false,
    val skipDiskCache: Boolean = false,
    val overrideWidth: Int = SIZE_ORIGINAL,
    val overrideHeight: Int = SIZE_ORIGINAL,
) {

    enum class Transform { None, CenterCrop, FitCenter, CenterInside }

    class Builder(var data: Any?) {
        @DrawableRes var placeholder: Int? = null
        @DrawableRes var error: Int? = null
        var transform: Transform = Transform.None
        var asCircle: Boolean = false
        var cornerRadiusPx: Int = 0
        var skipMemoryCache: Boolean = false
        var skipDiskCache: Boolean = false
        var overrideWidth: Int = SIZE_ORIGINAL
        var overrideHeight: Int = SIZE_ORIGINAL

        fun build(): ImageRequest = ImageRequest(
            data = data,
            placeholder = placeholder,
            error = error,
            transform = transform,
            asCircle = asCircle,
            cornerRadiusPx = cornerRadiusPx,
            skipMemoryCache = skipMemoryCache,
            skipDiskCache = skipDiskCache,
            overrideWidth = overrideWidth,
            overrideHeight = overrideHeight,
        )
    }

    companion object {
        const val SIZE_ORIGINAL: Int = -1
    }
}
