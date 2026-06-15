package com.dada.core.common.utils

import android.util.Log

object LogUtil {

    /**
     * 默认关闭，由 Application 在 onCreate 中根据 BuildConfig.DEBUG 显式打开。
     * 默认值为 false 可确保 release 构建即使遗漏初始化也不会泄露日志。
     */
    var enabled: Boolean = false

    fun d(tag: String, msg: String) {
        if (enabled) Log.d(tag, msg)
    }

    fun e(tag: String, msg: String) {
        if (enabled) Log.e(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        if (enabled) Log.e(tag, msg, tr)
    }

    fun w(tag: String, msg: String) {
        if (enabled) Log.w(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (enabled) Log.i(tag, msg)
    }
}
