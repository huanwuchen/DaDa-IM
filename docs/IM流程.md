# DaDa IM 核心能力分析

## 一、登录流程

### 1.1 注册/登录一体化

```
WelcomeActivity
    │
    ▼
用户输入用户名 → 点击"进入"
    │
    ▼
WelcomeViewModel.register(username)
    │
    ▼
ImApiService.register(RegisterRequest)
    POST /api/user/register
    Body: { username, deviceId }
    │
    ▼
Server 返回: ImUser { id, username, avatar, token, deviceId }
    │
    ▼
UserPreferences.saveUser(userId, deviceId, username, avatar)
  ├── KvUtil.putLong("user_id", userId)
  ├── KvUtil.putString("access_token", token)
  ├── KvUtil.putString("user_name", username)
  └── KvUtil.putString("user_avatar", avatar)
    │
    ▼
Room INSERT: ImUserProfileEntity
    │
    ▼
PushManager.onUserLogin(userId)
  ├── JPushInterface.setAlias(context, 0, userId.toString())
  └── ImApiService.reportPushToken(PushTokenRequest)
    │
    ▼
WebSocketService.start(context)
  └── WebSocketManager.connect(userId, WS_URL)
        └── ws://192.168.124.17:8080/ws/{userId}
    │
    ▼
跳转 → ImMainActivity
```

### 1.2 Token 管理

- **存储**: MMKV (`access_token` key)
- **注入**: OkHttp Interceptor 自动添加 `Authorization: Bearer {token}` 头
- **刷新**: 当前实现无自动刷新机制，依赖 WebSocket 长连接保持会话
- **校验**: 服务端 API 通过 Bearer Token 校验身份

来源于:
- 文件: `core/network/src/main/java/com/dada/core/network/di/NetworkModule.kt`
- 类: `NetworkModule`
- 方法: `provideAuthInterceptor()`

### 1.3 登录状态判断

```kotlin
// UserPreferencesImpl.kt
override fun isLoggedIn(): Boolean = getUserId() > 0
```

- 文件: `core/database/src/main/java/com/dada/core/database/UserPreferencesImpl.kt`
- 方法: `isLoggedIn()`

---

## 二、单聊流程

### 2.1 消息发送流程

```
WxChatActivity (用户输入文字，点击发送)
    │
    ▼
WxChatViewModel.sendTextMessage(content)
    │
    ▼
SendMessageUseCase.sendText(myUserId, peer, content)
    │
    ▼
ImChatRepositoryImpl.sendTextMessage(myUserId, peer, content)
    │
    ├── Step 1: 创建消息实体
    │   val entity = ImMessageEntity(
    │       id = UUID,
    │       conversationId = peer.id,
    │       fromId = myUserId,
    │       toId = peer.id,
    │       content = content,
    │       type = "text",
    │       timestamp = System.currentTimeMillis(),
    │       isMine = true,
    │       avatar = myAvatar
    │   )
    │
    ├── Step 2: 本地持久化
    │   messageDao.insert(entity)  // Room INSERT (IGNORE 冲突策略)
    │
    ├── Step 3: 更新/创建会话
    │   conversationDao.upsert(ImConversationEntity(
    │       peerId = peer.id,
    │       peerUsername = peer.username,
    │       peerAvatar = peer.avatar,
    │       lastMessage = content,
    │       lastMessageTime = timestamp,
    │       lastMessageType = "text",
    │       unreadCount = 0  // 自己发的不计未读
    │   ))
    │
    └── Step 4: WebSocket 发送
        webSocketManager.sendMessage(MessageModel(
            id = entity.id,
            fromId = myUserId,
            toId = peer.id,
            content = content,
            type = "text",
            timestamp = timestamp
        ))
```

来源于:
- 文件: `app/src/main/java/com/dada/app/data/repository/ImChatRepositoryImpl.kt`
- 方法: `sendTextMessage()`

### 2.2 消息接收流程

```
OkHttp WebSocket 收到文本帧
    │
    ▼
WebSocketListenerImpl.onMessage(text)
    │
    ├── onRawText(text) ──→ MessageManager.handleInbound(text)
    │                           │
    │                           ▼
    │                       解析 Frame 信封
    │                           │
    │                           ├── isAck → ackManager.complete(id)
    │                           ├── isHeartbeat → 回 ACK
    │                           └── 业务消息 → handleBusinessMessage(msg)
    │                                               │
    │                                               ├── LruDedup 去重
    │                                               ├── store.insertIncoming()
    │                                               ├── 回 ACK
    │                                               └── _inbound.tryEmit(msg)
    │
    └── onMessage(message) ──→ 旧监听器分发
                                │
                                ▼
                           GlobalMessagePersister.onMessageReceived()
                                │
                                ├── 过滤: system/heartbeat/call-* 类型跳过
                                ├── 验证: to == myUserId (只处理发给我的)
                                ├── 判断: incUnread = !(正在该聊天页)
                                │
                                └── chatRepository.saveIncomingMessage()
                                        │
                                        ├── Room INSERT: ImMessageEntity
                                        └── Room UPSERT: ImConversationEntity
                                              (lastMessage, lastMessageTime, unreadCount++)
```

