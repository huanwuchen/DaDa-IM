package com.dada.app.utils.media

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.dada.core.common.utils.LogUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音播放工具
 */
@Singleton
class VoicePlayer @Inject constructor() {

    private var player: MediaPlayer? = null
    private var currentUrl: String? = null

    fun play(url: String, onCompletion: (() -> Unit)? = null) {
        stop()
        currentUrl = url

        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnCompletionListener {
                onCompletion?.invoke()
                release()
                player = null
                currentUrl = null
            }
            setOnErrorListener { _, what, extra ->
                LogUtil.e("VoicePlayer", "播放错误: what=$what, extra=$extra")
                release()
                player = null
                currentUrl = null
                true
            }
            try {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener { it.start() }
            } catch (e: Exception) {
                LogUtil.e("VoicePlayer", "播放失败: ${e.message}")
                release()
                player = null
                currentUrl = null
            }
        }
    }

    fun stop() {
        player?.apply {
            try {
                if (isPlaying) stop()
            } catch (_: Exception) {}
            release()
        }
        player = null
        currentUrl = null
    }

    fun isPlaying(url: String? = null): Boolean {
        return if (url != null) {
            currentUrl == url && player?.isPlaying == true
        } else {
            player?.isPlaying == true
        }
    }
}
