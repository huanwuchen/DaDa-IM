package com.dada.app.ui.moments

import android.content.Context
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.R
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.ImageRequest
import com.dada.core.imageloader.loadImage

/**
 * 发布页的图片网格 Adapter
 *
 * 列表 = 已选图片([Item.Image]) + 可选的「+」按钮([Item.PickButton])
 * 采用 [ListAdapter] + DiffUtil,避免全量刷新带来的闪烁与浪费。
 */
class PublishImageAdapter(
    private val maxCount: Int,
    private val imageLoader: ImageLoader,
    private val onPickClick: () -> Unit,
    private val onRemoveClick: (Uri) -> Unit,
) : ListAdapter<PublishImageAdapter.Item, RecyclerView.ViewHolder>(DiffCallback) {

    sealed class Item {
        data class Image(val uri: Uri) : Item()
        object PickButton : Item()
    }

    fun submitImages(uris: List<Uri>) {
        val items = uris.map<Uri, Item> { Item.Image(it) } +
            if (uris.size < maxCount) listOf(Item.PickButton) else emptyList()
        submitList(items)
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is Item.Image) TYPE_IMAGE else TYPE_PICK

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val size = (parent.measuredWidth / 3).takeIf { it > 0 }
            ?: ViewGroup.LayoutParams.MATCH_PARENT
        val padding = dp(ctx, 2f)
        return when (viewType) {
            TYPE_IMAGE -> ImageHolder(buildImageView(ctx, size, padding))
            else -> PickHolder(buildPickButton(ctx, size, padding))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is Item.Image -> {
                val view = holder.itemView as ImageView
                view.loadImage(item.uri, imageLoader) {
                    transform = ImageRequest.Transform.CenterCrop
                }
                view.setOnClickListener { onRemoveClick(item.uri) }
            }
            Item.PickButton -> holder.itemView.setOnClickListener { onPickClick() }
        }
    }

    private fun buildImageView(ctx: Context, size: Int, padding: Int): ImageView =
        ImageView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            setPadding(padding, padding, padding, padding)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.color.moments_image_placeholder)
        }

    private fun buildPickButton(ctx: Context, size: Int, padding: Int): ImageView =
        ImageView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            setPadding(padding, padding, padding, padding)
            setImageResource(R.drawable.ic_add_image)
            setBackgroundResource(R.color.moments_pick_button_bg)
            contentDescription = ctx.getString(R.string.moments_add_image)
        }

    private fun dp(ctx: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, ctx.resources.displayMetrics
    ).toInt()

    private class ImageHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    private class PickHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private object DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean = when {
            oldItem is Item.Image && newItem is Item.Image -> oldItem.uri == newItem.uri
            oldItem is Item.PickButton && newItem is Item.PickButton -> true
            else -> false
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean = oldItem == newItem
    }

    companion object {
        private const val TYPE_IMAGE = 1
        private const val TYPE_PICK = 2
    }
}
