package com.dada.core.common.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * SharedPreferences 工具类
 *
 * 设计思路：
 * - 使用传统的 SharedPreferences 进行轻量级数据存储
 * - 采用单例模式，全局共享同一个 SharedPreferences 实例
 * - 需要在 Application 中调用 init() 方法进行初始化
 * - 提供常用的数据类型存取方法，简化使用流程
 *
 * 生命周期与初始化：
 * - 在 Application.onCreate() 中调用 SpUtils.init(this)
 * - 内部通过 lazy 延迟初始化 SharedPreferences 实例
 * - 整个应用生命周期内只创建一次
 *
 * 使用示例：
 * ```kotlin
 * // 在 Application 中初始化
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         SpUtils.init(this)
 *     }
 * }
 *
 * // 在任意位置使用
 * SpUtils.putString("username", "张三")
 * val username = SpUtils.getString("username")
 * ```
 */
object SpUtils {

    private lateinit var application: Application

    /**
     * 初始化方法（必须在 Application.onCreate() 中调用）
     *
     * @param app Application 实例
     */
    fun init(app: Application) {
        application = app
    }

    /**
     * SharedPreferences 实例（延迟初始化）
     * 使用 APPLICATION_ID 作为文件名，确保全局唯一
     */
    private val sp: SharedPreferences by lazy {
        application.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    }

    // ==================== String 类型操作 ====================

    /**
     * 保存 String 类型数据
     *
     * @param key 键名
     * @param value 值（null 时保存空字符串）
     */
    fun putString(key: String, value: String?) {
        sp.edit { putString(key, value ?: "") }
    }

    /**
     * 获取 String 类型数据
     *
     * @param key 键名
     * @param default 默认值（默认为空字符串）
     * @return 存储的值，不存在则返回默认值
     */
    fun getString(key: String, default: String = ""): String {
        return sp.getString(key, default) ?: default
    }

    // ==================== Int 类型操作 ====================

    /**
     * 保存 Int 类型数据
     *
     * @param key 键名
     * @param value 值
     */
    fun putInt(key: String, value: Int) {
        sp.edit { putInt(key, value) }
    }

    /**
     * 获取 Int 类型数据
     *
     * @param key 键名
     * @param default 默认值（默认为 0）
     * @return 存储的值，不存在则返回默认值
     */
    fun getInt(key: String, default: Int = 0): Int {
        return sp.getInt(key, default)
    }

    // ==================== Boolean 类型操作 ====================

    /**
     * 保存 Boolean 类型数据
     *
     * @param key 键名
     * @param value 值
     */
    fun putBoolean(key: String, value: Boolean) {
        sp.edit { putBoolean(key, value) }
    }

    /**
     * 获取 Boolean 类型数据
     *
     * @param key 键名
     * @param default 默认值（默认为 false）
     * @return 存储的值，不存在则返回默认值
     */
    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return sp.getBoolean(key, default)
    }

    // ==================== Long 类型操作 ====================

    /**
     * 保存 Long 类型数据
     *
     * @param key 键名
     * @param value 值
     */
    fun putLong(key: String, value: Long) {
        sp.edit { putLong(key, value) }
    }

    /**
     * 获取 Long 类型数据
     *
     * @param key 键名
     * @param default 默认值（默认为 0L）
     * @return 存储的值，不存在则返回默认值
     */
    fun getLong(key: String, default: Long = 0L): Long {
        return sp.getLong(key, default)
    }

    // ==================== Float 类型操作 ====================

    /**
     * 保存 Float 类型数据
     *
     * @param key 键名
     * @param value 值
     */
    fun putFloat(key: String, value: Float) {
        sp.edit { putFloat(key, value) }
    }

    /**
     * 获取 Float 类型数据
     *
     * @param key 键名
     * @param default 默认值（默认为 0f）
     * @return 存储的值，不存在则返回默认值
     */
    fun getFloat(key: String, default: Float = 0f): Float {
        return sp.getFloat(key, default)
    }

    // ==================== 删除与清空操作 ====================

    /**
     * 删除指定 key 的数据
     *
     * @param key 要删除的键名
     */
    fun remove(key: String) {
        sp.edit { remove(key) }
    }

    /**
     * 清空所有数据
     *
     * 注意：此操作会删除所有通过 SpUtils 保存的数据，请谨慎使用
     */
    fun clear() {
        sp.edit { clear() }
    }
}