来源于:
- 文件: `app/src/main/java/com/dada/app/network/websocket/GlobalMessagePersister.kt`
- 方法: `onMessageReceived()`

### 2.3 ACK 机制

**协议层 ACK (MessageManager)**:

```json
// 发送方发出消息后等待 ACK
{ "type": "ack", "ackId": "消息UUID" }

// 接收方收到消息后回 ACK
{ "type": "ack", "ackId": "消息UUID" }
```

**实现**:
- 发送方: `MessageManager.send()` → `attemptSendLoop()` 等待 ACK，超时重发
- 接收方: `MessageManager.handleBusinessMessage()` → 回 ACK
- 超时: 8 秒 (`ackTimeoutMs`)
- 最大重试: 5 次 (`retryPolicy.maxAttempts`)

来源于:
- 文件: `core/network/src/main/java/com/dada/core/network/websocket/MessageManager.kt`
- 方法: `send()`, `attemptSendLoop()`, `handleBusinessMessage()`

### 2.4 重发机制

```kotlin
// RetryPolicy.kt
class RetryPolicy(
    val maxAttempts: Int = 5,      // 最多重试 5 次
    private val baseMs: Long = 1_000,  // 基础延迟 1 秒
    private val maxMs: Long = 30_000,  // 最大延迟 30 秒
    private val jitterRatio: Double = 0.2,  // 抖动比例 20%
)
```

**退避策略**: 指数退避 + 随机抖动
- 第 1 次: ~1s
- 第 2 次: ~2s
- 第 3 次: ~4s
- 第 4 次: ~8s
- 第 5 次: ~16s

来源于:
- 文件: `core/network/src/main/java/com/dada/core/network/websocket/RetryPolicy.kt`

### 2.5 离线消息

**当前实现**: 服务端负责离线消息存储和下发。客户端连接后，服务端推送离线期间的消息。

**本地持久化**: 所有收到的消息都通过 `GlobalMessagePersister` 写入 Room，保证：
- 重新进入会话时能从本地直接看到历史
- 首页消息列表即时更新

**崩溃恢复**: `MessageManager.recoverUnackedFromStore()` 在重连后加载未确认消息重新发送。

---

## 三、消息类型支持

| 类型 | 发送方式 | 存储 | 展示 |
|------|---------|------|------|
| text | 直接 WebSocket 发送 | content = 文字 | TextView |
| image | 先上传 → WebSocket 发送 URL | content = URL | ImageView (Glide) |
| video | 先上传 → WebSocket 发送 URL | content = URL | ExoPlayer |
| audio | 先上传 → WebSocket 发送 URL | content = URL | VoicePlayer |
| file | 先上传 → WebSocket 发送 URL\|size\|name | content = 复合格式 | 文件卡片 |
| call_hint | 本地生成 | content = 提示文字 | 特殊样式 |

来源于:
- 文件: `core/common/src/main/java/com/dada/core/common/domain/model/Message.kt`
- 类: `Message.Companion`

---

## 四、群聊

**当前状态**: 项目代码中未发现群聊功能。所有聊天均为单聊 (peer-to-peer)。

---

## 五、在线状态

### 5.1 在线状态获取

```kotlin
// ImApiService.kt
@GET("/api/user/online")
suspend fun getOnlineUsers(): ApiResponse<OnlineUsersResponse>
```

- 通过 HTTP API 主动拉取在线用户列表
- 无实时在线状态推送机制（无 Presence 协议）

来源于:
- 文件: `core/network/src/main/java/com/dada/core/network/api/ImApiService.kt`
- 方法: `getOnlineUsers()`

### 5.2 心跳机制

**旧方案 (HeartBeatManager)**:
- 每 15 秒发送 `{ type: "heartbeat", content: "ping" }`
- 无 ACK 确认

**新方案 (MessageManager)**:
- 每 25 秒发送心跳 Frame
- 等待 8 秒 ACK 超时
- 超时触发 `forceReconnect()`
- 状态从 Connected → HalfOpen → Connected/Reconnecting

来源于:
- 文件: `core/network/src/main/java/com/dada/core/network/websocket/MessageManager.kt`
- 方法: `startHeartbeat()`

### 5.3 断线重连机制

