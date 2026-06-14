package com.dada.core.common.utils

import android.util.Log

object LogUtil {

    var enabled: Boolean = true

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
