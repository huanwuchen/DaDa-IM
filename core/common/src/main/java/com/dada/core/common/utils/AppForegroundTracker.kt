package com.dada.core.common.utils

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * App 前后台状态管理
 * 配合 ProcessLifecycleOwner 使用
 *
 * 用法：
 * ProcessLifecycleOwner.get().lifecycle.addObserver(AppForegroundTracker)
 */
object AppForegroundTracker : DefaultLifecycleObserver {

    /** 当前 App 是否在前台 */
    @Volatile
    var isForeground: Boolean = false
        private set

    /** 当前正在打开的聊天对方 userId（0 表示未在任何聊天页面）*/
    @Volatile
    private var currentChatUserId: Long = 0L

    /** 是否在聊天列表页面 */
    @Volatile
    private var inChatListPage: Boolean = false

    override fun onStart(owner: LifecycleOwner) {
        isForeground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground = false
    }

    /**
     * 设置当前聊天对象（在 WxChatActivity 中调用）
     */
    fun setCurrentChat(targetUserId: Long) {
        currentChatUserId = targetUserId
    }

    /**
     * 清除当前聊天对象（聊天页关闭时调用）
     */
    fun clearCurrentChat() {
        currentChatUserId = 0L
    }

    /**
     * 获取当前聊天对象ID
     */
    fun getCurrentChatUserId(): Long = currentChatUserId

    /**
     * 设置是否在聊天列表页面
     */
    fun setInChatListPage(inChatList: Boolean) {
        inChatListPage = inChatList
    }

    /**
     * 是否在聊天列表页面
     */
    fun isInChatListPage(): Boolean = inChatListPage
}
