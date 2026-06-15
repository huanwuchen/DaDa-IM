package com.dada.core.common.utils

import com.google.gson.Gson

object GsonUtil {

    private val gson = Gson()

    fun toJson(obj: Any?): String = gson.toJson(obj)

    fun <T> fromJson(json: String, clazz: Class<T>): T = gson.fromJson(json, clazz)
}
