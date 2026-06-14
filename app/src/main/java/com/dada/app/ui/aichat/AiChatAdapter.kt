package com.dada.app.ui.aichat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.R

class AiChatAdapter : ListAdapter<ChatMessage, AiChatAdapter.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_LEFT = 0
        private const val TYPE_RIGHT = 1
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isUser) TYPE_RIGHT else TYPE_LEFT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == TYPE_RIGHT) R.layout.item_ai_msg_right else R.layout.item_ai_msg_left
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 直接更新最后一个 ViewHolder 的文本，不触发 DiffUtil rebind。
     * 用于流式输出时高频更新。
     */
    fun updateLastHolderContent(msg: ChatMessage) {
        val recyclerView = recyclerView ?: return
        val lastPos = currentList.size - 1
        val holder = recyclerView.findViewHolderForAdapterPosition(lastPos) as? ViewHolder ?: return
        holder.updateContent(msg)
    }

    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvReasoning: TextView? = itemView.findViewById(R.id.tv_reasoning)
        private val reasoningContainer: LinearLayout? = itemView.findViewById(R.id.ll_reasoning)
        private val tvReasoningLabel: TextView? = itemView.findViewById(R.id.tv_reasoning_label)

        fun bind(msg: ChatMessage) {
            tvContent.text = msg.content
            bindReasoning(msg.reasoningContent)
        }

        fun updateContent(msg: ChatMessage) {
            tvContent.text = msg.content
            bindReasoning(msg.reasoningContent)
        }

        private fun bindReasoning(reasoning: String?) {
            if (reasoning.isNullOrBlank()) {
                reasoningContainer?.visibility = View.GONE
            } else {
                reasoningContainer?.visibility = View.VISIBLE
                tvReasoning?.text = reasoning
                tvReasoningLabel?.text = "思考中..."
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem === newItem

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem.content == newItem.content && oldItem.reasoningContent == newItem.reasoningContent
    }
}
