package com.dada.app.ui

import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.dada.app.R
import com.dada.app.databinding.ActivityMainBinding
import com.dada.app.ui.chatlist.ChatListFragment
import com.dada.app.ui.contacts.ContactsFragment
import com.dada.app.ui.discover.DiscoverFragment
import com.dada.app.ui.settings.MeFragment
import com.dada.core.common.utils.GsonUtil
import com.dada.core.common.utils.LogUtil
import com.dada.core.common.utils.ToastUtil
import com.dada.core.imageloader.ImageLoader
import com.dada.core.ui.base.BaseActivity
import com.dada.core.ui.base.BaseFragment
import com.dada.domain.moment.repository.MomentRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>() {

    private var currentFragment: Fragment? = null

    @Inject
    lateinit var momentRepository: MomentRepository

    private val tabs by lazy {
        listOf(
            TabItem(binding.navChats) { BaseFragment.newInstance<ChatListFragment>() },
            TabItem(binding.navContacts) { BaseFragment.newInstance<ContactsFragment>() },
            TabItem(binding.navDiscover) { BaseFragment.newInstance<DiscoverFragment>() },
            TabItem(binding.navMe) { BaseFragment.newInstance<MeFragment>() }
        )
    }

    override fun inflateBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun initView() {
        tabs.forEachIndexed { index, tab ->
            tab.navView.setOnClickListener { switchTab(index) }
        }
        switchTab(0)
    }

    override fun initData() {

    }


    private fun switchTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        val tag = "tab_$index"

        val transaction = supportFragmentManager.beginTransaction()
        val target = supportFragmentManager.findFragmentByTag(tag)
            ?: tab.creator().also { transaction.add(R.id.fl_container, it, tag) }

        currentFragment?.let { transaction.hide(it) }
        transaction.show(target).commit()
        currentFragment = target

        tabs.forEachIndexed { i, t -> t.navView.isSelected = i == index }
    }

    private data class TabItem(val navView: View, val creator: () -> Fragment)
}
