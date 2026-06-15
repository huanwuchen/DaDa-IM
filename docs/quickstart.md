# Quick Start

## Requirements

| Tool | Version |
|------|---------|
| JDK | 11+ |
| Android Studio | Arctic Fox (2020.3.1) or later |
| Android SDK | API 24+ (Android 7.0) |
| Gradle | 7.5.1 (wrapper included) |

## Setup

```bash
git clone https://github.com/huanwuchen/DaDa-IM.git
cd DaDa-IM

# Copy config template
cp gradle.properties.example gradle.properties
```

Edit `gradle.properties`:

```properties
# Required — your backend server
SERVER_BASE_URL=http://your-server-ip:8080
SERVER_WS_URL=ws://your-server-ip:8080

# Optional — JPush (offline push)
JPUSH_APPKEY=your_jpush_appkey_here
JPUSH_CHANNEL=developer-default

# Optional — Tencent Cloud (cloud calls)
TUI_SDK_APP_ID=0
TUI_SECRET_KEY=your_tui_secret_key_here

# Optional — AI Assistant
AI_API_URL=https://api.xiaomimimo.com/v1/chat/completions
AI_MODEL=mimo-v2.5-pro
AI_API_TOKEN=your_ai_api_token_here
```

Open in Android Studio, sync Gradle, run.

## Config Reference

| Key | Required | Without it |
|-----|----------|------------|
| `SERVER_BASE_URL` | Yes | Core chat won't work |
| `SERVER_WS_URL` | Yes | Realtime messaging won't work |
| `JPUSH_APPKEY` | No | No offline push |
| `TUI_SDK_APP_ID` | No | No cloud calls (LAN UDP still works) |
| `AI_API_TOKEN` | No | No AI assistant |

> Only the server address is required for core chat. JPush, TUICallKit, and AI degrade gracefully when unconfigured.

## Third-Party Setup

### JPush

1. Register at [jiguang.cn](https://www.jiguang.cn/)
2. Create app → get `AppKey`
3. Set `JPUSH_APPKEY` in `gradle.properties`

### Tencent Cloud TUICallKit

1. Register at [cloud.tencent.com](https://cloud.tencent.com/)
2. Enable TRTC → create app → get `SDKAppID` + `SecretKey`
3. Set in `gradle.properties` (or `0` to skip)

### MiMo AI

1. Get token at [api.xiaomimimo.com](https://api.xiaomimimo.com/)
2. Set `AI_API_TOKEN` in `gradle.properties` (or leave empty)

## Backend

This repo is the Android client only. The backend needs:

- **REST API** — auth, contacts, message sync, file upload
- **WebSocket** — realtime push at `ws://{host}:8080/ws/{userId}`
- **Push gateway** — JPush server-side SDK for offline push
