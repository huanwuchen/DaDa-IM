package com.dada.app.ui.contacts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dada.core.common.base.BaseViewModel
import com.dada.core.common.utils.LogUtil
import com.dada.core.database.UserPreferences
import com.dada.core.network.model.FriendInfo
import com.dada.domain.friend.repository.FriendRepository
import com.dada.domain.contact.repository.ImContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 通讯录 ViewModel（使用好友列表接口）
 */
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val imContactRepository: ImContactRepository,
    private val userPreferences: UserPreferences
) : BaseViewModel() {

    private val _friends = MutableLiveData<List<FriendInfo>>()
    val friends: LiveData<List<FriendInfo>> = _friends

    private val _pendingRequestCount = MutableLiveData(0)
    val pendingRequestCount: LiveData<Int> = _pendingRequestCount

    init {
        // 首次创建时加载好友列表
        refresh()
    }

    /**
     * 刷新好友列表
     */
    fun refresh() {
        launch {
            val userId = userPreferences.getUserId()
            if (userId <= 0) {
                postError("请先登录")
                return@launch
            }

            val result = friendRepository.getFriendList(userId)
            result.onSuccess {
                _friends.value = it
                // 同步写入 im_contacts，保证聊天时能拿到好友头像/昵称
                launch { imContactRepository.saveFriends(it) }
            }.onFailure {
                postError("加载失败: ${it.message}")
            }
        }
        loadPendingRequestCount()
    }

    /**
     * 加载待处理的好友请求数量
     */
    private fun loadPendingRequestCount() {
        launch {
            val userId = userPreferences.getUserId()
            if (userId <= 0) return@launch
            val result = friendRepository.getFriendRequests(userId)
            result.onSuccess { requests ->
                val pending = requests.count { it.status == 0 }
                LogUtil.d("ContactsVM", "好友请求总数=${requests.size}, 待处理=$pending")
                _pendingRequestCount.value = pending
            }.onFailure {
                LogUtil.e("ContactsVM", "获取好友请求失败: ${it.message}")
            }
        }
    }

    /**
     * 获取当前用户ID
     */
    fun getCurrentUserId(): Long {
        return userPreferences.getUserId()
    }
}
