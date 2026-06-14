package com.dada.app.ui.aichat

import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.dada.app.databinding.ActivityAiChatBinding
import com.dada.core.ui.base.BaseActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AiChatActivity : BaseActivity<ActivityAiChatBinding>() {

    private val viewModel: AiChatViewModel by viewModels()
    private lateinit var adapter: AiChatAdapter

    private var previousMessageCount = 0

    override fun inflateBinding(): ActivityAiChatBinding =
        ActivityAiChatBinding.inflate(layoutInflater)

    override fun initView() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = AiChatAdapter()
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@AiChatActivity).apply {
                stackFromEnd = true
            }
            adapter = this@AiChatActivity.adapter
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                binding.etMessage.text.clear()
                viewModel.sendMessage(text)
            }
        }
    }

    override fun initData() {
        bindBaseViewModel(viewModel)

        observe(viewModel.uiState) { state ->
            val newCount = state.messages.size
            adapter.submitList(state.messages.toList())

            when {
                // 新消息插入
                newCount > previousMessageCount -> {
                    binding.rvMessages.scrollToPosition(newCount - 1)
                }
                // 流式更新最后一条
                newCount == previousMessageCount && newCount > 0 && state.isStreaming -> {
                    adapter.updateLastHolderContent(state.messages.last())
                    binding.rvMessages.scrollToPosition(newCount - 1)
                }
                // 流结束最终同步
                newCount == previousMessageCount && newCount > 0 && !state.isStreaming -> {
                    adapter.notifyItemChanged(newCount - 1)
                    binding.rvMessages.smoothScrollToPosition(newCount - 1)
                }
            }
            previousMessageCount = newCount
        }

        observe(viewModel.effect) { effect ->
            when (effect) {
                is AiChatEffect.ShowToast ->
                    Toast.makeText(this, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.cancelRequest()
    }
}
