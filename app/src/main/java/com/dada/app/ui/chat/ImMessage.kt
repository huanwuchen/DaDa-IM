package com.dada.app.ui.chat

import com.dada.core.common.domain.model.Message

/**
 * ImMessage 已迁移至 [Message]（domain/model/Message.kt）。
 *
 * 此 typealias 保持向后兼容，现有 Adapter / Helper 代码无需修改。
 */
typealias ImMessage = Message
