package com.dada.core.imageloader.di

import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.internal.GlideImageLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageLoaderModule {

    @Binds
    @Singleton
    abstract fun bindImageLoader(impl: GlideImageLoader): ImageLoader
}
