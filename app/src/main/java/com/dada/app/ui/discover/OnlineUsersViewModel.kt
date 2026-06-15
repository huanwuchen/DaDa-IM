package com.dada.app.ui.discover

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dada.core.common.base.BaseViewModel
import com.dada.core.database.UserPreferences
import com.dada.core.network.model.OnlineUser
import com.dada.domain.friend.repository.FriendRepository
import com.dada.domain.user.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 在线用户发现 ViewModel
 */
@HiltViewModel
class OnlineUsersViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val friendRepository: FriendRepository,
    private val userPreferences: UserPreferences
) : BaseViewModel() {

    private val _onlineUsers = MutableLiveData<List<OnlineUser>>()
    val onlineUsers: LiveData<List<OnlineUser>> = _onlineUsers

    /** 已是好友的用户 ID 集合，供 Adapter 判断按钮状态 */
    private val _friendIds = MutableLiveData<Set<Long>>()
    val friendIds: LiveData<Set<Long>> = _friendIds

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _addFriendSuccess = MutableLiveData<String>()
    val addFriendSuccess: LiveData<String> = _addFriendSuccess

    /**
     * 加载在线用户，同时获取好友列表进行过滤
     */
    fun loadOnlineUsers() {
        launch {
            _loading.value = true

            // 并行获取在线用户和好友列表
            val onlineResult = userProfileRepository.getOnlineUsers()
            val currentUserId = userPreferences.getUserId()
            val friendResult = friendRepository.getFriendList(currentUserId)

            _loading.value = false

            val friendIdSet = friendResult.getOrNull()?.map { it.id }?.toSet() ?: emptySet()
            _friendIds.value = friendIdSet

            onlineResult.onSuccess { users ->
                // 过滤掉自己和已经是好友的用户
                val filtered = users.filter { it.id != currentUserId && it.id !in friendIdSet }
                _onlineUsers.value = filtered
            }.onFailure {
                _error.value = it.message ?: "加载失败"
            }
        }
    }

    /**
     * 添加好友
     */
    fun addFriend(fromUserId: Long, toUserId: Long, message: String?) {
        launch {
            _loading.value = true
            val result = friendRepository.sendFriendRequest(fromUserId, toUserId, message)
            _loading.value = false

            result.onSuccess {
                _addFriendSuccess.value = it
            }.onFailure {
                _error.value = it.message ?: "发送失败"
            }
        }
    }
}
