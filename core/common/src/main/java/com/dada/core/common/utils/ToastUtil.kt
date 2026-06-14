package com.dada.core.common.utils

import android.app.Application
import android.widget.Toast

object ToastUtil {

    private lateinit var app: Application

    fun init(application: Application) {
        app = application
    }

    private var toast: Toast? = null

    fun show(msg: String) {
        toast?.cancel()
        toast = Toast.makeText(app, msg, Toast.LENGTH_SHORT).apply { show() }
    }

    fun showLong(msg: String) {
        toast?.cancel()
        toast = Toast.makeText(app, msg, Toast.LENGTH_LONG).apply { show() }
    }
}
