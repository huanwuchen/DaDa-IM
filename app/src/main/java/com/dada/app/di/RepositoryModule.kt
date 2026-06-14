package com.dada.app.di

import com.dada.app.data.repository.AiChatRepositoryImpl
import com.dada.app.data.repository.FriendRepositoryImpl
import com.dada.app.data.repository.ImChatRepositoryImpl
import com.dada.app.data.repository.ImContactRepositoryImpl
import com.dada.app.data.repository.ImUserRepositoryImpl
import com.dada.app.data.repository.MomentRepositoryImpl
import com.dada.app.data.repository.UserProfileRepositoryImpl
import com.dada.domain.aichat.repository.AiChatRepository
import com.dada.domain.chat.repository.ImChatRepository
import com.dada.domain.contact.repository.ImContactRepository
import com.dada.domain.friend.repository.FriendRepository
import com.dada.domain.moment.repository.MomentRepository
import com.dada.domain.user.repository.ImUserRepository
import com.dada.domain.user.repository.UserProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository 接口 ↔ 实现的绑定
 *
 * 接口位于 :domain，实现位于 :app/data/repository。
 * ViewModel / UseCase 只依赖接口，做到面向接口编程（DIP）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindImChatRepository(impl: ImChatRepositoryImpl): ImChatRepository

    @Binds
    @Singleton
    abstract fun bindImUserRepository(impl: ImUserRepositoryImpl): ImUserRepository

    @Binds
    @Singleton
    abstract fun bindImContactRepository(impl: ImContactRepositoryImpl): ImContactRepository

    @Binds
    @Singleton
    abstract fun bindMomentRepository(impl: MomentRepositoryImpl): MomentRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(impl: UserProfileRepositoryImpl): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindFriendRepository(impl: FriendRepositoryImpl): FriendRepository

    @Binds
    @Singleton
    abstract fun bindAiChatRepository(impl: AiChatRepositoryImpl): AiChatRepository
}
