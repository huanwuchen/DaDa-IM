package com.dada.app.ui.chat.helper

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import com.dada.app.ui.chat.ImMessage
import com.dada.app.ui.image.ImagePreviewActivity
import com.dada.app.utils.media.VoicePlayer
import com.dada.app.widget.video.FullscreenVideoPlayerActivity

/**
 * 媒体打开助手
 *
 * 统一处理图片预览、视频播放、外部链接打开、语音播放，
 * Activity 的 Adapter 回调只需转发到此 Helper。
 */
class MediaOpenHelper(
    private val activity: Activity,
    private val voicePlayer: VoicePlayer,
) {

    /**
     * 跳转到 ImagePreviewActivity 看大图
     *
     * 当前会话中所有图片消息会被一起传入，用户可以左右翻看。
     * 起始位置取被点击 [imageView] 在屏幕上的坐标，提供从小图缩放到大图的过渡动画。
     */
    fun openImagePreview(rawUrl: String, imageView: View, allImageMessages: List<ImMessage>) {
        if (rawUrl.isBlank()) return

        val imageMessages = allImageMessages.filter { it.type == ImMessage.TYPE_IMAGE }
        val urls = ArrayList(imageMessages.map { it.content })
        val currentIndex = imageMessages.indexOfFirst { it.content == rawUrl }
            .coerceAtLeast(0)

        val location = IntArray(2)
        imageView.getLocationOnScreen(location)

        val intent = Intent(activity, ImagePreviewActivity::class.java).apply {
            putStringArrayListExtra(ImagePreviewActivity.EXTRA_IMAGE_URLS, urls)
            putExtra(ImagePreviewActivity.EXTRA_CURRENT_INDEX, currentIndex)
            putExtra(ImagePreviewActivity.EXTRA_START_X, location[0])
            putExtra(ImagePreviewActivity.EXTRA_START_Y, location[1])
            putExtra(ImagePreviewActivity.EXTRA_START_WIDTH, imageView.width)
            putExtra(ImagePreviewActivity.EXTRA_START_HEIGHT, imageView.height)
        }
        activity.startActivity(intent)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }

    /**
     * 跳转到视频播放页
     */
    fun openVideo(rawUrl: String, title: String) {
        if (rawUrl.isBlank()) return
        FullscreenVideoPlayerActivity.start(
            context = activity,
            videoUrl = rawUrl,
            title = title,
        )
    }

    /**
     * 用系统能力打开 URL（文件类型使用）
     */
    fun openExternalUrl(url: String) {
        if (url.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "无法打开: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 播放语音消息
     */
    fun playVoice(url: String) {
        if (url.isBlank()) return
        voicePlayer.play(url)
    }

    /**
     * 停止语音播放（供 onDestroy 调用）
     */
    fun stopVoice() {
        voicePlayer.stop()
    }
}
