package com.dada.core.network.di

import com.dada.core.network.api.ImApiService
import com.dada.core.network.api.MomentApiService
import com.dada.core.network.api.UserApiService
import com.dada.core.network.websocket.MessageManager
import com.dada.core.network.websocket.MessageStore
import com.dada.core.network.websocket.RetryPolicy
import com.dada.core.network.websocket.WebSocketManager
import com.dada.core.database.UserPreferences
import com.dada.core.common.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 网络层 Hilt 模块。
 *
 * 3.5 重构：原位于 :app/di/NetworkModule.kt，下沉到 :core:network 模块，
 * 使 feature 模块可独立使用网络栈而无需依赖 :app。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(userPreferences: UserPreferences): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()
            val token = userPreferences.getToken()
            val request = if (token.isNotBlank()) {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    @Named("default")
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @Named("default")
    fun provideRetrofit(@Named("default") okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(com.dada.core.network.BuildConfig.SERVER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideImApiService(@Named("default") retrofit: Retrofit): ImApiService {
        return retrofit.create(ImApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideMomentApiService(@Named("default") retrofit: Retrofit): MomentApiService {
        return retrofit.create(MomentApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideUserApiService(@Named("default") retrofit: Retrofit): UserApiService {
        return retrofit.create(UserApiService::class.java)
    }

    /**
     * MessageStore 默认提供 Noop 实现，便于先跑通。
     * app 模块如已落 Room 表，可在 app 自己的 @Module 里 @Binds 覆盖。
     */
    @Provides
    @Singleton
    fun provideMessageStore(): MessageStore = MessageStore.Noop

    @Provides
    @Singleton
    fun provideMessageManager(
        wsManager: WebSocketManager,
        store: MessageStore,
    ): MessageManager = MessageManager(
        conn = wsManager,
        store = store,
        retryPolicy = RetryPolicy(maxAttempts = 5),
    )
}
