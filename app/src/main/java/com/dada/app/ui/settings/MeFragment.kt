package com.dada.app.ui.settings

import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dada.app.R
import com.dada.core.ui.base.BaseFragment
import com.dada.app.databinding.FragmentMeBinding
import com.dada.app.ui.profile.UserProfileActivity
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.loadImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.jvm.java

/**
 * 「我」Tab
 *
 * 数据：从 Room 订阅当前用户资料（[MeViewModel.profile]），
 *      由注册流程负责写入；未登录时显示占位
 *
 * 交互：个人信息 / 设置入口（暂未实现）
 */
@AndroidEntryPoint
class MeFragment : BaseFragment<FragmentMeBinding>() {

    private val viewModel: MeViewModel by viewModels()

    @Inject lateinit var imageLoader: ImageLoader

    override fun inflateBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentMeBinding = FragmentMeBinding.inflate(inflater, container, false)

    override fun initView() {
        setupListeners()
    }

    override fun initData() {
        bindBaseViewModel(viewModel)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.profile.collect { profile ->
                    if (profile == null) {
                        binding.tvNickname.text = "未登录"
                        binding.tvUserId.text = "ID: -"
                        binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
                        return@collect
                    }

                    binding.tvNickname.text = profile.username.takeIf { it.isNotBlank() } ?: "未登录"
                    binding.tvUserId.text = profile.userId.takeIf { it > 0 }
                        ?.let { "ID: $it" } ?: "ID: -"

                    // 加载头像
                    loadAvatar(profile.avatar)
                }
            }
        }
    }

    private fun loadAvatar(avatarUrl: String?) {
        val finalUrl = if (avatarUrl.isNullOrBlank()) null else avatarUrl
        binding.ivAvatar.loadImage(finalUrl, imageLoader) {
            placeholder = R.drawable.ic_default_avatar
            error = R.drawable.ic_default_avatar
            asCircle = true
        }
    }

    private fun setupListeners() {
        binding.itemProfile.setOnClickListener {
            // Toast.makeText(requireContext(), "个人信息", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), UserProfileActivity::class.java)
            intent.putExtra(UserProfileActivity.EXTRA_USER_ID, viewModel.profile.value?.userId)
            intent.putExtra(UserProfileActivity.EXTRA_EDITABLE, true)
            startActivity(intent)

        }
        binding.itemSettings.setOnClickListener {
            Toast.makeText(requireContext(), "设置", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun newInstance() = MeFragment()
    }
}
