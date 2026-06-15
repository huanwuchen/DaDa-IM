package com.dada.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.dada.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息通知管理器
 *
 * 功能：
 * 1. 根据手机静音/铃声状态自动调整通知
 * 2. 消息到达时震动
 * 3. 播放通知音效
 */
@Singleton
class MessageNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    companion object {
        private const val CHANNEL_ID = "im_message_channel"
        private const val CHANNEL_NAME = "消息通知"
        private const val VIBRATE_DURATION = 200L // 震动时长（毫秒）
    }

    init {
        createNotificationChannel()
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "IM消息通知"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, VIBRATE_DURATION, 100, VIBRATE_DURATION)

                // 设置通知音效
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 处理新消息通知
     *
     * @param fromUserId 发送者ID
     * @param fromUsername 发送者昵称
     * @param message 消息内容
     * @param avatar 发送者头像
     */
    fun handleNewMessage(
        fromUserId: Long,
        fromUsername: String,
        message: String,
        avatar: String?
    ) {
        // 检查手机状态
        val ringerMode = audioManager.ringerMode

        when (ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                // 正常模式：震动 + 声音
                vibrate()
                playNotificationSound()
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                // 震动模式：仅震动
                vibrate()
            }
            AudioManager.RINGER_MODE_SILENT -> {
                // 静音模式：什么都不做
            }
        }
    }

    /**
     * 震动
     */
    private fun vibrate() {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, VIBRATE_DURATION, 100, VIBRATE_DURATION),
                -1 // 不重复
            )
            vibrator.vibrate(effect)
        } else {
            // Android 8.0以下
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, VIBRATE_DURATION, 100, VIBRATE_DURATION), -1)
        }
    }

    /**
     * 播放通知音效
     */
    private fun playNotificationSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notification)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 检查是否应该显示通知
     *
     * @return true-显示通知，false-不显示
     */
    fun shouldShowNotification(): Boolean {
        // 如果是静音模式，也不显示悬浮窗
        return audioManager.ringerMode != AudioManager.RINGER_MODE_SILENT
    }

    /**
     * 获取当前铃声模式描述
     */
    fun getRingerModeDescription(): String {
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "正常"
            AudioManager.RINGER_MODE_VIBRATE -> "震动"
            AudioManager.RINGER_MODE_SILENT -> "静音"
            else -> "未知"
        }
    }
}
