package com.dada.app.ui.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.R
import com.dada.core.network.model.FriendInfo
import com.dada.app.databinding.ItemContactBinding
import com.dada.core.common.Constants
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.loadImage

/**
 * 通讯录列表 Adapter（使用好友列表数据）
 *
 * @param imageLoader 头像加载（由宿主页面 Hilt 注入后透传）
 * @param onItemClick 列表项点击回调
 */
class ContactsAdapter(
    private val imageLoader: ImageLoader,
    private val onItemClick: (FriendInfo) -> Unit
) : ListAdapter<FriendInfo, ContactsAdapter.ContactViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ContactViewHolder(binding, imageLoader, onItemClick)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ContactViewHolder(
        private val binding: ItemContactBinding,
        private val imageLoader: ImageLoader,
        private val onItemClick: (FriendInfo) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(friend: FriendInfo) {
            binding.tvNickname.text = friend.username
            binding.ivAvatar.loadImage(friend.avatar?.takeIf { it.isNotEmpty() }, imageLoader) {
                placeholder = R.drawable.ic_default_avatar
                error = R.drawable.ic_default_avatar
                asCircle = true
            }
            binding.root.setOnClickListener { onItemClick(friend) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<FriendInfo>() {
        override fun areItemsTheSame(oldItem: FriendInfo, newItem: FriendInfo): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FriendInfo, newItem: FriendInfo): Boolean =
            oldItem == newItem
    }
}
