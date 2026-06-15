package com.dada.app.ui.chat.helper

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.MotionEvent
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dada.app.databinding.ActivityWxChatBinding
import com.dada.app.utils.media.VoiceRecorder
import java.io.File

/**
 * 「按住说话」语音录制助手
 *
 * 封装 VoiceRecorder + 触摸交互 + 权限请求，
 * Activity 只需调用 [setup] 和转发 [onRequestPermissionsResult]。
 */
class VoiceRecordHelper(
    private val activity: Activity,
    private val binding: ActivityWxChatBinding,
    private val onVoiceRecorded: (File, Long) -> Unit,
) {

    private val voiceRecorder = VoiceRecorder(activity)
    private var voiceCanceled = false
    private var pendingRecord = false

    /**
     * 绑定 btnHoldToTalk 触摸事件
     */
    fun setup() {
        binding.btnHoldToTalk.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                        pendingRecord = true
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            REQUEST_RECORD_AUDIO,
                        )
                        return@setOnTouchListener false
                    }
                    voiceCanceled = false
                    if (voiceRecorder.start()) {
                        binding.btnHoldToTalk.text = "松开 发送（上滑取消）"
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    voiceCanceled = event.y < -100
                    binding.btnHoldToTalk.text =
                        if (voiceCanceled) "松开手指 取消发送" else "松开 发送（上滑取消）"
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.btnHoldToTalk.text = "按住 说话"
                    if (voiceCanceled) {
                        voiceRecorder.cancel()
                    } else {
                        val result = voiceRecorder.stop()
                        if (result == null) {
                            Toast.makeText(activity, "录音失败", Toast.LENGTH_SHORT).show()
                        } else if (result.durationMs < 800) {
                            Toast.makeText(activity, "录音时间太短", Toast.LENGTH_SHORT).show()
                            result.file.delete()
                        } else {
                            onVoiceRecorded(result.file, result.durationMs)
                        }
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 转发权限请求结果（由 Activity 的 onRequestPermissionsResult 调用）
     */
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQUEST_RECORD_AUDIO) return
        if (!pendingRecord) return
        pendingRecord = false

        val allGranted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (!allGranted) {
            Toast.makeText(activity, "需要麦克风权限才能发送语音", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasPermission(perm: String): Boolean =
        ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val REQUEST_RECORD_AUDIO = 0x3004
    }
}
