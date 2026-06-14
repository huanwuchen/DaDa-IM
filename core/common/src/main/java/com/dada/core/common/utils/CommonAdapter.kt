package com.dada.core.common.utils

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

class CommonAdapter<T, VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    private val bind: VB.(T) -> Unit,
    private val itemId: (T) -> Any,
    private val onItemClick: (T) -> Unit = {}
) : ListAdapter<T, CommonAdapter.ViewHolder<VB>>(DiffCallback(itemId)) {

    class ViewHolder<VB : ViewBinding>(val binding: VB) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<VB> {
        val binding = inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder<VB>, position: Int) {
        val item = getItem(position)
        holder.binding.bind(item)
        holder.binding.root.setOnClickListener { onItemClick(item) }
    }

    private class DiffCallback<T>(private val itemId: (T) -> Any) : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T & Any, newItem: T & Any) =
            itemId(oldItem) == itemId(newItem)

        override fun areContentsTheSame(oldItem: T & Any, newItem: T & Any) =
            oldItem == newItem
    }
}
