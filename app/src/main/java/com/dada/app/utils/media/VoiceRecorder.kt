package com.dada.app.utils.media

import android.app.Activity
import android.media.MediaRecorder
import com.dada.core.common.utils.LogUtil
import java.io.File
import java.io.IOException

/**
 * 语音录制工具
 */
class VoiceRecorder(private val activity: Activity) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    fun start(): Boolean {
        return try {
            val file = File(activity.cacheDir, "voice_${System.currentTimeMillis()}.aac")
            outputFile = file

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            startTimeMs = System.currentTimeMillis()
            true
        } catch (e: IOException) {
            LogUtil.e("VoiceRecorder", "录音启动失败: ${e.message}")
            release()
            false
        }
    }

    fun stop(): RecordingResult? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null

            val file = outputFile ?: return null
            val durationMs = System.currentTimeMillis() - startTimeMs

            if (file.exists() && file.length() > 0) {
                RecordingResult(file, durationMs)
            } else {
                null
            }
        } catch (e: Exception) {
            LogUtil.e("VoiceRecorder", "录音停止失败: ${e.message}")
            release()
            null
        }
    }

    fun cancel() {
        release()
        outputFile?.delete()
        outputFile = null
    }

    private fun release() {
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
    }

    data class RecordingResult(val file: File, val durationMs: Long)
}
