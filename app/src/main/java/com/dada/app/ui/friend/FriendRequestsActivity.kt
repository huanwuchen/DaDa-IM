package com.dada.app.ui.friend

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.dada.app.databinding.ActivityFriendRequestsBinding
import com.dada.core.imageloader.ImageLoader
import com.dada.core.ui.base.BaseActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 好友请求管理页面
 */
@AndroidEntryPoint
class FriendRequestsActivity : BaseActivity<ActivityFriendRequestsBinding>() {

    private val viewModel: FriendRequestsViewModel by viewModels()
    private lateinit var adapter: FriendRequestsAdapter

    @Inject lateinit var imageLoader: ImageLoader

    private var currentUserId: Long = 0
    override fun inflateBinding(): ActivityFriendRequestsBinding {
         return ActivityFriendRequestsBinding.inflate(layoutInflater)
    }

    override fun initView() {
        currentUserId = intent.getLongExtra(EXTRA_USER_ID, 0)

        if (currentUserId == 0L) {
            Toast.makeText(this, "用户ID错误", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews()
        setupRecyclerView()
    }

    override fun initData() {
        observeViewModel()

        viewModel.loadFriendRequests(currentUserId)
    }


    private fun setupViews() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadFriendRequests(currentUserId)
        }
    }

    private fun setupRecyclerView() {
        adapter = FriendRequestsAdapter(
            imageLoader = imageLoader,
            onAcceptClick = { request ->
                viewModel.acceptRequest(request.id, currentUserId)
            },
            onRejectClick = { request ->
                viewModel.rejectRequest(request.id, currentUserId)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@FriendRequestsActivity)
            adapter = this@FriendRequestsActivity.adapter
        }
    }

    private fun observeViewModel() {
        viewModel.friendRequests.observe(this) { requests ->
            adapter.submitList(requests)
            binding.tvEmpty.visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.loading.observe(this) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.error.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        viewModel.actionSuccess.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            // 刷新列表
            viewModel.loadFriendRequests(currentUserId)
        }
    }

    companion object {
        private const val EXTRA_USER_ID = "extra_user_id"

        fun start(context: Context, userId: Long) {
            val intent = Intent(context, FriendRequestsActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
            }
            context.startActivity(intent)
        }
    }
}
