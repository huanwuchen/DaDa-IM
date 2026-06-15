package com.dada.app.network.websocket

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.dada.core.common.utils.LogUtil
import androidx.core.app.NotificationCompat
import com.dada.app.R
import com.dada.domain.chat.repository.ImChatRepository
import com.dada.domain.contact.repository.ImContactRepository
import com.dada.app.notification.GlobalMessageNotifier
import com.dada.app.ui.welcome.WelcomeActivity
import com.dada.core.database.UserPreferences
import com.dada.core.common.utils.AppForegroundTracker
import com.dada.core.network.websocket.WebSocketManager
import com.dada.core.network.websocket.WebSocketListener
import com.dada.core.network.websocket.MessageModel
import com.dada.core.network.websocket.MessageManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * WebSocket 后台服务
 * 即使 App 退到后台或聊天页面关闭，也能保持 WebSocket 连接接收消息
 */
@AndroidEntryPoint
class WebSocketService : Service() {

    @Inject lateinit var webSocketManager: WebSocketManager

    /** 可靠消息管理器。负责 ACK、重发、心跳。 */
    @Inject lateinit var messageManager: MessageManager

    @Inject lateinit var callManager: com.dada.app.network.call.CallManager

    /** 由 Hilt 注入的聊天 Repository（用于全局消息入库） */
    @Inject lateinit var chatRepository: ImChatRepository

    /** 由 Hilt 注入的通讯录 Repository（用于补全昵称/头像） */
    @Inject lateinit var contactRepository: ImContactRepository

    /** 由 Hilt 注入的全局消息通知器（用于震动、声音、悬浮窗） */
    @Inject lateinit var globalMessageNotifier: GlobalMessageNotifier

    /** 由 Hilt 注入的用户偏好（用于获取当前用户 ID） */
    @Inject lateinit var userPreferences: UserPreferences

    /** 全局消息持久化器，把所有收到的消息写入 Room */
    private val globalPersister by lazy {
        GlobalMessagePersister(chatRepository, contactRepository, userPreferences)
    }

