package com.dada.app.notification

import android.content.Context
import android.content.Intent
import com.dada.app.ui.chat.WxChatActivity
import com.dada.core.common.utils.AppForegroundTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局消息通知协调器
 *
 * 功能：
 * 1. 接收新消息
 * 2. 判断是否需要显示通知
 * 3. 协调震动、声音、App内弹窗
 */
@Singleton
class GlobalMessageNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: MessageNotificationManager,
    private val floatingWindowManager: InAppFloatingWindowManager
) {

    /**
     * 处理新消息
     *
     * @param fromUserId 发送者ID
     * @param fromUsername 发送者昵称
     * @param message 消息内容
     * @param avatar 发送者头像
     */
    fun onNewMessage(
        fromUserId: Long,
        fromUsername: String,
        message: String,
        avatar: String?
    ) {
        // 1. 检查是否在当前聊天页面
        val isInCurrentChat = AppForegroundTracker.getCurrentChatUserId() == fromUserId

        // 如果在当前聊天页面，不显示任何通知
        if (isInCurrentChat) {
            return
        }

        // 2. 处理震动和声音（根据手机状态）
        notificationManager.handleNewMessage(fromUserId, fromUsername, message, avatar)

        // 3. 检查是否在聊天列表页面
        val isInChatList = AppForegroundTracker.isInChatListPage()

        // 如果不在聊天列表页面，显示App内弹窗
        if (!isInChatList && notificationManager.shouldShowNotification()) {
            showFloatingWindow(fromUserId, fromUsername, message, avatar)
        }
    }

    /**
     * 显示App内弹窗
     */
    private fun showFloatingWindow(
        fromUserId: Long,
        fromUsername: String,
        message: String,
        avatar: String?
    ) {
        floatingWindowManager.showMessageNotification(
            fromUserId = fromUserId,
            fromUsername = fromUsername,
            message = formatMessage(message),
            avatar = avatar,
            onClick = {
                // 点击弹窗，打开聊天页面
                openChatPage(fromUserId, fromUsername, avatar)
            }
        )
    }

    /**
     * 格式化消息内容
     */
    private fun formatMessage(message: String): String {
        return when {
            message.length > 50 -> message.substring(0, 50) + "..."
            else -> message
        }
    }

    /**
     * 打开聊天页面
     */
    private fun openChatPage(userId: Long, username: String, avatar: String?) {
        val intent = Intent(context, WxChatActivity::class.java).apply {
            putExtra(WxChatActivity.EXTRA_TARGET_USER_ID, userId)
            putExtra(WxChatActivity.EXTRA_TARGET_USERNAME, username)
            putExtra(WxChatActivity.EXTRA_TARGET_AVATAR, avatar)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    /**
     * 处理来电通知（语音/视频）
     *
     * @param fromUserId 来电者ID
     * @param fromUsername 来电者昵称
     * @param avatar 来电者头像
     * @param isVideo 是否为视频通话
     * @param onAccept 接听回调
     * @param onReject 拒绝回调
     */
    fun onCallInvite(
        fromUserId: Long,
        fromUsername: String,
        avatar: String?,
        isVideo: Boolean,
        onAccept: () -> Unit,
        onReject: () -> Unit
    ) {
        // 处理震动和声音（来电使用更明显的提醒）
        notificationManager.handleNewMessage(fromUserId, fromUsername, "来电", avatar)

        // 显示来电弹窗
        floatingWindowManager.showCallNotification(
            fromUserId = fromUserId,
            fromUsername = fromUsername,
            avatar = avatar,
            isVideo = isVideo,
            onAccept = onAccept,
            onReject = onReject
        )
    }

    /**
     * 清除所有通知
     */
    fun clearAll() {
        floatingWindowManager.dismiss()
    }
}
