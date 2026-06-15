package com.dada.core.imageloader.internal

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule

/**
 * Glide 全局配置（三级缓存）。原位于 :app，迁移到 :core:imageloader 后 :app 不再直接依赖 Glide。
 *
 * 1. Active Resources — 正在使用的资源（弱引用缓存）
 * 2. Memory Cache     — 内存缓存（LRU）
 * 3. Disk Cache       — 磁盘缓存（LRU）
 */
@GlideModule
internal class DaDaGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val maxMemory = Runtime.getRuntime().maxMemory()
        val memoryCacheSize = (maxMemory / 8).toInt()
        builder.setMemoryCache(LruResourceCache(memoryCacheSize.toLong()))
        builder.setBitmapPool(LruBitmapPool(memoryCacheSize.toLong()))

        val diskCacheSize = 100L * 1024 * 1024 // 100MB
        builder.setDiskCache(
            InternalCacheDiskCacheFactory(context, "glide_cache", diskCacheSize)
        )
    }

    override fun isManifestParsingEnabled(): Boolean = false
}
