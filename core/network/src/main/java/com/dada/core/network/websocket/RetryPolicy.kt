package com.dada.core.network.websocket

import kotlin.random.Random

/**
 * 指数退避 + 抖动的重试策略。无状态、线程安全。
 *
 * @param maxAttempts 0 起算，达到时 [shouldGiveUp] 返回 true
 * @param baseMs      第 0 次的基础延迟
 * @param maxMs       单次延迟上限
 * @param jitterRatio 抖动比例，±jitterRatio * exp，避免雪崩
 */
class RetryPolicy(
    val maxAttempts: Int = 5,
    private val baseMs: Long = 1_000,
    private val maxMs: Long = 30_000,
    private val jitterRatio: Double = 0.2,
) {
    fun nextDelay(attempt: Int): Long {
        val shift = attempt.coerceIn(0, 10)
        val exp = (baseMs shl shift).coerceAtMost(maxMs)
        val jitter = (exp * jitterRatio * (Random.nextDouble() - 0.5) * 2).toLong()
        return (exp + jitter).coerceAtLeast(baseMs)
    }

    fun shouldGiveUp(attempt: Int): Boolean = attempt >= maxAttempts
}
