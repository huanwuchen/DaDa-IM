package com.dada.app.ui.friend

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.R
import com.dada.core.network.model.FriendRequest
import com.dada.app.databinding.ItemFriendRequestBinding
import com.dada.core.common.Constants
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.loadImage

/**
 * 好友请求列表 Adapter
 */
class FriendRequestsAdapter(
    private val imageLoader: ImageLoader,
    private val onAcceptClick: (FriendRequest) -> Unit,
    private val onRejectClick: (FriendRequest) -> Unit
) : ListAdapter<FriendRequest, FriendRequestsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, imageLoader, onAcceptClick, onRejectClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemFriendRequestBinding,
        private val imageLoader: ImageLoader,
        private val onAcceptClick: (FriendRequest) -> Unit,
        private val onRejectClick: (FriendRequest) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(request: FriendRequest) {
            binding.tvUsername.text = request.fromUsername
            binding.tvMessage.text = request.message?.takeIf { it.isNotBlank() } ?: "请求添加你为好友"

            binding.ivAvatar.loadImage(request.fromAvatar?.takeIf { it.isNotEmpty() }, imageLoader) {
                placeholder = R.drawable.ic_default_avatar
                error = R.drawable.ic_default_avatar
                asCircle = true
            }

            when (request.status) {
                0 -> {
                    binding.llActions.visibility = View.VISIBLE
                    binding.tvStatus.visibility = View.GONE
                    binding.btnAccept.setOnClickListener { onAcceptClick(request) }
                    binding.btnReject.setOnClickListener { onRejectClick(request) }
                }
                1 -> {
                    binding.llActions.visibility = View.GONE
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.tvStatus.text = "已同意"
                }
                2 -> {
                    binding.llActions.visibility = View.GONE
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.tvStatus.text = "已拒绝"
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<FriendRequest>() {
        override fun areItemsTheSame(oldItem: FriendRequest, newItem: FriendRequest): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FriendRequest, newItem: FriendRequest): Boolean {
            return oldItem == newItem
        }
    }
}
