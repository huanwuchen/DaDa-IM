package com.dada.core.network.websocket

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.util.concurrent.ConcurrentHashMap

/**
 * 维护 messageId → CompletableDeferred 的等待表。
 * - register(id) 多次返回同一个 Deferred（用于重试期间复用）
 * - complete(id) 收到对端 ack 时调用
 * - cancel(id)   超时/失败时取消等待
 *
 * 线程安全：基于 ConcurrentHashMap。
 */
class AckManager {

    private val waiters = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    /** 注册或返回已有的等待者。 */
    fun register(messageId: String): Deferred<Unit> =
        waiters.computeIfAbsent(messageId) { CompletableDeferred() }

    /** 收到 ack。若未注册（已超时清理）则忽略。 */
    fun complete(messageId: String) {
        waiters.remove(messageId)?.complete(Unit)
    }

    fun cancel(messageId: String, cause: Throwable) {
        waiters.remove(messageId)?.completeExceptionally(cause)
    }

    /** 连接彻底失败、Manager 停止时调用。 */
    fun cancelAll(cause: Throwable) {
        val snapshot = waiters.keys.toList()
        snapshot.forEach { cancel(it, cause) }
    }

    fun pendingCount(): Int = waiters.size
}
