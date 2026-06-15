package com.dada.app.ui.moments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.R
import com.dada.core.network.model.Moment
import com.dada.app.databinding.HeaderMomentsBinding
import com.dada.app.databinding.ItemMomentBinding
import com.dada.app.utils.MomentTimeFormatter
import com.dada.core.common.Constants
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.ImageRequest
import com.dada.core.imageloader.loadImage

/**
 * 朋友圈列表 Adapter
 *
 * 包含两种 ViewType：
 *  - position 0：头部（封面图 + 用户名 + 头像）
 *  - 其他：动态条目
 *
 * 单条结构：头像 / 昵称 / 正文 / 图片九宫格 / 视频 / 位置 / 时间 / 点赞 / 评论
 *
 * @param myUserId       当前登录用户，用于判断是否显示「删除」按钮
 * @param onLike         点击点赞
 * @param onComment      点击评论按钮
 * @param onDelete       点击删除
 * @param onImageClick   点击单张图片（参数：URL 列表 + 当前位置 + 被点击 view）
 * @param onVideoClick   点击视频
 * @param headerData     头部展示数据
 */
class MomentsAdapter(
    private val myUserId: Long,
    private val imageLoader: ImageLoader,
    private val onLike: (Moment) -> Unit,
    private val onComment: (Moment) -> Unit,
    private val onDelete: (Moment) -> Unit,
    private val onImageClick: (List<String>, Int, ImageView) -> Unit,
    private val onVideoClick: (String) -> Unit,
    private val onCoverClick: () -> Unit,
    private val headerData: HeaderData,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /**
     * 头部展示数据
     */
    data class HeaderData(
        val username: String,
        val avatarUrl: String?,
        var coverUrl: String?,
    )

    private val items = mutableListOf<Moment>()

    /**
     * 列表 = 头部(1) + 动态条目(N)
     */
    override fun getItemCount(): Int = 1 + items.size

    override fun getItemViewType(position: Int): Int =
        if (position == 0) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(HeaderMomentsBinding.inflate(inflater, parent, false))
        } else {
            ItemHolder(ItemMomentBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderHolder -> holder.bind(headerData)
            is ItemHolder -> holder.bind(items[position - 1])
        }
    }

    /**
     * 更新封面图 URL
     */
    fun updateCoverUrl(url: String?) {
        headerData.coverUrl = url
        notifyItemChanged(0)
    }

    /**
     * 提交动态列表（DiffUtil 计算差异，最小化刷新）
     */
    fun submitList(newList: List<Moment>, onCommitted: (() -> Unit)? = null) {
        val diff = DiffUtil.calculateDiff(MomentDiffCallback(items, newList))
        items.clear()
        items.addAll(newList)
        diff.dispatchUpdatesTo(object : androidx.recyclerview.widget.ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                notifyItemRangeInserted(position + 1, count)
            }
            override fun onRemoved(position: Int, count: Int) {
                notifyItemRangeRemoved(position + 1, count)
            }
            override fun onMoved(fromPosition: Int, toPosition: Int) {
                notifyItemMoved(fromPosition + 1, toPosition + 1)
            }
            override fun onChanged(position: Int, count: Int, payload: Any?) {
                notifyItemRangeChanged(position + 1, count, payload)
            }
        })
        onCommitted?.invoke()
    }

    // ============================== Header ==============================

    inner class HeaderHolder(private val binding: HeaderMomentsBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(data: HeaderData) {
            binding.tvMyUsername.text = data.username

            val coverUrl = Constants.resolveUrl(data.coverUrl)
            if (!coverUrl.isNullOrEmpty()) {
                binding.ivCover.loadImage(coverUrl, imageLoader) {
                    transform = ImageRequest.Transform.CenterCrop
                }
            } else {
                binding.ivCover.setImageResource(0)
                binding.ivCover.setBackgroundColor(0xFF393A3E.toInt())
            }

            val avatarUrl = Constants.resolveUrl(data.avatarUrl)
            binding.ivMyAvatar.loadImage(avatarUrl?.takeIf { it.isNotEmpty() }, imageLoader) {
                placeholder = R.drawable.ic_default_avatar
                error = R.drawable.ic_default_avatar
                asCircle = true
            }

            binding.ivCover.setOnClickListener { onCoverClick() }
        }
    }

    // ============================== Item ==============================

    inner class ItemHolder(private val binding: ItemMomentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Moment) {
            // 用户信息
            val name = item.user?.username?.takeIf { it.isNotBlank() } ?: "用户${item.userId}"
            binding.tvUsername.text = name

            binding.ivAvatar.loadImage(Constants.resolveUrl(item.user?.avatar), imageLoader) {
                placeholder = R.drawable.ic_default_avatar
                error = R.drawable.ic_default_avatar
                asCircle = true
            }

            // 正文
            if (item.content.isNullOrBlank()) {
                binding.tvContent.visibility = View.GONE
            } else {
                binding.tvContent.visibility = View.VISIBLE
                binding.tvContent.text = item.content
            }

            // 图片九宫格 / 视频（二选一）
            renderMedia(item)

            // 位置
            if (item.location.isNullOrBlank()) {
                binding.tvLocation.visibility = View.GONE
            } else {
                binding.tvLocation.visibility = View.VISIBLE
                binding.tvLocation.text = item.location
            }

            // 时间
            binding.tvTime.text = MomentTimeFormatter.format(item.createTime)

            // 删除（仅自己的）
            binding.btnDelete.visibility = if (item.userId == myUserId) View.VISIBLE else View.GONE
            binding.btnDelete.setOnClickListener { onDelete(item) }

            // 操作菜单（点赞/评论）
            binding.btnAction.setOnClickListener { showActionMenu(item) }

            // 点赞 + 评论
            renderInteractions(item)
        }

        /**
         * 渲染图片网格 / 视频缩略图
         */
        private fun renderMedia(item: Moment) {
            val videoUrl = item.videoUrl
            when {
                !videoUrl.isNullOrBlank() -> {
                    binding.flVideo.visibility = View.VISIBLE
                    binding.rvImages.visibility = View.GONE

                    binding.ivVideoThumb.loadImage(videoUrl, imageLoader) {
                        transform = ImageRequest.Transform.CenterCrop
                    }
                    binding.flVideo.setOnClickListener { onVideoClick(videoUrl) }
                }
                item.images.isNotEmpty() -> {
                    binding.flVideo.visibility = View.GONE
                    binding.rvImages.visibility = View.VISIBLE
                    setupImageGrid(item.images)
                }
                else -> {
                    binding.flVideo.visibility = View.GONE
                    binding.rvImages.visibility = View.GONE
                }
            }
        }

        /**
         * 微信式九宫格：1 张满铺，4 张 2x2，其余 3 列
         */
        private fun setupImageGrid(images: List<String>) {
            val rv = binding.rvImages
            val span = when {
                images.size == 1 -> 1
                images.size == 4 -> 2
                else -> 3
            }
            rv.layoutManager = GridLayoutManager(rv.context, span)
            rv.adapter = MomentImageAdapter(images, imageLoader) { position, view ->
                onImageClick(images, position, view)
            }

            // 添加间距装饰（避免复用时重复添加）
//            if (rv.itemDecorationCount == 0) {
//                val gapPx = (4 * rv.context.resources.displayMetrics.density).toInt()
//                rv.addItemDecoration(object : RecyclerView.ItemDecoration() {
//                    override fun getItemOffsets(
//                        outRect: android.graphics.Rect,
//                        view: View,
//                        parent: RecyclerView,
//                        state: RecyclerView.State,
//                    ) {
//                        val position = parent.getChildAdapterPosition(view)
//                        val spanCount = span
//                        val column = position % spanCount
//                        // 左：当前列 * gap / spanCount
//                        // 右：(spanCount - 1 - column) * gap / spanCount
//                        outRect.left = column * gapPx / spanCount
//                        outRect.right = (spanCount - 1 - column) * gapPx / spanCount
//                        if (position >= spanCount) {
//                            outRect.top = gapPx
//                        }
//                    }
//                })
//            }
        }

        /**
         * 渲染点赞列表 + 评论列表
         */
        private fun renderInteractions(item: Moment) {
            val likes = item.likes.orEmpty()
            val comments = item.comments.orEmpty()
            val hasLikes = likes.isNotEmpty()
            val hasComments = comments.isNotEmpty()

            binding.llInteractions.visibility =
                if (hasLikes || hasComments) View.VISIBLE else View.GONE

            if (hasLikes) {
                binding.tvLikes.visibility = View.VISIBLE
                val names = likes.joinToString("，") { it.username?.ifBlank { null } ?: "用户${it.userId}" }
                binding.tvLikes.text = "❤  $names"
            } else {
                binding.tvLikes.visibility = View.GONE
            }

            binding.dividerLikesComments.visibility =
                if (hasLikes && hasComments) View.VISIBLE else View.GONE

            // 评论：动态生成 TextView
            binding.llComments.removeAllViews()
            comments.forEach { comment ->
                val tv = TextView(itemView.context).apply {
                    textSize = 13f
                    setTextColor(0xFF222222.toInt())
                    setPadding(0, 4, 0, 4)
                    val author = comment.username?.ifBlank { null }
                        ?: comment.user?.username?.ifBlank { null }
                        ?: "用户${comment.userId}"
                    val replyTo = comment.replyToUsername?.takeIf { it.isNotBlank() }
                    val cmtContent = comment.content ?: ""
                    text = if (replyTo == null) {
                        spanColored("$author：$cmtContent", author.length)
                    } else {
                        val prefix = "$author 回复 $replyTo："
                        spanColored("$prefix$cmtContent", prefix.length)
                    }
                }
                binding.llComments.addView(
                    tv,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }

        private fun spanColored(text: String, coloredEnd: Int): CharSequence {
            val builder = android.text.SpannableStringBuilder(text)
            builder.setSpan(
                android.text.style.ForegroundColorSpan(0xFF576B95.toInt()),
                0, coloredEnd.coerceAtMost(text.length),
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            return builder
        }

        private fun showActionMenu(item: Moment) {
            val ctx = itemView.context
            val anchor = binding.btnAction

            val menuView = LayoutInflater.from(ctx).inflate(R.layout.popup_moment_action, null)
            val popup = PopupWindow(
                menuView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                isOutsideTouchable = true
                elevation = 8f
            }

            // 点赞按钮
            val likeText = if (item.likedByMe) "取消" else "赞"
            val btnLike = menuView.findViewById<View>(R.id.btn_like)
            btnLike?.let {
                ((it as? ViewGroup)?.getChildAt(1) as? TextView)?.text = likeText
                it.setOnClickListener {
                    onLike(item)
                    popup.dismiss()
                }
            }

            // 评论按钮
            menuView.findViewById<View>(R.id.btn_comment)?.setOnClickListener {
                onComment(item)
                popup.dismiss()
            }

            // 测量 popup 宽度，计算偏移让其左对齐
            menuView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupWidth = menuView.measuredWidth
            val anchorWidth = anchor.width
            // x 偏移：让 popup 右边缘对齐 anchor 右边缘，再向左移一点
            val xOffset = anchorWidth - popupWidth - (4 * ctx.resources.displayMetrics.density).toInt()
            // y 偏移：显示在按钮上方
            val yOffset = -(anchor.height + menuView.measuredHeight + (4 * ctx.resources.displayMetrics.density).toInt())

            popup.showAsDropDown(anchor, xOffset, yOffset, Gravity.START or Gravity.TOP)

            // 微信风格动画：从右下角弹出（等 layout 完成后再设置 pivot）
            menuView.alpha = 0f
            menuView.scaleX = 0.85f
            menuView.scaleY = 0.85f
            menuView.translationY = 10 * ctx.resources.displayMetrics.density

            menuView.post {
                menuView.pivotX = menuView.width.toFloat()
                menuView.pivotY = menuView.height.toFloat()

                val alpha = ObjectAnimator.ofFloat(menuView, View.ALPHA, 0f, 1f)
                val scaleX = ObjectAnimator.ofFloat(menuView, View.SCALE_X, 0.85f, 1f)
                val scaleY = ObjectAnimator.ofFloat(menuView, View.SCALE_Y, 0.85f, 1f)
                val transY = ObjectAnimator.ofFloat(menuView, View.TRANSLATION_Y, 20f, 0f)

                AnimatorSet().apply {
                    playTogether(alpha, scaleX, scaleY, transY)
                    duration = 180
                    interpolator = android.view.animation.DecelerateInterpolator()
                    start()
                }
            }
        }
    }

    // ============================== DiffUtil ==============================

    private class MomentDiffCallback(
        private val oldList: List<Moment>,
        private val newList: List<Moment>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int) =
            oldList[oldPos].id == newList[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int) =
            oldList[oldPos] == newList[newPos]
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}

/**
 * 朋友圈九宫格图片 Adapter
 */
class MomentImageAdapter(
    private val images: List<String>,
    private val imageLoader: ImageLoader,
    private val onClick: (position: Int, view: ImageView) -> Unit,
) : RecyclerView.Adapter<MomentImageAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_moment_image, parent, false)
        val size = computeCellSize(parent, images.size)
        view.layoutParams = ViewGroup.LayoutParams(size, size)
        return VH(view)
    }

    override fun getItemCount(): Int = images.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val imageView = holder.itemView as ImageView
        imageView.loadImage(images[position], imageLoader) {
            transform = ImageRequest.Transform.CenterCrop
        }
        imageView.setOnClickListener { onClick(position, imageView) }
    }

    private fun computeCellSize(parent: ViewGroup, count: Int): Int {
        val displayMetrics = parent.context.resources.displayMetrics
        // 内容宽度 = 屏幕宽 - 头像宽(42) - 左右 padding(14*2) - 头像 margin(12)
        val maxWidth = displayMetrics.widthPixels -
                (42 + 14 * 2 + 12) * displayMetrics.density.toInt()
        val span = when {
            count == 1 -> return (maxWidth * 0.7f).toInt()
            count == 4 -> 2
            else -> 3
        }
        val gap = (4 * displayMetrics.density).toInt()
        return (maxWidth - gap * (span - 1)) / span
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView)
}


