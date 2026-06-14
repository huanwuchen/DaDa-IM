package com.dada.app.ui.discover

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.dada.app.databinding.ActivityOnlineUsersBinding
import com.dada.app.ui.profile.UserProfileActivity
import com.dada.core.imageloader.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 在线用户发现页面
 */
@AndroidEntryPoint
class OnlineUsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineUsersBinding
    private val viewModel: OnlineUsersViewModel by viewModels()
    private lateinit var adapter: OnlineUsersAdapter

    @Inject lateinit var imageLoader: ImageLoader

    private var currentUserId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUserId = intent.getLongExtra(EXTRA_USER_ID, 0)

        setupViews()
        setupRecyclerView()
        observeViewModel()

        viewModel.loadOnlineUsers()
    }

    private fun setupViews() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadOnlineUsers()
        }
    }

    private fun setupRecyclerView() {
        adapter = OnlineUsersAdapter(
            imageLoader = imageLoader,
            onItemClick = { user ->
                // 查看用户信息
                UserProfileActivity.start(this, user.id, editable = false)
            },
            onAddFriendClick = { user ->
                // 添加好友
                showAddFriendDialog(user.id, user.username)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@OnlineUsersActivity)
            adapter = this@OnlineUsersActivity.adapter
        }
    }

    private fun observeViewModel() {
        viewModel.onlineUsers.observe(this) { users ->
            adapter.submitList(users)
            binding.tvEmpty.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.loading.observe(this) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.error.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        viewModel.addFriendSuccess.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddFriendDialog(toUserId: Long, username: String) {
        val editText = android.widget.EditText(this).apply {
            hint = "验证消息（可选）"
        }

        AlertDialog.Builder(this)
            .setTitle("添加好友")
            .setMessage("发送好友请求给 $username")
            .setView(editText)
            .setPositiveButton("发送") { _, _ ->
                val message = editText.text.toString().trim()
                viewModel.addFriend(currentUserId, toUserId, message.ifEmpty { null })
            }
            .setNegativeButton("取消", null)
            .show()
    }

    companion object {
        private const val EXTRA_USER_ID = "extra_user_id"

        fun start(context: Context, userId: Long) {
            val intent = Intent(context, OnlineUsersActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
            }
            context.startActivity(intent)
        }
    }
}
