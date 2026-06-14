package com.dada.core.imageloader

import android.widget.ImageView

/**
 * 图片加载抽象。UI 层只面向此接口编程，禁止直接引用具体加载库（Glide/Coil/Fresco）。
 *
 * 当前实现：[com.dada.core.imageloader.internal.GlideImageLoader]
 *
 * 替换实现只需修改 [com.dada.core.imageloader.di.ImageLoaderModule] 的 @Binds。
 */
interface ImageLoader {

    fun load(target: ImageView, request: ImageRequest)

    fun clear(target: ImageView)
}
