package com.dada.core.common.utils

import com.tencent.mmkv.MMKV

object KvUtil {

    private val kv: MMKV by lazy { MMKV.defaultMMKV() }

    fun putString(key: String, value: String?) = kv.encode(key, value ?: "")
    fun getString(key: String, default: String = ""): String = kv.decodeString(key) ?: default

    fun putInt(key: String, value: Int) = kv.encode(key, value)
    fun getInt(key: String, default: Int = 0): Int = kv.decodeInt(key, default)

    fun putBool(key: String, value: Boolean) = kv.encode(key, value)
    fun getBool(key: String, default: Boolean = false): Boolean = kv.decodeBool(key, default)

    fun putLong(key: String, value: Long) = kv.encode(key, value)
    fun getLong(key: String, default: Long = 0L): Long = kv.decodeLong(key, default)

    fun remove(key: String) = kv.removeValueForKey(key)
    fun clear() = kv.clearAll()
}
