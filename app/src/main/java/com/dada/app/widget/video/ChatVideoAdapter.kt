package com.dada.app.widget.video

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.R

/**
 * 聊天消息适配器示例
 * 展示如何在 RecyclerView 中使用 VideoPlayerView
 *
 * 关键点：
 * 1. ViewHolder 回收时调用 detach()
 * 2. 滚动时自动停止旧视频，播放新视频
 * 3. 使用 DiffUtil 优化性能
 */
class ChatVideoAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatMessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_TEXT = 1
        private const val VIEW_TYPE_VIDEO = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).type) {
            MessageType.TEXT -> VIEW_TYPE_TEXT
            MessageType.VIDEO -> VIEW_TYPE_VIDEO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TEXT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_text, parent, false)
                TextViewHolder(view)
            }
            VIEW_TYPE_VIDEO -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_video, parent, false)
                VideoViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is TextViewHolder -> holder.bind(message)
            is VideoViewHolder -> holder.bind(message)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        // 关键：ViewHolder 回收时 detach 播放器
        if (holder is VideoViewHolder) {
            holder.detach()
        }
    }

    /**
     * 文本消息 ViewHolder
     */
    class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)

        fun bind(message: ChatMessage) {
            tvContent.text = message.content
        }
    }

    /**
     * 视频消息 ViewHolder
     */
    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val videoPlayerView: VideoPlayerView = itemView.findViewById(R.id.video_player_view)

        fun bind(message: ChatMessage) {
            message.videoItem?.let { videoItem ->
                videoPlayerView.setVideoItem(videoItem)
            }
        }

        fun detach() {
            videoPlayerView.detach()
        }
    }
}

/**
 * 聊天消息数据模型
 */
data class ChatMessage(
    val id: String,
    val type: MessageType,
    val content: String = "",
    val videoItem: VideoItem? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 消息类型
 */
enum class MessageType {
    TEXT,
    VIDEO
}

/**
 * DiffUtil 回调
 */
class ChatMessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem == newItem
    }
}
