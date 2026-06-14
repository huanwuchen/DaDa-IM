# Project Evolution

```
v1  ─ 单模块，基础聊天
 │    Activity 直接调 API，消息只在内存
 │
v2  ─ 引入 MVVM + Repository
 │    ViewModel 隔离 UI 和数据层
 │    Repository 统一数据来源
 │
v3  ─ Room 持久化
 │    消息写入数据库，App 重启不丢
 │    会话列表、未读计数
 │
v4  ─ WebSocket 长连接
 │    替代轮询，实时消息推送
 │    心跳检测、断线重连
 │
v5  ─ 消息可靠性
 │    ACK 确认机制
 │    指数退避重试
 │    LRU 去重 + UNIQUE 约束
 │
v6  ─ 多模块 Clean Architecture
 │    app / domain / core:network / core:database / core:common
 │    Hilt 依赖注入，面向接口
 │
v7  ─ 推送集成
 │    极光 JPush 离线推送
 │    前台 Service 保活
 │
v8  ─ 音视频
 │    自研 UDP 引擎（局域网）
 │    TUICallKit（云端）
 │    自动局域网探测
 │
v9  ─ AI 助手
 │    MiMo API SSE 流式对话
 │    推理过程展示
 │
v10 ─ 朋友圈
      图文动态、点赞、评论
```

## Architecture Evolution

```
Current                              Planned
┌──────────────┐                    ┌──────────────────────────────┐
│  WebSocket   │  ────────────────▶ │  WebSocket (IM channel)      │
│  IM Channel  │                    └──────────────────────────────┘
└──────────────┘
┌──────────────┐                    ┌──────────────────────────────┐
│  UDP (LAN)   │  ────────────────▶ │  WebRTC                      │
│  Voice/Video │                    │  ├─ STUN / TURN              │
└──────────────┘                    │  ├─ Adaptive Bitrate         │
┌──────────────┐                    │  ├─ Jitter Buffer            │
│  TUICallKit  │  ────────────────▶ │  └─ SFU (selective forward)  │
│  Cloud Call  │                    └──────────────────────────────┘
└──────────────┘
```
