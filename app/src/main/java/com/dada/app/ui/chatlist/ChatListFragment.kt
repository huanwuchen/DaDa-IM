package com.dada.app.ui.chatlist

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.lifecycle.lifecycleScope
import com.dada.app.R
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.dada.core.ui.base.BaseFragment
import com.dada.app.databinding.FragmentChatListBinding
import com.dada.app.ui.aichat.AiChatActivity
import com.dada.app.ui.chat.WxChatActivity
import com.dada.app.ui.discover.OnlineUsersActivity
import com.dada.app.ui.scan.ScanActivity
import com.dada.core.common.utils.AppForegroundTracker
import com.dada.core.imageloader.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 消息列表 Tab（首页）
 *
 * 数据：从 Room 订阅会话列表（im_conversations），只有真正聊过天的用户才会出现
 * 交互：点击列表项进入对应聊天页
 */
@AndroidEntryPoint
class ChatListFragment : BaseFragment<FragmentChatListBinding>() {

    private val viewModel: ChatListViewModel by viewModels()
    private lateinit var adapter: ChatListAdapter

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var userPreferences: com.dada.core.database.UserPreferences

    override fun inflateBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentChatListBinding = FragmentChatListBinding.inflate(inflater, container, false)

    override fun initView() {
        setupRecyclerView()
        setupListeners()
    }

    override fun initData() {
        bindBaseViewModel(viewModel)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.conversations.collect { list ->
                    adapter.submitList(list)
                    // 空列表时给一个简单的提示（如果布局里有 tvEmpty 可以打开）
                    binding.rvChats.visibility = if (list.isEmpty()) View.VISIBLE else View.VISIBLE
                }
            }
        }

    }

    // ============================== 初始化 ==============================

    private fun setupRecyclerView() {
        adapter = ChatListAdapter(imageLoader) { item ->
            startActivity(
                Intent(requireContext(), WxChatActivity::class.java).apply {
                    putExtra(WxChatActivity.EXTRA_TARGET_USER_ID, item.peerId)
                    putExtra(WxChatActivity.EXTRA_TARGET_USERNAME, item.peerUsername)
                    putExtra(WxChatActivity.EXTRA_TARGET_AVATAR, item.peerAvatar)
                }
            )
        }
        binding.rvChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChatListFragment.adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL).apply {
                AppCompatResources.getDrawable(requireContext(), R.drawable.bg_chat_list_divider)?.let { setDrawable(it) }
            })
        }
    }

    private fun setupListeners() {
        binding.ivAdd.setOnClickListener { showAddPopup(it) }
        binding.llAiAssistant.setOnClickListener {
            startActivity(Intent(requireContext(), AiChatActivity::class.java))
        }
    }

    private fun showAddPopup(anchor: View) {
        val ctx = requireContext()
        val menuView = LayoutInflater.from(ctx).inflate(R.layout.popup_chat_add, null)
        val popup = PopupWindow(
            menuView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = 8f
        }

        menuView.findViewById<View>(R.id.btn_add_friend)?.setOnClickListener {
            popup.dismiss()
            OnlineUsersActivity.start(ctx, userPreferences.getUserId())
        }
        menuView.findViewById<View>(R.id.btn_scan)?.setOnClickListener {
            popup.dismiss()
            startActivity(Intent(ctx, ScanActivity::class.java))
        }

        // Measure and position: right-aligned to anchor, below it
        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = menuView.measuredWidth
        val anchorWidth = anchor.width
        val xOffset = anchorWidth - popupWidth - (4 * ctx.resources.displayMetrics.density).toInt()
        val yOffset = (4 * ctx.resources.displayMetrics.density).toInt()

        popup.showAsDropDown(anchor, xOffset, yOffset, Gravity.START or Gravity.TOP)

        // WeChat-style animation: pop from top-right corner
        menuView.alpha = 0f
        menuView.scaleX = 0.85f
        menuView.scaleY = 0.85f
        menuView.translationY = -10 * ctx.resources.displayMetrics.density

        menuView.post {
            menuView.pivotX = menuView.width.toFloat()
            menuView.pivotY = 0f

            val alpha = ObjectAnimator.ofFloat(menuView, View.ALPHA, 0f, 1f)
            val scaleX = ObjectAnimator.ofFloat(menuView, View.SCALE_X, 0.85f, 1f)
            val scaleY = ObjectAnimator.ofFloat(menuView, View.SCALE_Y, 0.85f, 1f)
            val transY = ObjectAnimator.ofFloat(menuView, View.TRANSLATION_Y, -20f, 0f)

            AnimatorSet().apply {
                playTogether(alpha, scaleX, scaleY, transY)
                duration = 180
                interpolator = android.view.animation.DecelerateInterpolator()
                start()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 标记进入聊天列表页面
        AppForegroundTracker.setInChatListPage(true)
    }

    override fun onPause() {
        super.onPause()
        // 标记离开聊天列表页面
        AppForegroundTracker.setInChatListPage(false)
    }

    companion object {

        fun newInstance(): ChatListFragment {
            val fragment = ChatListFragment()
            val bundle = Bundle()
            fragment.setArguments(bundle)
            return fragment
        }
    }


}
