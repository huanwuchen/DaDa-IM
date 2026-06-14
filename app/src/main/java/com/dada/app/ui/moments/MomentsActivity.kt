package com.dada.app.ui.moments

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.R
import com.dada.core.ui.base.BaseActivity
import com.dada.core.database.UserPreferences
import com.dada.core.network.model.Moment
import com.dada.app.databinding.ActivityMomentsBinding
import com.dada.app.widget.video.FullscreenVideoPlayerActivity
import com.dada.app.widget.imageviewer.StfalconImageViewer
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.loadImage
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.File

/**
 * 朋友圈主页(高仿微信)
 *
 * 布局:
 *  - 顶部透明 toolbar(覆盖在头图上,列表滚动时渐变为白色)
 *  - 列表第一项 = 头图 + 用户名 + 头像(HeaderViewHolder)
 *  - 之后是动态列表
 *  - 右上角发布按钮
 */
@AndroidEntryPoint
class MomentsActivity : BaseActivity<ActivityMomentsBinding>() {

    private val viewModel: MomentsViewModel by viewModels()

    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var imageLoader: ImageLoader

    private lateinit var momentsAdapter: MomentsAdapter

    private val pickCoverLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(::uploadCoverImage) }

    private val publishLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) viewModel.refresh()
    }

    override fun inflateBinding() = ActivityMomentsBinding.inflate(layoutInflater)

    override fun initView() {
        setupTransparentStatusBar()
        setupToolbar()
        setupList()
    }

    override fun initData() {
        observe(viewModel.uiState) { state ->
            momentsAdapter.submitList(state.list)
            binding.refreshLayout.isRefreshing = state.isRefreshing
        }
        observe(viewModel.coverUrl) { url ->
            momentsAdapter.updateCoverUrl(url)
        }
    }

    /** 沉浸式:状态栏透明 + 浅色图标(因为头图通常较暗) */
    private fun setupTransparentStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    private fun setupToolbar() {
        binding.tvTitle.setTextColor(Color.BLACK)
        binding.ivBack.setOnClickListener { finish() }
        binding.ivPublish.setOnClickListener {
            publishLauncher.launch(Intent(this, PublishMomentActivity::class.java))
        }
    }

    private fun setupList() {
        momentsAdapter = MomentsAdapter(
            myUserId = viewModel.myUserId,
            imageLoader = imageLoader,
            onLike = viewModel::toggleLike,
            onComment = ::showCommentInput,
            onDelete = ::confirmDelete,
            onImageClick = ::openImagePreview,
            onVideoClick = ::openVideoPlayer,
            onCoverClick = ::pickCoverImage,
            headerData = MomentsAdapter.HeaderData(
                username = viewModel.myUsername.ifBlank { "未登录" },
                avatarUrl = userPreferences.getUserAvatar(),
                coverUrl = userPreferences.getCoverImage(),
            ),
        )
        binding.rvMoments.apply {
            layoutManager = LinearLayoutManager(this@MomentsActivity)
            adapter = momentsAdapter
            itemAnimator = null
            addOnScrollListener(scrollListener)
        }
        binding.refreshLayout.setOnRefreshListener { viewModel.refresh() }
    }

    /**
     * 合并两个职责:
     *  - 列表滚动时让 toolbar 渐变(透明 -> 白色,图标白 -> 黑)
     *  - 滑到底部触发加载更多
     */
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        private var lastRatio = -1f

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            updateToolbarFade(rv)
            triggerLoadMoreIfNeeded(rv, dy)
        }

        private fun updateToolbarFade(rv: RecyclerView) {
            val firstView = rv.layoutManager?.findViewByPosition(0)
            val ratio = if (firstView == null) 1f else {
                val total = (firstView.height - binding.toolbar.height).coerceAtLeast(1)
                val scrolled = (-firstView.top).coerceAtLeast(0)
                (scrolled.toFloat() / total).coerceIn(0f, 1f)
            }
            // 缓存上一次 ratio,避免每帧重复触发 setColorFilter / 状态栏 API
            if (ratio == lastRatio) return
            lastRatio = ratio

            binding.toolbar.setBackgroundColor(
                ColorUtils.blendARGB(Color.TRANSPARENT, Color.WHITE, ratio)
            )
            binding.tvTitle.alpha = ratio
            val iconTint = ColorUtils.blendARGB(Color.WHITE, Color.BLACK, ratio)
            binding.ivBack.setColorFilter(iconTint)
            binding.ivPublish.setColorFilter(iconTint)
            WindowInsetsControllerCompat(window, window.decorView)
                .isAppearanceLightStatusBars = ratio > 0.5f
        }

        private fun triggerLoadMoreIfNeeded(rv: RecyclerView, dy: Int) {
            if (dy <= 0) return
            val lm = rv.layoutManager as? LinearLayoutManager ?: return
            if (lm.findLastVisibleItemPosition() >= lm.itemCount - 2) viewModel.loadMore()
        }
    }

    /** 底部弹窗评论输入(仿微信) */
    private fun showCommentInput(moment: Moment) {
        val dialog = BottomSheetDialog(this, R.style.Theme_BottomSheetDialog)
        dialog.setContentView(R.layout.dialog_comment_input)
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        val etComment = dialog.findViewById<EditText>(R.id.et_comment) ?: return
        val btnSend = dialog.findViewById<TextView>(R.id.btn_send)

        etComment.requestFocus()

        val submit = {
            val content = etComment.text.toString().trim()
            if (content.isNotBlank()) viewModel.comment(moment, content)
            dialog.dismiss()
        }
        btnSend?.setOnClickListener { submit() }
        etComment.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { submit(); true } else false
        }
        dialog.show()
    }

    private fun confirmDelete(moment: Moment) {
        AlertDialog.Builder(this)
            .setMessage("确定删除该动态？")
            .setPositiveButton("删除") { _, _ -> viewModel.delete(moment) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openImagePreview(urls: List<String>, position: Int, view: ImageView) {
        StfalconImageViewer.Builder(view.context, urls) { imageView, url ->
            imageView.loadImage(url, imageLoader)
        }
            .withStartPosition(position)
            .withTransitionFrom(view)
            .withHiddenStatusBar(true)
            .allowSwipeToDismiss(true)
            .allowZooming(true)
            .show()
    }

    private fun openVideoPlayer(url: String) {
        if (url.isBlank()) return
        FullscreenVideoPlayerActivity.start(
            context = this,
            videoUrl = url,
            title = "朋友圈视频"
        )
    }

    private fun pickCoverImage() {
        pickCoverLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun uploadCoverImage(uri: Uri) {
        lifecycleScope.launch {
            val tmpFile = runCatching {
                withContext(Dispatchers.IO) {
                    val target = File(cacheDir, "cover_${System.currentTimeMillis()}.jpg")
                    contentResolver.openInputStream(uri).use { input ->
                        if (input == null) error("无法读取所选图片")
                        target.outputStream().use { input.copyTo(it) }
                    }
                    target
                }
            }.getOrElse { e ->
                Toast.makeText(this@MomentsActivity, "上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
                return@launch
            }
            viewModel.uploadCover(tmpFile)
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, MomentsActivity::class.java))
        }
    }
}
