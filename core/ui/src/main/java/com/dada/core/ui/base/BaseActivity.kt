package com.dada.core.ui.base

import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.dada.core.common.base.BaseViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Activity 基类
 *
 * 标准 MVVM 接入步骤：
 *  1. 子类指定泛型 VB 并实现 [inflateBinding] / [initView] / [initData]
 *  2. 在 [initData] 中调用 [observe] 来收集 ViewModel 的 Flow
 *  3. 如需绑定 BaseViewModel 的通用 loading/error/toast，调用 [bindBaseViewModel]
 *
 * 3.x 重构：从 :core:common 迁入 :core:ui，因 UI 基类不属于 common 范畴。
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    lateinit var binding: VB

    abstract fun inflateBinding(): VB
    abstract fun initView()
    abstract fun initData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = inflateBinding()
        setContentView(binding.root)
        initView()
        initData()
    }

    // ============================== Flow 订阅辅助 ==============================

    protected inline fun <T> observe(
        flow: SharedFlow<T>,
        crossinline onEach: suspend (T) -> Unit,
    ) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect { onEach(it) }
            }
        }
    }

    protected inline fun <T> observe(
        flow: StateFlow<T>,
        crossinline onEach: suspend (T) -> Unit,
    ) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect { onEach(it) }
            }
        }
    }

    /**
     * 一键绑定 [BaseViewModel] 的通用副作用：
     *  - errorEvent / toastEvent -> 弹 Toast
     *  - 子类可在 onLoadingChanged 中自定义 loading UI
     */
    protected fun bindBaseViewModel(
        viewModel: BaseViewModel,
        onLoadingChanged: ((Boolean) -> Unit)? = null,
    ) {
        observe(viewModel.errorEvent) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        observe(viewModel.toastEvent) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        if (onLoadingChanged != null) {
            observe(viewModel.isLoading) { onLoadingChanged(it) }
        }
    }




}
