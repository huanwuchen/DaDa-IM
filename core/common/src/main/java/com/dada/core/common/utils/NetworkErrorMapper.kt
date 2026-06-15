package com.dada.core.common.utils

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 将网络/系统异常映射为可直接展示给用户的中文文案，
 * 避免把 raw 异常 message（如 "Failed to connect to /192.168.1.1:8080"）
 * 暴露到 UI，也作为协程未捕获异常的兜底文案来源。
 */
object NetworkErrorMapper {

    /**
     * @return 用户可读文案。永远不返回 null/空串。
     */
    fun toMessage(throwable: Throwable?): String = when (throwable) {
        null -> "未知错误"
        is ConnectException -> "无法连接服务器，请稍后重试"
        is UnknownHostException -> "网络不可用，请检查网络连接"
        is SocketTimeoutException -> "连接超时，请稍后重试"
        is SSLException -> "安全连接异常，请稍后重试"
        is IOException -> "网络异常，请稍后重试"
        else -> throwable.message?.takeIf { it.isNotBlank() } ?: "系统异常"
    }
}
