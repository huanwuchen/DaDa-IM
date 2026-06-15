package com.dada.app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.R
import com.dada.core.common.Constants
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.ImageRequest
import com.dada.core.imageloader.loadImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 微信风格聊天列表 Adapter
 *
 * 多类型支持：
 *  - 文本：左 / 右气泡
 *  - 图片：缩略图气泡，点击查看大图
 *  - 视频：封面 + 播放按钮 + 时长
 *  - 语音：背景条 + 时长，点击播放
 *  - 文件：图标 + 文件名 + 大小，点击下载/打开
 */
class WxChatAdapter(
    private val myUserId: Long,
    private val imageLoader: ImageLoader,
    private val callbacks: Callbacks = Callbacks(),
) : ListAdapter<ImMessage, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    /**
     * 各类气泡的点击回调
     *
     * 第二个参数为被点击的 View（图片/视频缩略图等），用于过渡动画的起始位置。
     */
    data class Callbacks(
        val onImageClick: (ImMessage, View) -> Unit = { _, _ -> },
        val onVideoClick: (ImMessage, View) -> Unit = { _, _ -> },
        val onVoiceClick: (ImMessage, View) -> Unit = { _, _ -> },
        val onFileClick: (ImMessage, View) -> Unit = { _, _ -> },
    )

    // ============================== ViewType ==============================

    override fun getItemViewType(position: Int): Int {
        val msg = getItem(position)
        val isMine = msg.fromId == myUserId
        return when (msg.type) {
            ImMessage.TYPE_IMAGE -> if (isMine) TYPE_IMAGE_RIGHT else TYPE_IMAGE_LEFT
            ImMessage.TYPE_VIDEO -> if (isMine) TYPE_VIDEO_RIGHT else TYPE_VIDEO_LEFT
            ImMessage.TYPE_AUDIO -> if (isMine) TYPE_VOICE_RIGHT else TYPE_VOICE_LEFT
            ImMessage.TYPE_FILE -> if (isMine) TYPE_FILE_RIGHT else TYPE_FILE_LEFT
            ImMessage.TYPE_CALL_HINT -> TYPE_CALL_HINT
            else -> if (isMine) TYPE_TEXT_RIGHT else TYPE_TEXT_LEFT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TEXT_LEFT -> TextHolder(inflater.inflate(R.layout.item_chat_left, parent, false))
            TYPE_TEXT_RIGHT -> TextHolder(inflater.inflate(R.layout.item_chat_right, parent, false))
            TYPE_IMAGE_LEFT, TYPE_IMAGE_RIGHT ->
                ImageHolder(
                    inflater.inflate(R.layout.item_chat_image, parent, false),
                    rightAligned = (viewType == TYPE_IMAGE_RIGHT)
                )
            TYPE_VIDEO_LEFT, TYPE_VIDEO_RIGHT ->
                VideoHolder(
                    inflater.inflate(R.layout.item_chat_video, parent, false),
                    rightAligned = (viewType == TYPE_VIDEO_RIGHT)
                )
            TYPE_VOICE_LEFT, TYPE_VOICE_RIGHT ->
                VoiceHolder(
                    inflater.inflate(R.layout.item_chat_voice_msg, parent, false),
                    rightAligned = (viewType == TYPE_VOICE_RIGHT)
                )
            TYPE_FILE_LEFT, TYPE_FILE_RIGHT ->
                FileHolder(
                    inflater.inflate(R.layout.item_chat_file_msg, parent, false),
                    rightAligned = (viewType == TYPE_FILE_RIGHT)
                )
            TYPE_CALL_HINT ->
                CallHintHolder(inflater.inflate(R.layout.item_chat_call_hint, parent, false))
            else -> throw IllegalArgumentException("未知的 viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is TextHolder -> holder.bind(msg)
            is ImageHolder -> holder.bind(msg, callbacks.onImageClick)
            is VideoHolder -> holder.bind(msg, callbacks.onVideoClick)
            is VoiceHolder -> holder.bind(msg, callbacks.onVoiceClick)
            is FileHolder -> holder.bind(msg, callbacks.onFileClick)
            is CallHintHolder -> holder.bind(msg)
        }
    }

    // ============================== ViewHolders ==============================

    /** 纯文本气泡（复用之前的左右气泡布局） */
    inner class TextHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val ivAvatar: ImageView? = itemView.findViewById(R.id.iv_avatar)

        fun bind(msg: ImMessage) {
            tvContent.text = msg.content
            tvTime.text = formatTime(msg.time)
            loadAvatar(ivAvatar, msg.avatar, imageLoader)
        }
    }

    /** 图片气泡 */
    inner class ImageHolder(
        itemView: View,
        rightAligned: Boolean,
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_image)
        private val ivAvatar: ImageView? = itemView.findViewById(R.id.iv_avatar)

        init {
            (itemView as LinearLayout).gravity =
                if (rightAligned) android.view.Gravity.END else android.view.Gravity.START
            if (rightAligned) moveAvatarToEnd(itemView)
        }

        fun bind(msg: ImMessage, onClick: (ImMessage, View) -> Unit) {
            val url = msg.thumbUrl ?: msg.content
            ivImage.loadImage(Constants.resolveUrl(url), imageLoader) {
                transform = ImageRequest.Transform.CenterCrop
            }
            ivImage.setOnClickListener { onClick(msg, ivImage) }
            loadAvatar(ivAvatar, msg.avatar, imageLoader)
        }
    }

    /** 视频气泡 */
    inner class VideoHolder(
        itemView: View,
        rightAligned: Boolean,
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivThumb: ImageView = itemView.findViewById(R.id.iv_thumb)
        private val tvDuration: TextView = itemView.findViewById(R.id.tv_duration)
        private val ivAvatar: ImageView? = itemView.findViewById(R.id.iv_avatar)

        init {
            (itemView as LinearLayout).gravity =
                if (rightAligned) android.view.Gravity.END else android.view.Gravity.START
            if (rightAligned) moveAvatarToEnd(itemView)
        }

        fun bind(msg: ImMessage, onClick: (ImMessage, View) -> Unit) {
            val thumb = msg.thumbUrl ?: msg.content
            ivThumb.loadImage(Constants.resolveUrl(thumb), imageLoader) {
                transform = ImageRequest.Transform.CenterCrop
            }
            tvDuration.text = formatDuration(msg.duration)
            val container = itemView.findViewById<View>(R.id.fl_video)
            container.setOnClickListener { onClick(msg, container) }
            loadAvatar(ivAvatar, msg.avatar, imageLoader)
        }
    }

    /** 语音气泡 */
    inner class VoiceHolder(
        itemView: View,
        rightAligned: Boolean,
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvDuration: TextView = itemView.findViewById(R.id.tv_voice_duration)
        private val ivAvatar: ImageView? = itemView.findViewById(R.id.iv_avatar)

        init {
            (itemView as LinearLayout).gravity =
                if (rightAligned) android.view.Gravity.END else android.view.Gravity.START
            if (rightAligned) moveAvatarToEnd(itemView)
        }

        fun bind(msg: ImMessage, onClick: (ImMessage, View) -> Unit) {
            val seconds = (msg.duration / 1000).coerceAtLeast(1)
            tvDuration.text = "${seconds}″"
            val container = itemView.findViewById<View>(R.id.ll_voice)
            container.setOnClickListener { onClick(msg, container) }
            loadAvatar(ivAvatar, msg.avatar, imageLoader)
        }
    }

    /** 文件气泡 */
    inner class FileHolder(
        itemView: View,
        rightAligned: Boolean,
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_file_name)
        private val tvSize: TextView = itemView.findViewById(R.id.tv_file_size)
        private val ivAvatar: ImageView? = itemView.findViewById(R.id.iv_avatar)

        init {
            (itemView as LinearLayout).gravity =
                if (rightAligned) android.view.Gravity.END else android.view.Gravity.START
            if (rightAligned) moveAvatarToEnd(itemView)
        }

        fun bind(msg: ImMessage, onClick: (ImMessage, View) -> Unit) {
            tvName.text = msg.fileName?.takeIf { it.isNotBlank() }
                ?: msg.content.substringAfterLast('/')
            tvSize.text = formatSize(msg.size)
            val container = itemView.findViewById<View>(R.id.ll_file)
            container.setOnClickListener { onClick(msg, container) }
            loadAvatar(ivAvatar, msg.avatar, imageLoader)
        }
    }

    /** 通话提示消息（居中灰色文字 + 图标） */
    class CallHintHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_call_icon)
        private val tvHint: TextView = itemView.findViewById(R.id.tv_call_hint)

        fun bind(msg: ImMessage) {
            tvHint.text = msg.content
            if (msg.iconRes != 0) {
                ivIcon.setImageResource(msg.iconRes)
            }
        }
    }

    // ============================== DiffUtil ==============================

    private class MessageDiffCallback : DiffUtil.ItemCallback<ImMessage>() {
        override fun areItemsTheSame(oldItem: ImMessage, newItem: ImMessage) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ImMessage, newItem: ImMessage) =
            oldItem == newItem
    }

    companion object {
        // 文本
        private const val TYPE_TEXT_LEFT = 1
        private const val TYPE_TEXT_RIGHT = 2

        // 图片
        private const val TYPE_IMAGE_LEFT = 3
        private const val TYPE_IMAGE_RIGHT = 4

        // 视频
        private const val TYPE_VIDEO_LEFT = 5
        private const val TYPE_VIDEO_RIGHT = 6

        // 语音
        private const val TYPE_VOICE_LEFT = 7
        private const val TYPE_VOICE_RIGHT = 8

        // 文件
        private const val TYPE_FILE_LEFT = 9
        private const val TYPE_FILE_RIGHT = 10

        // 通话提示
        private const val TYPE_CALL_HINT = 11

        private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())

        private fun formatTime(timestamp: Long): String =
            TIME_FORMAT.format(Date(timestamp))

        /** 时长 -> mm:ss */
        private fun formatDuration(durationMs: Long): String {
            val sec = (durationMs / 1000).toInt()
            return String.format(Locale.getDefault(), "%02d:%02d", sec / 60, sec % 60)
        }

        /** 字节数 -> 人类可读 */
        private fun formatSize(bytes: Long): String {
            if (bytes <= 0) return ""
            val units = arrayOf("B", "KB", "MB", "GB")
            var size = bytes.toDouble()
            var unit = 0
            while (size >= 1024 && unit < units.size - 1) {
                size /= 1024
                unit++
            }
            return String.format(Locale.getDefault(), "%.1f %s", size, units[unit])
        }

        /**
         * 将头像从行首移到行尾（自己发送的消息，头像在右侧）
         */
        private fun moveAvatarToEnd(itemView: View) {
            val row = itemView.findViewById<LinearLayout>(R.id.ll_content_row) ?: return
            val avatar = itemView.findViewById<ImageView>(R.id.iv_avatar) ?: return
            val oldLp = avatar.layoutParams as? LinearLayout.LayoutParams ?: return
            row.removeView(avatar)
            val lp = LinearLayout.LayoutParams(oldLp.width, oldLp.height).apply {
                marginStart = 6.dp(itemView)
            }
            row.addView(avatar, lp)
        }

        private fun Int.dp(view: View): Int =
            (this * view.context.resources.displayMetrics.density).toInt()

        /** 加载头像（自动拼接服务器 host） */
        private fun loadAvatar(ivAvatar: ImageView?, avatarUrl: String?, imageLoader: ImageLoader) {
            ivAvatar?.let { avatar ->
                val resolved = avatarUrl?.takeIf { it.isNotBlank() }?.let { Constants.resolveUrl(it) }
                avatar.loadImage(resolved?.takeIf { it.isNotEmpty() }, imageLoader) {
                    placeholder = R.drawable.ic_default_avatar
                    error = R.drawable.ic_default_avatar
                    asCircle = true
                }
            }
        }
    }
}
