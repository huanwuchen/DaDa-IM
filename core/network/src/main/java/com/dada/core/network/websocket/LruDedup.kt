package com.dada.core.network.websocket

/**
 * 入站消息去重：保留最近 N 个 messageId。
 * 不要求严格的全局去重，目的是抵御服务端重发/网络重复投递。
 * 进程重启后失效（业务侧入库时建议同时做 INSERT OR IGNORE 保证持久化层去重）。
 */
class LruDedup(private val capacity: Int = 2048) {

    private val map = object : LinkedHashMap<String, Boolean>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>): Boolean = size > capacity
    }
    private val lock = Any()

    /** 首次出现返回 true；重复出现返回 false。 */
    fun putIfAbsent(id: String): Boolean = synchronized(lock) {
        if (map.containsKey(id)) false
        else { map[id] = true; true }
    }

    fun clear() = synchronized(lock) { map.clear() }
}
