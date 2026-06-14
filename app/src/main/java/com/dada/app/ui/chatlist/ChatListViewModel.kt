package com.dada.app.ui.chatlist

import androidx.lifecycle.viewModelScope
import com.dada.core.common.base.BaseViewModel
import com.dada.domain.chat.repository.ImChatRepository
import com.dada.domain.contact.repository.ImContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 首页消息列表 ViewModel
 *
 * 数据来源：im_conversations 表，按最后消息时间倒序。
 * 任何会话被新增或更新（在 [ImChatRepository] 中），首页都会自动刷新。
 */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ImChatRepository,
    private val contactRepository: ImContactRepository,
) : BaseViewModel() {

    val conversations: StateFlow<List<ChatListItem>> =
        chatRepository.observeConversations()
            .map { list -> list.map { it.toChatListItem() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // 会话列表变化时，批量刷新对方用户信息（头像/昵称）
        // distinctUntilChanged 避免 refresh 更新会话表后重复触发
        chatRepository.observeConversations()
            .map { list -> list.map { it.peerId }.sorted() }
            .distinctUntilChanged()
            .onEach { peerIds ->
                if (peerIds.isNotEmpty()) {
                    contactRepository.batchRefreshContacts(peerIds)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * 删除某个会话（消息明细 + 列表条目）
     */
    fun deleteConversation(peerId: Long) {
        launch { chatRepository.deleteConversation(peerId) }
    }
}
