package com.dada.app.widget.video

/**
 * 视频数据模型
 * @param url 视频播放地址
 * @param coverUrl 封面图地址
 * @param duration 视频时长（秒），可选
 */
data class VideoItem(
    val url: String,
    val coverUrl: String? = null,
    val duration: Int = 0
)
