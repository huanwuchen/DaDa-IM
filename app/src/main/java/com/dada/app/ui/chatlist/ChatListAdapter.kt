package com.dada.app.ui.chatlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.databinding.ItemChatListBinding
import com.dada.core.common.Constants
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.loadImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 首页消息列表 Adapter
 *
 * 数据来自 Room（im_conversations 表）
 *
 * @param imageLoader 由宿主页面通过 Hilt 注入后透传，Adapter 不直接依赖具体加载库
 * @param onItemClick 列表项点击回调
 */
class ChatListAdapter(
    private val imageLoader: ImageLoader,
    private val onItemClick: (ChatListItem) -> Unit,
) : ListAdapter<ChatListItem, ChatListAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, imageLoader, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemChatListBinding,
        private val imageLoader: ImageLoader,
        private val onItemClick: (ChatListItem) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatListItem) {
            binding.tvNickname.text = item.peerUsername
            binding.tvLastMessage.text = item.lastMessage
            binding.tvTime.text = formatTime(item.lastMessageTime)

            if (item.unreadCount > 0) {
                binding.tvUnreadCount.visibility = View.VISIBLE
                binding.tvUnreadCount.text =
                    if (item.unreadCount > 99) "99+" else item.unreadCount.toString()
            } else {
                binding.tvUnreadCount.visibility = View.GONE
            }

            loadAvatar(Constants.resolveUrl(item.peerAvatar))

            binding.root.setOnClickListener { onItemClick(item) }
        }

        private fun loadAvatar(avatarUrl: String?) {
            binding.ivAvatar.loadImage(avatarUrl?.takeIf { it.isNotEmpty() }, imageLoader) {
                placeholder = com.dada.app.R.drawable.ic_default_avatar
                error = com.dada.app.R.drawable.ic_default_avatar
                asCircle = true
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ChatListItem>() {
        override fun areItemsTheSame(oldItem: ChatListItem, newItem: ChatListItem) =
            oldItem.peerId == newItem.peerId

        override fun areContentsTheSame(oldItem: ChatListItem, newItem: ChatListItem) =
            oldItem == newItem
    }

    companion object {
        private val TIME_FORMAT_HM = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val DATE_FORMAT = SimpleDateFormat("MM/dd", Locale.getDefault())

        /**
         * 时间格式化：今天显示 HH:mm，昨天显示「昨天」，更早显示 MM/dd
         */
        private fun formatTime(timestamp: Long): String {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply { timeInMillis = timestamp }
            val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
            val sameDay =
                sameYear && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
            val isYesterday = sameYear &&
                    now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR) == 1
            return when {
                sameDay -> TIME_FORMAT_HM.format(Date(timestamp))
                isYesterday -> "昨天"
                else -> DATE_FORMAT.format(Date(timestamp))
            }
        }
    }
}
