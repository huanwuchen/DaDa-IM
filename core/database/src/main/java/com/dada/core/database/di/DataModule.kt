package com.dada.core.database.di

import android.content.Context
import androidx.room.Room
import com.dada.core.database.AppDatabase
import com.dada.core.database.UserPreferences
import com.dada.core.database.UserPreferencesImpl
import com.dada.core.database.dao.ChatMessageDao
import com.dada.core.database.dao.ImContactDao
import com.dada.core.database.dao.ImConversationDao
import com.dada.core.database.dao.ImMessageDao
import com.dada.core.database.dao.ImUserProfileDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库 Hilt 模块。
 *
 * 3.5 重构：原位于 :app/di/DataModule.kt，下沉到 :core:database 模块。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindUserPreferences(impl: UserPreferencesImpl): UserPreferences

    companion object {

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "dada_app.db"
            )
                // 上线前规则：每次涉及表结构变更，必须新增 Migration 链。
                // 暂保留 fallbackToDestructiveMigration 仅在开发期，且加 schema 文件备份以便后续编写 Migration。
                .fallbackToDestructiveMigration()
                .build()
        }

        @Provides
        fun provideChatMessageDao(database: AppDatabase): ChatMessageDao =
            database.chatMessageDao()

        @Provides
        fun provideImContactDao(database: AppDatabase): ImContactDao =
            database.imContactDao()

        @Provides
        fun provideImConversationDao(database: AppDatabase): ImConversationDao =
            database.imConversationDao()

        @Provides
        fun provideImMessageDao(database: AppDatabase): ImMessageDao =
            database.imMessageDao()

        @Provides
        fun provideImUserProfileDao(database: AppDatabase): ImUserProfileDao =
            database.imUserProfileDao()
    }
}