```
WebSocket 断开
    │
    ▼
WebSocketManager.scheduleReconnect()
    ├── reconnectCount++
    ├── 状态: RECONNECTING
    ├── 延迟: 3 秒 (RECONNECT_DELAY)
    └── doConnect() 重新连接
        │
        ├── 成功 → reconnectCount = 0, 状态: CONNECTED
        └── 失败 → 继续重连，最多 5 次 (MAX_RECONNECT_COUNT)
            │
            └── 超过上限 → 状态: Dead
```

**前台服务保活**:
- `WebSocketService` 以前台服务运行
- `START_STICKY` 被系统杀掉后自动重启
- 两个通知渠道: 服务通知 (低优先级) + 消息通知 (高优先级)

来源于:
- 文件: `app/src/main/java/com/dada/app/network/websocket/WebSocketService.kt`
- 方法: `onStartCommand()`, `createNotificationChannel()`

---

## 六、消息存储

### 6.1 消息表 (im_messages)

| 字段 | 类型 | 说明 |
|------|------|------|
| seq | Long (PK, 自增) | 本地排序序号 |
| id | String (UNIQUE) | 业务消息 ID (UUID) |
| conversationId | Long | 会话 ID = 对方 userId |
| fromId | Long | 发送者 ID |
| toId | Long | 接收者 ID |
| content | String | 消息内容/媒体 URL |
| type | String | 消息类型 (text/image/video/audio/file/call_hint) |
| timestamp | Long | 消息时间戳 |
| isMine | Boolean | 是否自己发的 |
| thumbUrl | String? | 缩略图 URL |
| duration | Long | 音视频时长 (ms) |
| size | Long | 文件大小 (bytes) |
| width/height | Int | 图片/视频尺寸 |
| fileName | String? | 文件名 |
| avatar | String? | 发送者头像 |

**索引**:
- `UNIQUE(id)` — 消息去重
- `(conversationId, timestamp, seq)` — 会话消息排序查询

来源于:
- 文件: `core/database/src/main/java/com/dada/core/database/entity/ImMessageEntity.kt`

### 6.2 会话表 (im_conversations)

| 字段 | 类型 | 说明 |
|------|------|------|
| peerId | Long (PK) | 对方用户 ID |
| peerUsername | String | 对方昵称 (冗余) |
| peerAvatar | String? | 对方头像 (冗余) |
| lastMessage | String | 最后一条消息内容 |
| lastMessageTime | Long | 最后消息时间 (排序用) |
| lastMessageType | String | 最后消息类型 |
| unreadCount | Int | 未读消息数 |

来源于:
- 文件: `core/database/src/main/java/com/dada/core/database/entity/ImConversationEntity.kt`

### 6.3 未读数实现

**累加**: 收到新消息时，如果不在该聊天页 → `unreadCount++`
```kotlin
// GlobalMessagePersister.kt
val incUnread = !(AppForegroundTracker.isForeground &&
    AppForegroundTracker.getCurrentChatUserId() == peerId)
chatRepository.saveIncomingMessage(myUserId, peer, message, incUnread)
```

**清零**: 进入聊天页时 → `conversationDao.clearUnread(peerId)`
```kotlin
// ImChatRepositoryImpl.kt
override suspend fun markAsRead(peerId: Long) {
    conversationDao.clearUnread(peerId)
}
```

来源于:
- 文件: `app/src/main/java/com/dada/app/network/websocket/GlobalMessagePersister.kt`
- 文件: `app/src/main/java/com/dada/app/data/repository/ImChatRepositoryImpl.kt`

### 6.4 最近消息实现

- 每次发/收消息时更新 `im_conversations` 表的 `lastMessage` 和 `lastMessageTime`
- 会话列表按 `lastMessageTime DESC` 排序
- 通过 Room Flow 自动通知 UI 更新

```kotlin
// ImConversationDao.kt
@Query("SELECT * FROM im_conversations ORDER BY lastMessageTime DESC")
fun observeConversations(): Flow<List<ImConversationEntity>>
```

---

## 七、推送

### 7.1 推送架构

```
App 退后台
    │
    ├── WebSocketService (前台服务) 保持连接
    │     └── 即使 App 在后台也能收消息
    │
    └── 如果进程被杀
          │
          ▼
       JPush 离线推送
          │
          ├── 服务端 → JPush Server → 设备
          ├── JPushReceiver.onNotifyMessageOpened()
          └── 跳转到聊天页
```

### 7.2 极光推送集成

**初始化**:
```kotlin
// PushManager.kt
fun init() {
    JPushInterface.init(context)
}
```

