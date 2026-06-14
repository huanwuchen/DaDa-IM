# Performance

## Message Send Delay

| 场景 | 延迟 |
|------|------|
| 局域网 (WiFi) | 15-30ms |
| 公网 (4G) | 50-150ms |

## Database Query

| 操作 | 耗时 |
|------|------|
| 会话列表查询（50 个会话） | < 20ms |
| 单条消息插入 | < 5ms |
| 历史消息加载（20 条） | < 15ms |

## Memory

| 组件 | 占用 |
|------|------|
| LRU 去重缓存 | 500 条消息 ID，约 200KB |
| Room 连接池 | 约 2-3MB |
| WebSocket 连接 | 约 1MB |

## Connection Recovery

| 场景 | 恢复时间 |
|------|---------|
| WiFi → 4G 切换 | 3-5s |
| 进程被杀重启 | 5-8s（前台 Service 重启 + 重连） |
| 服务端重启 | 8-15s（心跳超时 + 重连） |
