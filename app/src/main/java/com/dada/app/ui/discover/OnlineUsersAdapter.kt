package com.dada.app.ui.discover

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.R
import com.dada.core.network.model.OnlineUser
import com.dada.app.databinding.ItemOnlineUserBinding
import com.dada.core.common.Constants
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.loadImage

/**
 * 在线用户列表适配器
 */
class OnlineUsersAdapter(
    private val imageLoader: ImageLoader,
    private val onItemClick: (OnlineUser) -> Unit,
    private val onAddFriendClick: (OnlineUser) -> Unit
) : ListAdapter<OnlineUser, OnlineUsersAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOnlineUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemOnlineUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: OnlineUser) {
            binding.tvUsername.text = user.username
            binding.tvUserId.text = "ID: ${user.id}"

            binding.ivAvatar.loadImage(user.avatar?.takeIf { it.isNotEmpty() }, imageLoader) {
                placeholder = R.drawable.ic_default_avatar
                error = R.drawable.ic_default_avatar
                asCircle = true
            }

            binding.ivOnlineStatus.setImageResource(
                if (user.online) R.drawable.ic_online else R.drawable.ic_offline
            )

            binding.root.setOnClickListener { onItemClick(user) }
            binding.btnAddFriend.setOnClickListener { onAddFriendClick(user) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<OnlineUser>() {
        override fun areItemsTheSame(oldItem: OnlineUser, newItem: OnlineUser): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: OnlineUser, newItem: OnlineUser): Boolean {
            return oldItem == newItem
        }
    }
}
