package com.dada.core.common.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 通用 ViewModel 基类
 *
 * 提供：
 *  - 简化的协程入口 [launch] / [launchIO]
 *  - 通用的 loading 状态：[isLoading]
 *  - 一次性错误事件：[errorEvent]（SharedFlow，避免 LiveData 粘性问题）
 *  - 一次性 Toast 事件：[toastEvent]
 *
 * 业务子类可在 [launch] 块里调用 [showLoading] / [hideLoading] / [postError] / [postToast]
 * 也可以使用 [runAsync] 自动包装 loading + 错误处理
 */
abstract class BaseViewModel : ViewModel() {

    // ============================== 通用状态 ==============================

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // ============================== 协程辅助 ==============================

    /**
     * 在 viewModelScope 中启动一个协程
     */
    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch { block() }

    /**
     * 在 IO 调度器中启动协程（适合数据库 / 网络 / 文件等阻塞工作）
     */
    protected fun launchIO(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(Dispatchers.IO) { block() }

    /**
     * 自动管理 loading 状态 + try/catch 的工具方法
     *
     * @param showLoading 是否在执行期间触发 loading 状态
     * @param onError     可选的错误处理；为 null 时使用默认（[postError]）
     */
    protected fun runAsync(
        showLoading: Boolean = true,
        onError: (suspend (Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = launch {
        if (showLoading) showLoading()
        try {
            block()
        } catch (e: Exception) {
            if (onError != null) onError(e) else postError(e.message ?: "未知错误")
        } finally {
            if (showLoading) hideLoading()
        }
    }

    /**
     * 切到主线程执行
     */
    protected suspend fun <T> onMain(block: suspend () -> T): T =
        withContext(Dispatchers.Main) { block() }

    // ============================== 状态/事件触发 ==============================

    protected fun showLoading() {
        _isLoading.value = true
    }

    protected fun hideLoading() {
        _isLoading.value = false
    }

    protected fun postError(message: String) {
        _errorEvent.tryEmit(message)
    }

    protected fun postToast(message: String) {
        _toastEvent.tryEmit(message)
    }
}