    /** Service 协程作用域 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 全局消息监听器（用于通知）
    private val globalListener = object : WebSocketListener {
        override fun onConnected() {
            LogUtil.d(TAG, "Service: WebSocket 连接成功")
            updateNotification("已连接")
        }

        override fun onDisconnected() {
            LogUtil.d(TAG, "Service: WebSocket 断开")
            updateNotification("已断开")
        }

        override fun onMessageReceived(message: MessageModel) {
            LogUtil.d(TAG, "Service: 收到消息 type=${message.type} from=${message.fromId}")

            // 过滤系统消息和心跳
            if (message.type == "system" || message.type == "heartbeat") return

            val myUserId = userPreferences.getUserId()
            val from = message.fromId ?: return

            LogUtil.d(TAG, "消息校验: from=$from (${from.javaClass.simpleName}), myUserId=$myUserId (${myUserId.javaClass.simpleName}), type=${message.type}")

            // 不是发给自己的消息忽略（服务器可能回显自己发的消息）
//            if (from == myUserId) {
//                LogUtil.d(TAG, "过滤自己发的消息: from=$from")
//                return
//            }

            // 通话信令：call-invite 时弹出来电通知
            if (message.type == "call-invite") {
                LogUtil.d(TAG, "收到来电通知: from=$from")
                sendCallNotification(from)
                return
            }

            // 其他通话信令由 CallManager 处理
            if (message.type in CALL_SIGNAL_TYPES) return

            // 如果用户正在和该联系人聊天，不弹通知（避免打扰）
            if (AppForegroundTracker.isForeground &&
                AppForegroundTracker.getCurrentChatUserId() == from) {
                LogUtil.d(TAG, "用户正在该聊天页，不发送通知")
                return
            }

            // 弹出新消息通知
            sendMessageNotification(from, message.content)
        }

        override fun onConnectFailed(error: String) {
            LogUtil.e(TAG, "Service: 连接失败 $error")
            updateNotification("连接失败")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LogUtil.d(TAG, "WebSocketService onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("正在启动..."))

        // 启动可靠消息管理器：接管心跳、ack、重发
        // 内部会把 WebSocketManager.legacyHeartbeatEnabled 设为 false
        messageManager.start()

        // 注册全局监听器
        webSocketManager.addListener(globalListener)
        // 注册全局持久化器（保证收到的消息总是入库）
        webSocketManager.addListener(globalPersister)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.d(TAG, "WebSocketService onStartCommand")

        // 启动 WebSocket 连接
        val userId = userPreferences.getUserId()
        if (userId > 0) {
            webSocketManager.connect(userId, BASE_URL)
        } else {
            LogUtil.w(TAG, "userId 无效，停止服务")
            stopSelf()
        }

        // START_STICKY: 被系统杀掉后会自动重启
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.d(TAG, "WebSocketService onDestroy")
        webSocketManager.removeListener(globalListener)
        webSocketManager.removeListener(globalPersister)
        globalPersister.release()
        messageManager.stop()
        webSocketManager.disconnect()
        serviceScope.cancel()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // 前台服务通知渠道（低优先级，不打扰）
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "IM 后台连接",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持消息接收"
                setShowBadge(false)
            }
            nm.createNotificationChannel(serviceChannel)

            // 新消息通知渠道（高优先级）
            val messageChannel = NotificationChannel(
                CHANNEL_ID_MESSAGE,
                "新消息",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "收到新的聊天消息"
            }
            nm.createNotificationChannel(messageChannel)
        }
    }

    /**
     * 构建前台服务通知
     */
    private fun buildNotification(content: String): Notification {
        val intent = Intent(this, WelcomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("IM 在线")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * 更新前台服务通知
     */
    private fun updateNotification(content: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(content))
    }

    /**
     * 发送新消息通知
     * 使用 GlobalMessageNotifier 统一处理震动、声音、悬浮窗
     */
    private fun sendMessageNotification(fromId: Long, content: String) {
        // 从数据库获取联系人信息
        serviceScope.launch {
            val contact = withContext(Dispatchers.IO) {
                contactRepository.getContact(fromId)
            }
            val username = contact?.username ?: "用户$fromId"
            val avatar = contact?.avatar

            // 在主线程调用通知器
            globalMessageNotifier.onNewMessage(
                fromUserId = fromId,
                fromUsername = username,
                message = content,
                avatar = avatar
            )
        }
    }

    /**
     * 发送来电通知
     * 使用 GlobalMessageNotifier 在 App 顶部显示来电弹窗
     */
    private fun sendCallNotification(fromId: Long) {
        // 通过 Hilt 注入的 callManager
        val isVideo = callManager.callInfo.value.callType == com.dada.app.network.call.CallType.VIDEO

        // 从数据库获取联系人信息
        serviceScope.launch {
            val contact = withContext(Dispatchers.IO) {
                contactRepository.getContact(fromId)
            }
            val username = contact?.username ?: "用户$fromId"
            val avatar = contact?.avatar

            // 显示来电弹窗
            globalMessageNotifier.onCallInvite(
                fromUserId = fromId,
                fromUsername = username,
                avatar = avatar,
                isVideo = isVideo,
                onAccept = {
                    // 接听：跳转到 CallActivity
                    val intent = Intent(this@WebSocketService, com.dada.app.ui.call.CallActivity::class.java).apply {
                        putExtra("extra_target_user_id", fromId)
                        putExtra("extra_target_username", username)
                        putExtra("extra_is_incoming", true)
                        putExtra("extra_is_video", isVideo)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                },
                onReject = {
                    // 拒绝：发送拒绝信令
                    val rejectMsg = MessageModel(
                        fromId = userPreferences.getUserId(),
                        toId = fromId,
                        content = "",
                        type = "call-reject"
                    )
                    webSocketManager.sendMessage(rejectMsg)
                }
            )
        }
    }

    // 通话信令类型
    private val CALL_SIGNAL_TYPES = setOf(
        "call-invite", "call-accept", "call-reject", "call-hangup"
    )

    companion object {
        private const val TAG = "WebSocketService"
        private const val CHANNEL_ID_SERVICE = "im_websocket_service"
        private const val CHANNEL_ID_MESSAGE = "im_message"
        private const val NOTIFICATION_ID = 1001
        private const val MESSAGE_NOTIFICATION_ID_BASE = 2000
        private const val CALL_NOTIFICATION_ID_BASE = 3000

        private val BASE_URL: String
            get() = com.dada.core.network.BuildConfig.SERVER_WS_URL

        /**
         * 启动服务
         */
        fun start(context: Context) {
            val intent = Intent(context, WebSocketService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止服务
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, WebSocketService::class.java))
        }
    }
}
