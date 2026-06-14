package com.dada.app.ui.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dada.app.R
import com.dada.core.common.base.BaseViewModel
import com.dada.domain.chat.model.ChatPeer
import com.dada.domain.chat.repository.ImChatRepository
import com.dada.domain.contact.repository.ImContactRepository
import com.dada.app.domain.SendMessageUseCase
import com.dada.app.network.call.CallManager
import com.dada.app.network.call.CallState
import com.dada.app.network.call.CallType
import com.dada.core.database.UserPreferences
import com.dada.core.network.websocket.WebSocketManager
import com.dada.core.common.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

/**
 * 聊天页 ViewModel（3.4 单一 UiState 重构）
 *
 * 数据流：
 *  - Room 订阅消息列表
 *  - WebSocket 连接状态 / CallManager 状态
 *  - combine 后聚合为 [WxChatUiState]
 *
 * 副作用（发送失败/导航等）通过 [effect] 一次性事件流暴露。
 */
@HiltViewModel
class WxChatViewModel @Inject constructor(
    private val chatRepository: ImChatRepository,
    private val contactRepository: ImContactRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val userPreferences: UserPreferences,
    private val webSocketManager: WebSocketManager,
    private val callManager: CallManager,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

    /** 我的用户 ID */
    val myUserId: Long = userPreferences.getUserId()

    /** 对方用户 ID（必填） */
    val targetUserId: Long =
        savedStateHandle.get<Long>(WxChatActivity.EXTRA_TARGET_USER_ID) ?: 0L

    /** 对方用户名 */
    val targetUsername: String =
        savedStateHandle.get<String>(WxChatActivity.EXTRA_TARGET_USERNAME) ?: "未知用户"

    /** 对方头像（Intent 传入的初始值，init 中会用数据库最新值覆盖） */
    @Volatile
    private var resolvedAvatar: String? =
        savedStateHandle.get<String>(WxChatActivity.EXTRA_TARGET_AVATAR)

    init {
        if (targetUserId > 0L) {
            // 进入会话清零未读
            launch { chatRepository.markAsRead(targetUserId) }

            launchIO {
                // 优先从数据库读取最新联系人信息（batchRefreshContacts 可能已更新）
                val latestContact = contactRepository.getContact(targetUserId)
                val avatar = latestContact?.avatar ?: resolvedAvatar
                val username = latestContact?.username?.takeIf { it.isNotBlank() } ?: targetUsername

                // 更新本地引用，后续发送消息时使用最新头像
                resolvedAvatar = avatar

                // 写入联系人表（用最新值，避免 Intent 旧值覆盖）
                contactRepository.upsert(
                    com.dada.core.database.entity.ImContactEntity(
                        id = targetUserId,
                        username = username,
                        avatar = avatar,
                        updateTime = System.currentTimeMillis(),
                    )
                )

                // 补全历史消息中头像为空的记录
                if (!avatar.isNullOrBlank()) {
                    chatRepository.updateMessageAvatar(targetUserId, avatar)
                }

                // 同步更新本地 UI 状态中的头像和昵称
                _localState.value = _localState.value.copy(
                    peer = _localState.value.peer.copy(
                        username = username,
                        avatar = avatar,
                    )
                )
            }

            // 监听通话结束，自动插入通话提示消息
            callManager.callState
                .drop(1)
                .onEach { state ->
                    if (state == CallState.IDLE) onCallEnded()
                }
                .launchIn(viewModelScope)
        }
    }

    // ============================== 一次性副作用通道 ==============================

    private val _effect = MutableSharedFlow<WxChatEffect>(extraBufferCapacity = 4)
    val effect: SharedFlow<WxChatEffect> = _effect.asSharedFlow()

    // ============================== 单一 UiState ==============================

    /** 本地 UI 状态（仅 ViewModel 内部可写） */
    private val _localState = MutableStateFlow(
        WxChatUiState(
            peer = WxChatUiState.PeerHeader(
                id = targetUserId,
                username = targetUsername,
                avatar = resolvedAvatar,
            ),
        )
    )

    /**
     * 对外暴露的单一 UiState（聚合 Room + WebSocket + 本地状态）。
     */
    val uiState: StateFlow<WxChatUiState> = combine(
        if (targetUserId > 0L) chatRepository.observeMessages(targetUserId) else emptyFlow(),
        webSocketManager.connectionState,
        _localState,
    ) { messages, connection, local ->
        local.copy(
            messages = messages,
            connectionState = connection,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _localState.value,
    )

    // ============================== 连接 ==============================

    fun connect() {
        if (myUserId <= 0L) return
        launch { webSocketManager.connect(myUserId, Constants.WS_URL) }
    }

    // ============================== 发送 ==============================

    fun sendTextMessage(content: String) {
        if (content.isBlank()) return
        if (!ensureValid()) return
        launch {
            val ok = sendMessageUseCase.sendText(myUserId, peerInfo(), content)
            if (!ok) _effect.tryEmit(WxChatEffect.SendFailed("未连接服务器"))
        }
    }

    fun sendImage(uri: Uri) {
        if (!ensureValid()) return
        runAsync { sendMessageUseCase.sendImage(myUserId, peerInfo(), uri) }
    }

    fun sendVideo(uri: Uri) {
        if (!ensureValid()) return
        runAsync { sendMessageUseCase.sendVideo(myUserId, peerInfo(), uri) }
    }

    fun sendVoice(file: File, durationMs: Long) {
        if (!ensureValid()) return
        runAsync { sendMessageUseCase.sendVoice(myUserId, peerInfo(), file, durationMs) }
    }

    fun sendFile(uri: Uri) {
        if (!ensureValid()) return
        runAsync { sendMessageUseCase.sendFile(myUserId, peerInfo(), uri) }
    }

    fun deleteConversation() {
        if (targetUserId <= 0L) return
        launch {
            chatRepository.deleteConversation(targetUserId)
            _effect.tryEmit(WxChatEffect.FinishPage)
        }
    }

    // ============================== 通话提示 ==============================

    private suspend fun onCallEnded() {
        val duration = callManager.lastCallDuration
        val callType = callManager.lastCallType
        val isVideo = callType == CallType.VIDEO
        val typeLabel = if (isVideo) "视频通话" else "语音通话"
        val iconRes = if (isVideo) R.drawable.ic_video_call else R.drawable.ic_call

        val content = if (duration > 0) {
            val minutes = duration / 60
            val seconds = duration % 60
            if (minutes > 0) "$typeLabel ${minutes}分${seconds}秒" else "$typeLabel ${seconds}秒"
        } else if (callManager.lastIsOutgoing) {
            "$typeLabel 已取消"
        } else {
            "未接听"
        }

        chatRepository.saveCallHintMessage(
            myUserId = myUserId,
            peerId = targetUserId,
            content = content,
            iconRes = iconRes,
        )
    }

    // ============================== 内部 ==============================

    private fun ensureValid(): Boolean {
        if (myUserId <= 0L || targetUserId <= 0L) {
            _effect.tryEmit(WxChatEffect.ShowToast("用户信息不完整"))
            return false
        }
        return true
    }

    private fun peerInfo() = ChatPeer(
        id = targetUserId,
        username = targetUsername,
        avatar = resolvedAvatar,
    )
}
