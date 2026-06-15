package com.dada.app.widget.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import com.dada.app.databinding.ActivityFullscreenVideoPlayerBinding
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.ImageRequest
import com.dada.core.imageloader.loadImage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 全屏视频播放页面
 *
 * 功能：
 * - 全屏沉浸式播放
 * - 支持手势控制（点击显示/隐藏控制栏）
 * - 自动旋转支持
 * - 播放完成可选择自动关闭或循环播放
 */
@AndroidEntryPoint
class FullscreenVideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenVideoPlayerBinding
    private lateinit var videoPlayerManager: VideoPlayerManager

    @Inject lateinit var imageLoader: ImageLoader

    private lateinit var playerView: PlayerView
    private lateinit var coverImageView: ImageView
    private lateinit var loadingView: ProgressBar

    private var videoUrl: String = ""
    private var coverUrl: String? = null
    private var title: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏沉浸式
        setupFullscreen()

        binding = ActivityFullscreenVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取参数
        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        coverUrl = intent.getStringExtra(EXTRA_COVER_URL)
        title = intent.getStringExtra(EXTRA_TITLE)

        if (videoUrl.isEmpty()) {
            finish()
            return
        }

        videoPlayerManager = VideoPlayerManager.getInstance(this)

        setupViews()
        playVideo()
    }

    private fun setupFullscreen() {
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 沉浸式全屏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupViews() {
        playerView = binding.playerView
        coverImageView = binding.ivCover
        loadingView = binding.loading

        // 标题
        binding.tvTitle.text = title ?: "视频播放"

        // 返回按钮
        binding.ivBack.setOnClickListener {
            finish()
        }

        // 点击视频区域显示/隐藏控制栏
        playerView.setOnClickListener {
            toggleControlBar()
        }

        // 初始隐藏控制栏
        binding.topBar.visibility = View.GONE
    }

    private fun playVideo() {
        // 显示封面
        if (!coverUrl.isNullOrEmpty()) {
            coverImageView.loadImage(coverUrl, imageLoader) {
                transform = ImageRequest.Transform.CenterCrop
            }
            coverImageView.visibility = View.VISIBLE
        }

        // 显示加载状态
        loadingView.visibility = View.VISIBLE

        // 自动播放
        videoPlayerManager.play(
            url = videoUrl,
            playerView = playerView,
            callback = object : VideoPlayerManager.PlaybackCallback {
                override fun onStateChanged(state: VideoPlayerManager.PlaybackState) {
                    runOnUiThread {
                        when (state) {
                            VideoPlayerManager.PlaybackState.BUFFERING -> {
                                loadingView.visibility = View.VISIBLE
                            }
                            VideoPlayerManager.PlaybackState.READY -> {
                                loadingView.visibility = View.GONE
                                coverImageView.visibility = View.GONE
                            }
                            VideoPlayerManager.PlaybackState.ENDED -> {
                                // 播放完成，显示封面
                                coverImageView.visibility = View.VISIBLE
                                // 可选：自动关闭或循环播放
                                // finish()
                            }
                            else -> {}
                        }
                    }
                }

                override fun onPlaying() {
                    runOnUiThread {
                        coverImageView.visibility = View.GONE
                        loadingView.visibility = View.GONE
                    }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        loadingView.visibility = View.GONE
                        android.widget.Toast.makeText(
                            this@FullscreenVideoPlayerActivity,
                            "播放失败: $message",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            }
        )
    }

    private fun toggleControlBar() {
        val isVisible = binding.topBar.visibility == View.VISIBLE
        binding.topBar.visibility = if (isVisible) View.GONE else View.VISIBLE

        // 3秒后自动隐藏
        if (!isVisible) {
            binding.topBar.postDelayed({
                binding.topBar.visibility = View.GONE
            }, 3000)
        }
    }

    override fun onPause() {
        super.onPause()
        videoPlayerManager.pause()
    }

    override fun onResume() {
        super.onResume()
        if (videoPlayerManager.getCurrentUrl() == videoUrl) {
            videoPlayerManager.resume()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoPlayerManager.stop()
    }

    companion object {
        private const val EXTRA_VIDEO_URL = "extra_video_url"
        private const val EXTRA_COVER_URL = "extra_cover_url"
        private const val EXTRA_TITLE = "extra_title"

        /**
         * 启动全屏视频播放页面
         *
         * @param context 上下文
         * @param videoUrl 视频地址
         * @param coverUrl 封面图地址（可选）
         * @param title 标题（可选）
         */
        fun start(
            context: Context,
            videoUrl: String,
            coverUrl: String? = null,
            title: String? = null
        ) {
            val intent = Intent(context, FullscreenVideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_COVER_URL, coverUrl)
                putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }
    }
}
