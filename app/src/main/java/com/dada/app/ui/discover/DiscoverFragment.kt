package com.dada.app.ui.discover

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import com.dada.core.ui.base.BaseFragment
import com.dada.app.databinding.FragmentDiscoverBinding
import com.dada.app.ui.moments.MomentsActivity
import com.dada.app.ui.scan.ScanActivity

/**
 * 发现 Tab
 *
 * 已接入：
 *  - 朋友圈：跳转到 [MomentsActivity]
 *  - 扫一扫：跳转到 [ScanActivity]
 */
class DiscoverFragment : BaseFragment<FragmentDiscoverBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentDiscoverBinding = FragmentDiscoverBinding.inflate(inflater, container, false)

    override fun initView() {
        binding.itemMoments.setOnClickListener {
            startActivity(Intent(requireContext(), MomentsActivity::class.java))
        }
        binding.itemScan.setOnClickListener {
            startActivity(Intent(requireContext(), ScanActivity::class.java))
        }
    }

    override fun initData() {
        // 当前页面无外部数据
    }

    companion object {
        fun newInstance() = DiscoverFragment()
    }
}
