package com.dada.app.widget.emoji

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Emoji 网格 Adapter
 *
 * 简单的 GridLayoutManager + TextView，每格展示一个 emoji
 *
 * @param onEmojiClick 点击回调，参数为该 emoji 字符串
 */
class EmojiAdapter(
    private val onEmojiClick: (String) -> Unit,
) : RecyclerView.Adapter<EmojiAdapter.VH>() {

    private val items = EmojiData.EMOJIS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            textSize = 22f
            gravity = android.view.Gravity.CENTER
            val padding = (resources.displayMetrics.density * 8).toInt()
            setPadding(padding, padding, padding, padding)
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return VH(tv)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val emoji = items[position]
        (holder.itemView as TextView).text = emoji
        holder.itemView.setOnClickListener { onEmojiClick(emoji) }
    }

    class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView)
}
