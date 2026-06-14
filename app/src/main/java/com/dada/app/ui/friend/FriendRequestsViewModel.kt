package com.dada.app.ui.friend

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dada.core.network.model.FriendRequest
import com.dada.domain.friend.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 好友请求 ViewModel
 */
@HiltViewModel
class FriendRequestsViewModel @Inject constructor(
    private val friendRepository: FriendRepository
) : ViewModel() {

    private val _friendRequests = MutableLiveData<List<FriendRequest>>()
    val friendRequests: LiveData<List<FriendRequest>> = _friendRequests

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _actionSuccess = MutableLiveData<String>()
    val actionSuccess: LiveData<String> = _actionSuccess

    /**
     * 加载好友请求列表
     */
    fun loadFriendRequests(userId: Long) {
        viewModelScope.launch {
            _loading.value = true
            val result = friendRepository.getFriendRequests(userId)
            _loading.value = false

            result.onSuccess {
                _friendRequests.value = it
            }.onFailure {
                _error.value = it.message ?: "加载失败"
            }
        }
    }

    /**
     * 同意好友请求
     */
    fun acceptRequest(requestId: Long, userId: Long) {
        viewModelScope.launch {
            _loading.value = true
            val result = friendRepository.acceptFriendRequest(requestId, userId)
            _loading.value = false

            result.onSuccess {
                _actionSuccess.value = it
            }.onFailure {
                _error.value = it.message ?: "操作失败"
            }
        }
    }

    /**
     * 拒绝好友请求
     */
    fun rejectRequest(requestId: Long, userId: Long) {
        viewModelScope.launch {
            _loading.value = true
            val result = friendRepository.rejectFriendRequest(requestId, userId)
            _loading.value = false

            result.onSuccess {
                _actionSuccess.value = it
            }.onFailure {
                _error.value = it.message ?: "操作失败"
            }
        }
    }
}
