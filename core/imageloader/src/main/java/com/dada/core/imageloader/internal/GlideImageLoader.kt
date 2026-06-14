package com.dada.core.imageloader.internal

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 唯一允许 import com.bumptech.glide 的位置。
 */
@Singleton
class GlideImageLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImageLoader {

    override fun load(target: ImageView, request: ImageRequest) {
        val glideRequest: RequestBuilder<*> = Glide.with(target).load(request.data)
        val options = buildOptions(request)
        glideRequest.apply(options).into(target)
    }

    override fun clear(target: ImageView) {
        Glide.with(target).clear(target)
    }

    private fun buildOptions(request: ImageRequest): RequestOptions {
        var options = RequestOptions()

        request.placeholder?.let { options = options.placeholder(it) }
        request.error?.let { options = options.error(it) }

        // 变换：圆形/圆角与 transform 互斥时优先 asCircle > cornerRadiusPx > transform
        options = when {
            request.asCircle -> options.transform(CircleCrop())
            request.cornerRadiusPx > 0 -> when (request.transform) {
                ImageRequest.Transform.CenterCrop ->
                    options.transform(CenterCrop(), RoundedCorners(request.cornerRadiusPx))
                ImageRequest.Transform.FitCenter ->
                    options.transform(FitCenter(), RoundedCorners(request.cornerRadiusPx))
                ImageRequest.Transform.CenterInside ->
                    options.transform(CenterInside(), RoundedCorners(request.cornerRadiusPx))
                ImageRequest.Transform.None ->
                    options.transform(RoundedCorners(request.cornerRadiusPx))
            }
            else -> when (request.transform) {
                ImageRequest.Transform.CenterCrop -> options.centerCrop()
                ImageRequest.Transform.FitCenter -> options.fitCenter()
                ImageRequest.Transform.CenterInside -> options.centerInside()
                ImageRequest.Transform.None -> options
            }
        }

        if (request.skipMemoryCache) {
            options = options.skipMemoryCache(true)
        }
        if (request.skipDiskCache) {
            options = options.diskCacheStrategy(DiskCacheStrategy.NONE)
        }
        if (request.overrideWidth > 0 && request.overrideHeight > 0) {
            options = options.override(request.overrideWidth, request.overrideHeight)
        }

        return options
    }
}
