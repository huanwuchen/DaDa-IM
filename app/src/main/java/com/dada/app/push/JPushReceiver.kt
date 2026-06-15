package com.dada.app.push

import android.content.Context
import android.content.Intent
import com.dada.core.common.utils.LogUtil
import cn.jpush.android.api.CustomMessage
import cn.jpush.android.api.JPushMessage
import cn.jpush.android.api.NotificationMessage
import cn.jpush.android.service.JPushMessageReceiver
import com.dada.core.database.UserPreferences
import com.dada.app.ui.main.ImMainActivity
import com.dada.app.ui.welcome.WelcomeActivity
import com.dada.app.ui.chat.WxChatActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.json.JSONObject

/**
 * 极光推送接收器
 *
 * 处理三类事件：
 *  1. [onRegister] —— 拿到 RegistrationID 时上报后端
 *  2. [onMessage] —— 收到自定义消息（透传），不会自动展示通知
 *  3. [onNotifyMessageOpened] —— 用户点击通知，跳转到具体聊天页
 *
 * 服务端推送时建议在 extras 中带上：
 *   {"fromUserId": 123, "fromUsername": "张三"}
 * 这样点击通知就能直接打开 [WxChatActivity]。
 */
class JPushReceiver : JPushMessageReceiver() {

    /** 从 SingletonComponent 取 PushManager（Receiver 不能直接 @Inject） */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PushReceiverEntryPoint {
        fun pushManager(): PushManager
        fun userPreferences(): UserPreferences
    }

    private fun getPushManager(context: Context): PushManager {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PushReceiverEntryPoint::class.java
        )
        return entryPoint.pushManager()
    }

    private fun getUserPreferences(context: Context): UserPreferences {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PushReceiverEntryPoint::class.java
        )
        return entryPoint.userPreferences()
    }

    // ============================== Token / 别名 ==============================

    /**
     * 拿到（或刷新）RegistrationID
     */
    override fun onRegister(context: Context, registrationId: String) {
        LogUtil.d(TAG, "onRegister: $registrationId")
        getPushManager(context).onRegistrationIdReceived(registrationId)
    }

    /**
     * 别名设置/删除回调（仅打 log，便于排查）
     */
    override fun onAliasOperatorResult(context: Context, msg: JPushMessage) {
        LogUtil.d(TAG, "onAliasOperatorResult: alias=${msg.alias}, code=${msg.errorCode}")
    }

    // ============================== 消息接收 ==============================

    /**
     * 透传消息（不会自动展示通知）
     *
     * 当 App 在前台时通常用透传消息：我们这里不主动弹通知，
     * 因为 IM 的实时消息已经走 WebSocket，避免重复打扰用户。
     */
    override fun onMessage(context: Context, msg: CustomMessage) {
        LogUtil.d(TAG, "onMessage: ${msg.message}, extra=${msg.extra}")
    }

    /**
     * 通知到达系统通知栏（用户尚未点击）
     */
    override fun onNotifyMessageArrived(context: Context, msg: NotificationMessage) {
        LogUtil.d(TAG, "onNotifyMessageArrived: ${msg.notificationContent}")
    }

    /**
     * 用户点击通知 -> 跳转到对应聊天页
     */
    override fun onNotifyMessageOpened(context: Context, msg: NotificationMessage) {
        LogUtil.d(TAG, "onNotifyMessageOpened: ${msg.notificationContent}, extras=${msg.notificationExtras}")
        val intent = buildOpenIntent(context, msg.notificationExtras)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        context.startActivity(intent)
    }

    /**
     * 根据通知 extras 决定打开哪个页面
     *
     *  - 未登录 -> 走 WelcomeActivity（处理后再跳）
     *  - extras 含 userId -> 直接进入 WxChatActivity
     *  - 否则 -> 进 ImMainActivity 的消息列表
     */
    private fun buildOpenIntent(context: Context, extras: String?): Intent {
        if (!getUserPreferences(context).isLoggedIn()) {
            return Intent(context, WelcomeActivity::class.java)
        }

        val payload = parseExtras(extras)
        val fromUserId = payload?.optLong(KEY_FROMUSER_ID, 0L) ?: 0L
        if (fromUserId > 0L) {
            return Intent(context, WxChatActivity::class.java).apply {
                putExtra(WxChatActivity.EXTRA_TARGET_USER_ID, fromUserId)
                putExtra(
                    WxChatActivity.EXTRA_TARGET_USERNAME,
                    "用户$fromUserId"  // 服务端未传 username，用 ID 兜底
                )
            }
        }
        return Intent(context, ImMainActivity::class.java)
    }

    /**
     * 把 extras（JSON 字符串）安全地解析成 JSONObject
     */
    private fun parseExtras(extras: String?): JSONObject? {
        if (extras.isNullOrBlank()) return null
        return runCatching { JSONObject(extras) }.getOrNull()
    }

    companion object {
        private const val TAG = "JPushReceiver"

        /** 服务端推送时约定的 extras 字段名（对应发送方的 userId） */
        const val KEY_USER_ID = "userId"
        const val KEY_FROMUSER_ID = "fromUserId"
        const val KEY_TYPE = "type"
    }
}
