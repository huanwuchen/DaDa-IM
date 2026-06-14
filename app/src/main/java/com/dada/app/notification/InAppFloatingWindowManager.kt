package com.dada.app.notification

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.dada.app.R
import com.dada.core.common.Constants
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.loadImage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App 内部消息弹窗管理器
 *
 * 功能：
 * 1. 在当前 Activity 顶部显示消息弹窗
 * 2. 点击弹窗打开聊天页面
 * 3. 自动消失
 * 4. 支持来电弹窗（语音/视频通话）
 *
 * 注意：不使用系统级悬浮窗权限，仅在 App 内部显示
 */
@Singleton
class InAppFloatingWindowManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
) : Application.ActivityLifecycleCallbacks {

    private var currentActivity: Activity? = null
    private var floatingView: View? = null
    private var isShowing = false
    private var dismissRunnable: Runnable? = null

    companion object {
        private const val DISPLAY_DURATION = 3000L // 普通消息显示时长（毫秒）
        private const val CALL_DISPLAY_DURATION = 30000L // 来电显示时长（30秒）
        private const val ANIMATION_DURATION = 300L // 动画时长
    }

    init {
        // 注册 Activity 生命周期回调
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(this)
    }

    /**
     * 显示消息弹窗
     *
     * @param fromUserId 发送者ID
     * @param fromUsername 发送者昵称
     * @param message 消息内容
     * @param avatar 发送者头像
     * @param onClick 点击回调
     */
    fun showMessageNotification(
        fromUserId: Long,
        fromUsername: String,
        message: String,
        avatar: String?,
        onClick: () -> Unit
    ) {
        val activity = currentActivity ?: return

        // 如果已经在显示，先移除
        if (isShowing) {
            dismiss()
        }

        // 在主线程执行
        activity.runOnUiThread {
            try {
                // 获取根布局
                val rootView = activity.window.decorView as? FrameLayout ?: return@runOnUiThread

                // 创建弹窗视图
                floatingView = LayoutInflater.from(activity).inflate(
                    R.layout.layout_message_floating_window,
                    null
                )

                // 设置内容
                floatingView?.apply {
                    val ivAvatar = findViewById<ImageView>(R.id.iv_avatar)
                    val tvUsername = findViewById<TextView>(R.id.tv_username)
                    val tvMessage = findViewById<TextView>(R.id.tv_message)
                    val ivClose = findViewById<ImageView>(R.id.iv_close)

                    tvUsername.text = fromUsername
                    tvMessage.text = message

                    ivAvatar.loadImage(avatar?.takeIf { it.isNotEmpty() }, imageLoader) {
                        placeholder = R.drawable.ic_default_avatar
                        error = R.drawable.ic_default_avatar
                        asCircle = true
                    }

                    // 点击事件
                    setOnClickListener {
                        onClick()
                        dismiss()
                    }

                    // 关闭按钮
                    ivClose.setOnClickListener {
                        dismiss()
                    }
                }

                // 设置布局参数
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = getStatusBarHeight(activity)
                    leftMargin = dpToPx(8)
                    rightMargin = dpToPx(8)
                }

                // 添加到根布局
                rootView.addView(floatingView, params)
                isShowing = true

                // 显示动画
                showAnimation()

                // 自动消失
                dismissRunnable = Runnable { dismiss() }
                floatingView?.postDelayed(dismissRunnable, DISPLAY_DURATION)
            } catch (e: Exception) {
                e.printStackTrace()
                isShowing = false
            }
        }
    }

    /**
     * 显示来电弹窗（语音/视频）
     *
     * @param fromUserId 来电者ID
     * @param fromUsername 来电者昵称
     * @param avatar 来电者头像
     * @param isVideo 是否为视频通话
     * @param onAccept 接听回调
     * @param onReject 拒绝回调
     */
    fun showCallNotification(
        fromUserId: Long,
        fromUsername: String,
        avatar: String?,
        isVideo: Boolean,
        onAccept: () -> Unit,
        onReject: () -> Unit
    ) {
        val activity = currentActivity ?: return

        // 如果已经在显示，先移除
        if (isShowing) {
            dismiss()
        }

        // 在主线程执行
        activity.runOnUiThread {
            try {
                // 获取根布局
                val rootView = activity.window.decorView as? FrameLayout ?: return@runOnUiThread

                // 创建来电弹窗视图
                floatingView = LayoutInflater.from(activity).inflate(
                    R.layout.layout_call_floating_window,
                    rootView,
                    false
                )

                // 设置内容
                floatingView?.apply {
                    val ivAvatar = findViewById<ImageView>(R.id.iv_avatar)
                    val tvCallType = findViewById<TextView>(R.id.tv_call_type)
                    val tvUsername = findViewById<TextView>(R.id.tv_username)
                    val btnAccept = findViewById<ImageView>(R.id.btn_accept)
                    val btnReject = findViewById<ImageView>(R.id.btn_reject)

                    tvCallType.text = if (isVideo) "视频来电" else "语音来电"
                    tvUsername.text = "$fromUsername 邀请你通话"

                    ivAvatar.loadImage(avatar?.takeIf { it.isNotEmpty() }, imageLoader) {
                        placeholder = R.drawable.ic_default_avatar
                        error = R.drawable.ic_default_avatar
                        asCircle = true
                    }

                    // 接听按钮
                    btnAccept.setOnClickListener {
                        onAccept()
                        dismiss()
                    }

                    // 拒绝按钮
                    btnReject.setOnClickListener {
                        onReject()
                        dismiss()
                    }
                }

                // 设置布局参数
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = getStatusBarHeight(activity)
                    leftMargin = dpToPx(8)
                    rightMargin = dpToPx(8)
                }

                // 添加到根布局
                rootView.addView(floatingView, params)
                isShowing = true

                // 显示动画
                showAnimation()

                // 来电弹窗显示更长时间（30秒）
                dismissRunnable = Runnable { dismiss() }
                floatingView?.postDelayed(dismissRunnable, CALL_DISPLAY_DURATION)
            } catch (e: Exception) {
                e.printStackTrace()
                isShowing = false
            }
        }
    }

    /**
     * 显示动画（从顶部滑入）
     */
    private fun showAnimation() {
        floatingView?.let { view ->
            view.translationY = -view.height.toFloat()
            view.alpha = 0f
            view.post {
                ObjectAnimator.ofFloat(view, "translationY", 0f).apply {
                    duration = ANIMATION_DURATION
                    start()
                }
                ObjectAnimator.ofFloat(view, "alpha", 1f).apply {
                    duration = ANIMATION_DURATION
                    start()
                }
            }
        }
    }

    /**
     * 隐藏动画（滑出到顶部）
     */
    private fun hideAnimation(onEnd: () -> Unit) {
        floatingView?.let { view ->
            val animator1 = ObjectAnimator.ofFloat(view, "translationY", -view.height.toFloat()).apply {
                duration = ANIMATION_DURATION
            }
            val animator2 = ObjectAnimator.ofFloat(view, "alpha", 0f).apply {
                duration = ANIMATION_DURATION
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onEnd()
                    }
                })
            }
            animator1.start()
            animator2.start()
        }
    }

    /**
     * 移除弹窗
     */
    fun dismiss() {
        if (!isShowing || floatingView == null) return

        val activity = currentActivity ?: return

        activity.runOnUiThread {
            // 取消自动消失
            dismissRunnable?.let { floatingView?.removeCallbacks(it) }

            // 隐藏动画后移除
            hideAnimation {
                try {
                    val rootView = activity.window.decorView as? FrameLayout
                    rootView?.removeView(floatingView)
                    floatingView = null
                    isShowing = false
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 获取状态栏高度
     */
    private fun getStatusBarHeight(context: Context): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    /**
     * dp 转 px
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * 检查是否正在显示
     */
    fun isShowing(): Boolean = isShowing

    // ============================== Activity 生命周期回调 ==============================

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivity == activity) {
            // Activity 暂停时移除弹窗
            dismiss()
        }
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
            dismiss()
        }
    }
}
