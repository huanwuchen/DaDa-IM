package com.dada.core.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.dada.core.common.base.BaseViewModel
import com.dada.core.common.utils.LogUtil
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Fragment 基类 - 支持懒加载机制
 *
 * 【核心设计思路】
 * ==============
 * 在 ViewPager 或 TabLayout + Fragment 的场景中，系统默认会预加载相邻的 Fragment（offscreenPageLimit）。
 * 如果所有 Fragment 都在 onCreateView/onViewCreated 时立即加载数据，会导致：
 *   1. 多个 Fragment 同时发起网络请求，造成资源浪费
 *   2. 用户看不到的页面也在消耗流量和 CPU
 *   3. 首屏加载变慢，影响用户体验
 *
 * 【懒加载解决方案】
 * ==================
 * 只有当 Fragment 真正对用户可见时，才执行首次数据加载（onLazyInit）。
 * 后续每次可见/不可见时，可执行相应操作（onVisible/onInvisible）。
 *
 * 【生命周期触发时机】
 * ===================
 * - onLazyInit()  : Fragment 首次对用户可见时调用，整个生命周期只执行一次
 *                   适合：网络请求、数据库查询等耗时操作
 * 
 * - onVisible()   : 每次 Fragment 从不可见变为可见时调用
 *                   适合：刷新数据、恢复动画、重新开始视频播放等
 * 
 * - onInvisible() : 每次 Fragment 从可见变为不可见时调用
 *                   适合：暂停动画、停止视频播放、保存临时状态等
 *
 * 【实现原理】
 * ===========
 * 通过以下三个回调的组合来判断 Fragment 是否对用户可见：
 *   1. onResume/onPause     - Activity 前后台切换
 *   2. onHiddenChanged      - Fragment show/hide 切换（如 MainActivity 的 Tab 切换）
 *   3. isHidden             - 检查当前是否被隐藏
 *
 * 使用两个标志位：
 *   - isVisibleToUser  : 当前是否对用户可见
 *   - isFirstVisible   : 是否是首次可见（用于控制 onLazyInit 只执行一次）
 *
 * 3.x 重构：从 :core:common 迁入 :core:ui。
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null
    val binding: VB get() = _binding!!

    // ============================== 懒加载相关状态 ==============================
        
    /**
     * 标记 Fragment 视图是否已创建
     * 确保在视图创建完成后才执行懒加载逻辑
     */
    private var isViewCreated = false
        
    /**
     * 标记是否是首次对用户可见
     * true  = 还未首次可见，下次可见时需要执行 onLazyInit()
     * false = 已经首次可见过，onLazyInit() 已执行，不会再执行
     */
    private var isFirstVisible = true
        
    /**
     * 标记当前是否对用户可见
     * 由 onResume/onPause/onHiddenChanged 共同维护
     */
    private var isVisibleToUser = false

    abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB
    abstract fun initView()
    abstract fun initData()


    private val logTag: String get() = "BaseFragment[${javaClass.simpleName}@${hashCode().toString(16)}]"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        LogUtil.d(logTag, "onCreateView")
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogUtil.d(logTag, "onViewCreated -> initView/initData")
        
        // 标记视图已创建完成
        isViewCreated = true
        
        // 初始化视图组件（按钮点击事件、RecyclerView 配置等）
        initView()
        
        // 初始化数据（轻量级数据，不涉及网络请求等耗时操作）
        // 注意：耗时的网络请求应该放在 onLazyInit() 中
        initData()
    }


    // ============================== 懒加载回调方法 ==============================
    
    /**
     * 懒加载初始化 - 首次可见时调用，整个生命周期只执行一次
     * 
     * 【调用时机】
     * Fragment 首次从「不可见」变为「可见」时自动调用
     * 
     * 【适用场景】
     * - 发起网络请求获取列表数据
     * - 从数据库加载缓存数据
     * - 其他耗时的一次性初始化操作
     * 
     * 【注意事项】
     * - 此时 binding 一定不为 null（因为 isViewCreated = true）
     * - 只会执行一次，即使 Fragment 销毁重建后会重置
     * 
     * 示例：
     * ```kotlin
     * override fun onLazyInit() {
     *     viewModel.loadChatList()  // 首次可见时才加载聊天列表
     * }
     * ```
     */
    protected open fun onLazyInit() {}
    
    /**
     * 可见回调 - 每次 Fragment 变为可见时调用
     * 
     * 【调用时机】
     * Fragment 从「不可见」变为「可见」时调用（包括首次可见）
     * 
     * 【适用场景】
     * - 刷新数据（如通讯录下拉刷新）
     * - 恢复动画或视频播放
     * - 重新开始定位更新
     * - 统计页面曝光
     * 
     * 示例：
     * ```kotlin
     * override fun onVisible() {
     *     viewModel.refreshContacts()  // 每次可见都刷新联系人列表
     *     startAnimation()              // 恢复动画
     * }
     * ```
     */
    protected open fun onVisible() {}
    
    /**
     * 不可见回调 - 每次 Fragment 变为不可见时调用
     * 
     * 【调用时机】
     * Fragment 从「可见」变为「不可见」时调用
     * 
     * 【适用场景】
     * - 暂停动画或视频播放
     * - 停止定位更新
     * - 保存临时状态
     * - 释放资源
     * 
     * 示例：
     * ```kotlin
     * override fun onInvisible() {
     *     pauseAnimation()       // 暂停动画
     *     stopLocationUpdate()   // 停止定位
     * }
     * ```
     */
    protected open fun onInvisible() {}

    override fun onResume() {
        super.onResume()
        // Activity 恢复时，如果 Fragment 没有被隐藏，则视为可见
        if (!isHidden) {
            notifyVisible(true)
        }
    }

    override fun onPause() {
        super.onPause()
        // Activity 暂停时，Fragment 也变为不可见
        notifyVisible(false)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Fragment show/hide 状态改变时通知可见性变化
        // 例如：MainActivity 切换 Tab 时会触发此回调
        notifyVisible(!hidden)
    }

    /**
     * 通知可见性变化的核心方法
     * 
     * 【处理逻辑】
     * 1. 避免重复通知：如果可见状态没有变化，直接返回
     * 2. 变为可见时：
     *    - 如果是首次可见，执行 onLazyInit()（只执行一次）
     *    - 每次都执行 onVisible()
     * 3. 变为不可见时：
     *    - 执行 onInvisible()
     * 
     * @param visible true = 对用户可见，false = 对用户不可见
     */
    private fun notifyVisible(visible: Boolean) {
        // 防止重复通知（状态未变化时不处理）
        if (visible == isVisibleToUser) return
        
        // 如果视图还没创建完成，暂不处理（等待 onViewCreated 后再说）
        if (!isViewCreated) return

        isVisibleToUser = visible
        LogUtil.d(logTag, "notifyVisible: visible=$visible, isFirstVisible=$isFirstVisible")

        if (visible) {
            // 变为可见
            if (isFirstVisible) {
                isFirstVisible = false
                LogUtil.d(logTag, "-> onLazyInit (首次可见)")
                onLazyInit()   // 懒加载：只执行一次
            }
            LogUtil.d(logTag, "-> onVisible")
            onVisible()        // 可见回调：每次都执行
        } else {
            // 变为不可见
            LogUtil.d(logTag, "-> onInvisible")
            onInvisible()      // 不可见回调
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LogUtil.d(logTag, "onDestroyView -> 清理状态")
        
        // 清理 Binding 引用，防止内存泄漏
        _binding = null
        
        // 重置懒加载状态
        // 注意：如果 Fragment 被销毁后重建，懒加载会重新执行
        // 如果希望 Fragment 重建后不重新加载，可以将状态保存到 Bundle 中
        isViewCreated = false
        isFirstVisible = true
        isVisibleToUser = false
    }


    // ============================== Flow 订阅辅助 ==============================

    /**
     * 在 viewLifecycleOwner 上以 STARTED 状态收集 [SharedFlow]。
     *
     * 注意：必须用 viewLifecycleOwner 而非 this，否则 Fragment 视图销毁时
     * 协程不会取消，会导致泄漏 _binding。
     */
    protected inline fun <T> observe(
        flow: SharedFlow<T>,
        crossinline onEach: suspend (T) -> Unit,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect { onEach(it) }
            }
        }
    }

    protected inline fun <T> observe(
        flow: StateFlow<T>,
        crossinline onEach: suspend (T) -> Unit,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect { onEach(it) }
            }
        }
    }

    /**
     * 绑定 [BaseViewModel] 的通用副作用（错误/Toast/可选 loading）
     */
    protected fun bindBaseViewModel(
        viewModel: BaseViewModel,
        onLoadingChanged: ((Boolean) -> Unit)? = null,
    ) {
        observe(viewModel.errorEvent) { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        observe(viewModel.toastEvent) { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        if (onLoadingChanged != null) {
            observe(viewModel.isLoading) { onLoadingChanged(it) }
        }
    }


    companion object {
        inline fun <reified T : BaseFragment<*>> newInstance(): T {
            return T::class.java.getDeclaredConstructor().newInstance().apply {
                arguments = Bundle()
            }
        }
    }




}
