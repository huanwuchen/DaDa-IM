package com.dada.app.widget.video

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.R
import com.dada.app.ui.chat.ImMessage

/**
 * 朋友圈视频适配器
 * 用于朋友圈中的视频列表展示
 *
 * 特点：
 * - 支持滚动时自动播放可见视频
 * - 自动管理播放器资源
 * - ViewHolder 回收时自动 detach
 */
class MomentVideoAdapter : ListAdapter<ImMessage, MomentVideoAdapter.VideoViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_moment_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        holder.detach()
    }

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val videoPlayerView: VideoPlayerView = itemView.findViewById(R.id.video_player_view)

        fun bind(message: ImMessage) {
            val videoItem = VideoItem(
                url = message.content,
                coverUrl = message.thumbUrl
            )
            videoPlayerView.setVideoItem(videoItem)
        }

        fun detach() {
            videoPlayerView.detach()
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ImMessage>() {
        override fun areItemsTheSame(oldItem: ImMessage, newItem: ImMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ImMessage, newItem: ImMessage): Boolean {
            return oldItem == newItem
        }
    }
}