**别名绑定** (登录时):
```kotlin
fun onUserLogin(userId: Long) {
    JPushInterface.setAlias(context, 0, userId.toString())
    uploadRegistrationId(registrationId)
}
```

**RegistrationID 上报**:
```kotlin
fun onRegistrationIdReceived(registrationId: String) {
    KvUtil.putString("jpush_registration_id", registrationId)
    scope.launch {
        imApiService.reportPushToken(PushTokenRequest(
            userId = userPreferences.getUserId(),
            registrationId = registrationId,
            platform = "android"
        ))
    }
}
```

来源于:
- 文件: `app/src/main/java/com/dada/app/push/PushManager.kt`
- 文件: `app/src/main/java/com/dada/app/push/JPushReceiver.kt`

### 7.3 推送跳转逻辑

```kotlin
// JPushReceiver.kt → buildOpenIntent()
when {
    !userPreferences.isLoggedIn() → WelcomeActivity
    extras.containsKey(KEY_FROMUSER_ID) → WxChatActivity(targetUserId)
    else → ImMainActivity
}
```

### 7.4 应用内通知

**场景**: 用户在 App 内但不在聊天页时收到消息

**方案**: `InAppFloatingWindowManager` — 在当前 Activity 的根 FrameLayout 上叠加悬浮窗

- 不需要 `SYSTEM_ALERT_WINDOW` 权限
- 消息通知: 3 秒自动消失
- 来电通知: 30 秒超时
- 动画: 顶部滑入/滑出

来源于:
- 文件: `app/src/main/java/com/dada/app/notification/InAppFloatingWindowManager.kt`

---

## 八、音视频通话

### 8.1 双方案架构

```
音视频通话
    │
    ├── 方案 A: 自研 UDP 引擎 (默认)
    │     ├── CallManager (信令)
    │     ├── VoiceEngine (语音: 16kHz PCM over UDP)
    │     └── VideoEngine (视频: H264/JPEG over UDP)
    │
    └── 方案 B: 腾讯 TUICallKit (备选)
          ├── TUICallEngine
          └── LiteAVSDK_TRTC
```

### 8.2 信令流程 (自研方案)

```
A (发起方)                    Server                    B (接收方)
    │                           │                           │
    │── call-invite ───────────→│── call-invite ───────────→│
    │   {callId, type,          │                           │
    │    transport:"udp",       │                           │
    │    data:{audioIp,Port}}   │                           │
    │                           │                           │
    │                           │←── call-accept ───────────│
    │←── call-accept ───────────│   {audioIp, audioPort}    │
    │   {audioIp, audioPort}    │                           │
    │                           │                           │
    │═══ UDP 直连 (PCM 音频) ═══════════════════════════════│
    │                           │                           │
    │── call-hangup ───────────→│── call-hangup ───────────→│
```

来源于:
- 文件: `app/src/main/java/com/dada/app/network/call/CallManager.kt`
- 方法: `invite()`, `accept()`, `reject()`, `hangup()`

### 8.3 VoiceEngine 技术参数

| 参数 | 值 |
|------|-----|
| 采样率 | 16kHz |
| 声道 | 单声道 |
| 位深 | 16-bit PCM |
| 帧大小 | 640 bytes (20ms 音频) |
| UDP 包格式 | 4B seq + 4B timestamp + 640B PCM = 648 bytes |
| WebSocket 帧 | 0x02 prefix + seq + timestamp + pcmData |
| 音频模式 | MODE_IN_COMMUNICATION + VOICE_COMMUNICATION |

来源于:
- 文件: `app/src/main/java/com/dada/app/network/call/voice/VoiceEngine.kt`

### 8.4 VideoEngine 技术架构

```
Camera (CameraView)
    │
    ▼
YUV Frame Queue
    │
    ├── encodeThread: YUV → H264/JPEG 编码
    │       │
    │       ▼
    │   Encoded Queue
    │       │
    │       ├── sendThread: → UDP/WebSocket 发送
    │       │
    │       └── recvThread: ← UDP/WebSocket 接收
    │               │
    │               ▼
    │           H264/JPEG 解码 → SurfaceView
```

来源于:
- 文件: `app/src/main/java/com/dada/app/network/call/video/VideoEngine.kt`
- 方法: `start()`, `startWebSocket()`

### 8.5 传输模式

| 模式 | 场景 | 实现 |
|------|------|------|
| UDP 直连 | 局域网/同网段 | VoiceEngine/VideoEngine 直接 UDP |
| WebSocket | 跨网段/无法 UDP | 数据通过 WebSocket 二进制帧中转 |

`NetworkCallRouter` 负责自动检测是否同网段，选择最优传输路径。

来源于:
- 文件: `app/src/main/java/com/dada/app/network/call/NetworkCallRouter.kt`

---

