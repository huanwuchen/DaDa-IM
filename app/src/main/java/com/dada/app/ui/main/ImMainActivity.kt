package com.dada.app.ui.main

import android.os.Bundle
import android.os.PersistableBundle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.dada.app.R
import com.dada.core.ui.base.BaseActivity
import com.dada.app.databinding.ActivityImMainBinding
import com.dada.core.common.utils.LogUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * IM 主页面（仿微信首页）
 *
 * 功能：
 *  - 通过底部 BottomNavigationView 切换 4 个 Tab
 *    1. 消息（ChatListFragment）
 *    2. 通讯录（ContactsFragment）
 *    3. 发现（DiscoverFragment）
 *    4. 我（MeFragment）
 *
 * 实现要点：
 *  - 使用 Navigation Component 现代化方案
 *  - 自动管理 Fragment 生命周期和状态保存
 *  - BottomNavigationView 与 NavController 自动绑定
 */
@AndroidEntryPoint
class ImMainActivity : BaseActivity<ActivityImMainBinding>() {

    override fun inflateBinding(): ActivityImMainBinding {
        return ActivityImMainBinding.inflate(layoutInflater)
    }

    override fun initView() {
        setupNavigation()
    }

    override fun initData() {
        // 当前页面无额外初始化数据


    }

    /**
     * 设置 Navigation Component
     * 使用现代化的 NavController + BottomNavigationView 方案
     */
    private fun setupNavigation() {
        // 获取 NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // 将 BottomNavigationView 与 NavController 绑定
        // 自动处理 Tab 切换和 Fragment 导航
        binding.bottomNavigation.setupWithNavController(navController)

        // 可选：监听导航目的地变化
        // navController.addOnDestinationChangedListener { _, destination, _ ->
        //     // 根据不同的 destination 做一些处理
        //     // 例如：隐藏/显示底部导航栏
        // }
    }

}
