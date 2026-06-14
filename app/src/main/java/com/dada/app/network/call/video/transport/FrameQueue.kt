package com.dada.app.network.call.video.transport

/**
 * 线程安全的帧队列（生产者-消费者模式）
 *
 * 【作用】
 * 在不同线程间传递数据
 * - 生产者通过 offer() 放入数据
 * - 消费者通过 take() 取出数据
 *
 * 【为什么不用 BlockingQueue？】
 * 这里需要"队列满时丢弃旧数据"的语义（保证实时性）
 * BlockingQueue 会阻塞生产者，不适合实时视频
 *
 * @param T 数据类型（YuvFrame 或 ByteArray）
 * @param maxSize 队列最大长度
 */
class FrameQueue<T>(private val maxSize: Int) {

    private val queue = mutableListOf<T>()
    private val lock = Object()

    /**
     * 放入数据（非阻塞）
     * 如果队列已满，会丢弃这一帧（保证实时性）
     *
     * @return 是否成功放入
     */
    fun offer(item: T): Boolean {
        synchronized(lock) {
            if (queue.size < maxSize) {
                queue.add(item)
                lock.notifyAll()  // 唤醒等待的消费者
                return true
            }
            return false  // 队列已满，丢弃
        }
    }

    /**
     * 取出数据（阻塞，等待数据或被中断）
     *
     * @param timeoutMs 最长等待时间（毫秒）
     * @return 取出的数据，超时返回 null
     */
    fun take(timeoutMs: Long = 100): T? {
        synchronized(lock) {
            if (queue.isEmpty()) {
                lock.wait(timeoutMs)
            }
            return if (queue.isEmpty()) null else queue.removeAt(0)
        }
    }

    /**
     * 唤醒所有等待的消费者（用于停止时退出阻塞）
     */
    fun notifyWaiting() {
        synchronized(lock) {
            lock.notifyAll()
        }
    }

    /**
     * 清空队列
     */
    fun clear() {
        synchronized(lock) {
            queue.clear()
        }
    }

    /**
     * 当前队列大小
     */
    fun size(): Int {
        synchronized(lock) {
            return queue.size
        }
    }
}
