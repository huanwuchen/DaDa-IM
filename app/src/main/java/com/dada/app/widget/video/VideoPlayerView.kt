package com.dada.app.widget.video

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.media3.ui.PlayerView
import com.dada.app.R
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.ImageRequest
import com.dada.core.imageloader.loadImage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 视频播放器自定义 View
 * 基于 Media3 的 PlayerView 封装
 *
 * 自定义 View 无法直接 @Inject，通过 [EntryPointAccessors] 从应用级 Hilt 组件中取出 [ImageLoader]。
 */
class  VideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImageLoaderEntryPoint {
        fun imageLoader(): ImageLoader
    }

    private val imageLoader: ImageLoader =
        EntryPointAccessors.fromApplication(
            context.applicationContext, ImageLoaderEntryPoint::class.java
        ).imageLoader()

    private val playerView: PlayerView
    private val coverImageView: ImageView
    private val playButton: ImageView
    private val loadingView: ProgressBar

    private var videoItem: VideoItem? = null
    private var isAttached = false

    init {
        // 创建布局
        LayoutInflater.from(context).inflate(R.layout.view_video_player, this, true)

        playerView = findViewById(R.id.player_view)
        coverImageView = findViewById(R.id.iv_cover)
        playButton = findViewById(R.id.iv_play)
        loadingView = findViewById(R.id.loading)

        // 配置 PlayerView
        playerView.apply {
            useController = false // 不使用默认控制器
            controllerAutoShow = false
        }

        // 播放按钮点击事件
        playButton.setOnClickListener {
            togglePlayPause()
        }

        // 封面点击事件
        coverImageView.setOnClickListener {
            togglePlayPause()
        }
    }

    /**
     * 获取内部的 PlayerView（用于全屏播放等场景）
     */
    fun getPlayerView(): PlayerView = playerView

    /**
     * 设置视频数据
     */
    fun setVideoItem(item: VideoItem) {
        this.videoItem = item

        if (!item.coverUrl.isNullOrEmpty()) {
            coverImageView.loadImage(item.coverUrl, imageLoader) {
                transform = ImageRequest.Transform.CenterCrop
            }
            coverImageView.visibility = View.VISIBLE
        } else {
            coverImageView.visibility = View.GONE
        }

        // 显示播放按钮
        playButton.visibility = View.VISIBLE
        loadingView.visibility = View.GONE
    }

    /**
     * 开始播放
     */
    fun play() {
        val item = videoItem ?: return
        val manager = VideoPlayerManager.getInstance(context)

        // 如果当前正在播放这个视频，只需要恢复
        if (manager.getCurrentUrl() == item.url && isAttached) {
            manager.resume()
            return
        }

        // 播放新视频
        manager.play(item.url, playerView, object : VideoPlayerManager.PlaybackCallback {
            override fun onStateChanged(state: VideoPlayerManager.PlaybackState) {
                post {
                    when (state) {
                        VideoPlayerManager.PlaybackState.BUFFERING -> {
                            showLoading()
                        }
                        VideoPlayerManager.PlaybackState.READY -> {
                            hideLoading()
                            hideCover()
                        }
                        VideoPlayerManager.PlaybackState.ENDED -> {
                            showCover()
                            showPlayButton()
                        }
                        else -> {}
                    }
                }
            }

            override fun onPlaying() {
                post {
                    hidePlayButton()
                    hideCover()
                }
            }

            override fun onPaused() {
                post {
                    showPlayButton()
                }
            }

            override fun onError(message: String) {
                post {
                    hideLoading()
                    showCover()
                    showPlayButton()
                    // 可以显示错误提示
                }
            }
        })

        isAttached = true
    }

    /**
     * 暂停播放
     */
    fun pause() {
        VideoPlayerManager.getInstance(context).pause()
    }

    /**
     * 切换播放/暂停
     */
    private fun togglePlayPause() {
        val manager = VideoPlayerManager.getInstance(context)
        if (manager.isPlaying() && manager.getCurrentUrl() == videoItem?.url) {
            pause()
        } else {
            play()
        }
    }

    /**
     * 从播放器 detach（RecyclerView 回收时调用）
     */
    fun detach() {
        if (isAttached) {
            VideoPlayerManager.getInstance(context).detach()
            isAttached = false
            showCover()
            showPlayButton()
            hideLoading()
        }
    }

    /**
     * 显示封面
     */
    private fun showCover() {
        if (!videoItem?.coverUrl.isNullOrEmpty()) {
            coverImageView.visibility = View.VISIBLE
        }
    }

    /**
     * 隐藏封面
     */
    private fun hideCover() {
        coverImageView.visibility = View.GONE
    }

    /**
     * 显示播放按钮
     */
    private fun showPlayButton() {
        playButton.visibility = View.VISIBLE
    }

    /**
     * 隐藏播放按钮
     */
    private fun hidePlayButton() {
        playButton.visibility = View.GONE
    }

    /**
     * 显示加载状态
     */
    private fun showLoading() {
        loadingView.visibility = View.VISIBLE
        playButton.visibility = View.GONE
    }

    /**
     * 隐藏加载状态
     */
    private fun hideLoading() {
        loadingView.visibility = View.GONE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detach()
    }
}
