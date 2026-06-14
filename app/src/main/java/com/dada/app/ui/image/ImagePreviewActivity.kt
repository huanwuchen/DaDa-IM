package com.dada.app.ui.image

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.widget.ViewPager2
import com.dada.core.ui.base.BaseActivity
import com.dada.app.databinding.ActivityImagePreviewBinding
import com.dada.core.imageloader.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ImagePreviewActivity : BaseActivity<ActivityImagePreviewBinding>() {

    private lateinit var imageAdapter: ImagePreviewAdapter

    @Inject lateinit var imageLoader: ImageLoader

    // 起始位置和尺寸
    private var startX = 0
    private var startY = 0
    private var startWidth = 0
    private var startHeight = 0

    override fun inflateBinding() = ActivityImagePreviewBinding.inflate(layoutInflater)

    override fun initView() {
        setupFullScreen()

        val imageUrls = intent.getStringArrayListExtra(EXTRA_IMAGE_URLS) ?: arrayListOf()
        val currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)
        startX = intent.getIntExtra(EXTRA_START_X, 0)
        startY = intent.getIntExtra(EXTRA_START_Y, 0)
        startWidth = intent.getIntExtra(EXTRA_START_WIDTH, 0)
        startHeight = intent.getIntExtra(EXTRA_START_HEIGHT, 0)

        imageAdapter = ImagePreviewAdapter(imageUrls, imageLoader) { finish() }
        binding.viewPager.adapter = imageAdapter
        binding.viewPager.setCurrentItem(currentIndex, false)

        updateIndicator(currentIndex, imageUrls.size)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position, imageUrls.size)
            }
        })

        binding.ivClose.setOnClickListener { finish() }

        // 等待布局完成后执行进入动画
        binding.root.post { playEnterAnimation() }
    }

    override fun initData() {}

    private fun playEnterAnimation() {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()

        // 计算起始缩放比例
        val scaleX = startWidth / screenWidth
        val scaleY = startHeight / screenHeight

        // 计算起始位置的偏移（从图片中心到屏幕中心的偏移）
        val pivotX = startX + startWidth / 2f
        val pivotY = startY + startHeight / 2f
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val translateX = pivotX - centerX
        val translateY = pivotY - centerY

        // 初始状态：小图位置
        binding.viewPager.scaleX = scaleX
        binding.viewPager.scaleY = scaleY
        binding.viewPager.translationX = translateX
        binding.viewPager.translationY = translateY
        binding.root.setBackgroundColor(Color.TRANSPARENT)

        // 动画到全屏
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.viewPager, View.SCALE_X, scaleX, 1f),
                ObjectAnimator.ofFloat(binding.viewPager, View.SCALE_Y, scaleY, 1f),
                ObjectAnimator.ofFloat(binding.viewPager, View.TRANSLATION_X, translateX, 0f),
                ObjectAnimator.ofFloat(binding.viewPager, View.TRANSLATION_Y, translateY, 0f),
                ObjectAnimator.ofArgb(binding.root, "backgroundColor", Color.TRANSPARENT, Color.BLACK)
            )
            duration = 280
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun playExitAnimation(onEnd: () -> Unit) {

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()

        val scaleX = startWidth / screenWidth
        val scaleY = startHeight / screenHeight
        val pivotX = startX + startWidth / 2f
        val pivotY = startY + startHeight / 2f
        val translateX = pivotX - screenWidth / 2f
        val translateY = pivotY - screenHeight / 2f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.viewPager, View.SCALE_X, 1f, scaleX),
                ObjectAnimator.ofFloat(binding.viewPager, View.SCALE_Y, 1f, scaleY),
                ObjectAnimator.ofFloat(binding.viewPager, View.TRANSLATION_X, 0f, translateX),
                ObjectAnimator.ofFloat(binding.viewPager, View.TRANSLATION_Y, 0f, translateY),
                ObjectAnimator.ofArgb(binding.root, "backgroundColor", Color.BLACK, Color.TRANSPARENT)
            )
            duration = 250
            interpolator = DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd()
                }
            })
            start()
        }
    }

    private fun setupFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    private fun updateIndicator(position: Int, total: Int) {
        binding.tvIndicator.text = "${position + 1}/$total"
    }

    override fun finish() {
        // 先播放退出动画，再关闭
        playExitAnimation {
            super.finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val EXTRA_IMAGE_URLS = "extra_image_urls"
        const val EXTRA_CURRENT_INDEX = "extra_current_index"
        const val EXTRA_START_X = "extra_start_x"
        const val EXTRA_START_Y = "extra_start_y"
        const val EXTRA_START_WIDTH = "extra_start_width"
        const val EXTRA_START_HEIGHT = "extra_start_height"
    }
}
