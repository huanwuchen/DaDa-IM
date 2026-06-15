package com.dada.core.common

object Constants {

    // ============================== 网络超时 ==============================
    const val PAGE_SIZE = 20
    const val CONNECT_TIMEOUT = 15L
    const val READ_TIMEOUT = 15L
    const val WRITE_TIMEOUT = 15L

    // ============================== 服务端地址 ==============================

    /** HTTP / 静态资源 Host */
    var BASE_URL: String = "http://your-server-ip:8080"
        private set

    /** WebSocket Host */
    var WS_URL: String = "ws://your-server-ip:8080"
        private set

    private var initialized = false

    /**
     * 初始化服务端地址。
     * 在 Application.onCreate() 中调用，从 BuildConfig 传入编译期配置。
     */
    fun init(baseUrl: String, wsUrl: String) {
        if (initialized) return
        initialized = true
        if (baseUrl.isNotBlank()) BASE_URL = baseUrl
        if (wsUrl.isNotBlank()) WS_URL = wsUrl
    }

    /**
     * 将相对路径拼接为完整 URL
     *
     * - 已经是完整 HTTP URL → 原样返回
     * - 相对路径（/uploads/...） → 拼接 [BASE_URL]
     * - null / 空 → 返回 null
     */
    fun resolveUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return BASE_URL + if (path.startsWith("/")) path else "/$path"
    }
}
