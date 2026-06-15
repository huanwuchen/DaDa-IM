package com.dada.app.ui.contacts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.dada.app.R
import com.dada.core.ui.base.BaseFragment
import com.dada.app.databinding.FragmentContactsBinding
import com.dada.app.ui.chat.WxChatActivity
import com.dada.app.ui.discover.OnlineUsersActivity
import com.dada.core.common.utils.LogUtil
import com.dada.core.common.utils.ToastUtil
import com.dada.core.imageloader.ImageLoader
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通讯录 Tab
 *
 * 数据流：
 *  - 列表来自好友列表接口
 *  - 进入页面 + onResume 时自动 refresh
 *
 * 交互：
 *  - 点击列表项 -> 进入 [WxChatActivity]
 *  - 点击顶部「发现」-> 进入在线用户页面
 */
@AndroidEntryPoint
class ContactsFragment : BaseFragment<FragmentContactsBinding>() {

    private val viewModel: ContactsViewModel by viewModels()
    private lateinit var adapter: ContactsAdapter

    @Inject lateinit var imageLoader: ImageLoader

    override fun inflateBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentContactsBinding = FragmentContactsBinding.inflate(inflater, container, false)

    override fun initView() {
        setupRecyclerView()
        setupListeners()
    }

    override fun initData() {
        // 通用副作用（错误 -> Toast）
        bindBaseViewModel(viewModel)

        // 订阅好友列表
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.friends.asFlow().collect { list ->
                    adapter.submitList(list)
                }
            }
        }

        // 订阅待处理好友请求数量，更新红点和底部 Tab 徽标
        viewModel.pendingRequestCount.observe(viewLifecycleOwner) { count ->
            LogUtil.d("ContactsFragment", "pendingRequestCount changed: $count")
            binding.dotFriendRequests.visibility = if (count > 0) View.VISIBLE else View.GONE

            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
            if (bottomNav == null) {
                LogUtil.e("ContactsFragment", "bottomNav is null")
                return@observe
            }
            if (count > 0) {
                bottomNav.getOrCreateBadge(R.id.nav_contacts).apply {
                    isVisible = true
                    number = count
                    backgroundColor = android.graphics.Color.parseColor("#FF4444")
                    badgeTextColor = android.graphics.Color.WHITE
                    maxCharacterCount = 3
                }
            } else {
                bottomNav.removeBadge(R.id.nav_contacts)
            }
        }
    }



    // ============================== 初始化 ==============================

    private fun setupRecyclerView() {
        adapter = ContactsAdapter(imageLoader) { friend ->
            startActivity(
                Intent(requireContext(), WxChatActivity::class.java).apply {
                    putExtra(WxChatActivity.EXTRA_TARGET_USER_ID, friend.id)
                    putExtra(WxChatActivity.EXTRA_TARGET_USERNAME, friend.username)
                    putExtra(WxChatActivity.EXTRA_TARGET_AVATAR, friend.avatar)
                }
            )
        }
        binding.rvContacts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ContactsFragment.adapter
        }
    }

    private fun setupListeners() {
        // 发现按钮 - 跳转到在线用户页面
        binding.btnDiscover.setOnClickListener {
            val userId = viewModel.getCurrentUserId()
            OnlineUsersActivity.start(requireContext(), userId)
        }

        // 好友请求按钮
        binding.btnFriendRequests?.setOnClickListener {
            val userId = viewModel.getCurrentUserId()
            com.dada.app.ui.friend.FriendRequestsActivity.start(requireContext(), userId)
        }
    }


    override fun onResume() {
        super.onResume()
        // 每次回到该页面去服务器刷新一次
        viewModel.refresh()
    }

}
