package com.dada.app.widget.video

import android.content.Context
import com.dada.core.common.utils.LogUtil
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * 视频播放器管理器（单例）
 * 负责全局唯一的 ExoPlayer 实例管理
 *
 * 功能：
 * - 单例播放器，全局只有一个 ExoPlayer
 * - 支持 attach/detach PlayerView（RecyclerView 复用关键）
 * - 自动释放资源
 * - 播放状态回调
 */
class VideoPlayerManager private constructor(context: Context) {

    companion object {
        private const val TAG = "VideoPlayerManager"

        @Volatile
        private var instance: VideoPlayerManager? = null

        fun getInstance(context: Context): VideoPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: VideoPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        // 配置播放器
        repeatMode = Player.REPEAT_MODE_OFF
        playWhenReady = false

        // 添加播放器监听
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> {
                        LogUtil.d(TAG, "播放器状态: IDLE")
                        currentCallback?.onStateChanged(PlaybackState.IDLE)
                    }
                    Player.STATE_BUFFERING -> {
                        LogUtil.d(TAG, "播放器状态: BUFFERING")
                        currentCallback?.onStateChanged(PlaybackState.BUFFERING)
                    }
                    Player.STATE_READY -> {
                        LogUtil.d(TAG, "播放器状态: READY")
                        currentCallback?.onStateChanged(PlaybackState.READY)
                    }
                    Player.STATE_ENDED -> {
                        LogUtil.d(TAG, "播放器状态: ENDED")
                        currentCallback?.onStateChanged(PlaybackState.ENDED)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                LogUtil.d(TAG, "播放状态变化: isPlaying=$isPlaying")
                if (isPlaying) {
                    currentCallback?.onPlaying()
                } else {
                    currentCallback?.onPaused()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                LogUtil.e(TAG, "播放错误: ${error.message}", error)
                currentCallback?.onError(error.message ?: "播放失败")
            }
        })
    }

    private var currentPlayerView: PlayerView? = null
    private var currentUrl: String? = null
    private var currentCallback: PlaybackCallback? = null

    /**
     * 播放视频
     * @param url 视频地址
     * @param playerView 播放器视图
     * @param callback 播放状态回调
     */
    fun play(url: String, playerView: PlayerView, callback: PlaybackCallback? = null) {
        LogUtil.d(TAG, "play: url=$url")

        // 如果是同一个视频，只需要 attach
        if (url == currentUrl && exoPlayer.isPlaying) {
            attach(playerView, callback)
            return
        }

        // 停止当前播放
        if (currentUrl != url) {
            stop()
        }

        currentUrl = url
        currentCallback = callback

        // 设置媒体源
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        // attach 到新的 PlayerView
        attach(playerView, callback)

        // 开始播放
        exoPlayer.playWhenReady = true
    }

    /**
     * 暂停播放
     */
    fun pause() {
        LogUtil.d(TAG, "pause")
        exoPlayer.playWhenReady = false
    }

    /**
     * 恢复播放
     */
    fun resume() {
        LogUtil.d(TAG, "resume")
        exoPlayer.playWhenReady = true
    }

    /**
     * 停止播放
     */
    fun stop() {
        LogUtil.d(TAG, "stop")
        exoPlayer.stop()
        currentUrl = null
        currentCallback = null
    }

    /**
     * 将播放器 attach 到指定的 PlayerView
     * RecyclerView 复用的关键方法
     */
    fun attach(playerView: PlayerView, callback: PlaybackCallback? = null) {
        if (currentPlayerView == playerView) {
            return
        }

        LogUtil.d(TAG, "attach: playerView=$playerView")

        // 保存旧的 PlayerView 引用
        val oldPlayerView = currentPlayerView

        // 更新当前 PlayerView 和回调
        currentPlayerView = playerView
        currentCallback = callback

        // 从旧的 PlayerView 切换到新的 PlayerView
        PlayerView.switchTargetView(exoPlayer, oldPlayerView, playerView)
    }

    /**
     * 从当前 PlayerView detach
     */
    fun detach() {
        if (currentPlayerView != null) {
            LogUtil.d(TAG, "detach: playerView=$currentPlayerView")
            PlayerView.switchTargetView(exoPlayer, currentPlayerView, null)
            currentPlayerView = null
        }
    }

    /**
     * 释放播放器资源
     * 应在 Application.onTerminate 或不再使用时调用
     */
    fun release() {
        LogUtil.d(TAG, "release")
        detach()
        exoPlayer.release()
        currentUrl = null
        currentCallback = null
        instance = null
    }

    /**
     * 获取当前播放的 URL
     */
    fun getCurrentUrl(): String? = currentUrl

    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean = exoPlayer.isPlaying

    /**
     * 获取播放进度（毫秒）
     */
    fun getCurrentPosition(): Long = exoPlayer.currentPosition

    /**
     * 获取视频总时长（毫秒）
     */
    fun getDuration(): Long = exoPlayer.duration

    /**
     * 播放状态枚举
     */
    enum class PlaybackState {
        IDLE,       // 空闲
        BUFFERING,  // 缓冲中
        READY,      // 准备就绪
        ENDED       // 播放结束
    }

    /**
     * 播放状态回调接口
     */
    interface PlaybackCallback {
        fun onStateChanged(state: PlaybackState) {}
        fun onPlaying() {}
        fun onPaused() {}
        fun onError(message: String) {}
    }
}
