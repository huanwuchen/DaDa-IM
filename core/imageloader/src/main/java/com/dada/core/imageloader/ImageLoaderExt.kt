package com.dada.core.imageloader

import android.widget.ImageView

/**
 * UI 层调用入口。注入 [ImageLoader] 后通过此扩展函数加载图片。
 *
 * 示例：
 * ```
 * @Inject lateinit var imageLoader: ImageLoader
 *
 * imageView.loadImage(url, imageLoader) {
 *     placeholder = R.drawable.ic_avatar_default
 *     transform = ImageRequest.Transform.CenterCrop
 *     asCircle = true
 * }
 * ```
 */
inline fun ImageView.loadImage(
    data: Any?,
    imageLoader: ImageLoader,
    block: ImageRequest.Builder.() -> Unit = {},
) {
    val request = ImageRequest.Builder(data).apply(block).build()
    imageLoader.load(this, request)
}

/**
 * 清除当前 ImageView 上的请求并释放 Bitmap。Activity/Fragment 销毁、列表 ViewHolder 复用时调用。
 */
fun ImageView.clearImage(imageLoader: ImageLoader) {
    imageLoader.clear(this)
}
