package com.dada.core.database.entity

import com.dada.core.common.data.model.ChatMessage
import com.dada.core.common.data.model.ChatRole
import com.dada.core.common.data.model.ChatType

/**
 * ChatMessageEntity 与 ChatMessage 之间的转换扩展函数
 */

/**
 * 将 Entity 转换为领域模型
 */
fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    role = ChatRole.valueOf(role),
    type = ChatType.valueOf(type),
    text = text,
    voicePath = voicePath,
    voiceDurationSec = voiceDurationSec,
    timestamp = timestamp,
)

/**
 * 将领域模型转换为 Entity
 */
fun ChatMessage.toEntity(): ChatMessageEntity = ChatMessageEntity(
    id = id,
    role = role.name,
    type = type.name,
    text = text,
    voicePath = voicePath,
    voiceDurationSec = voiceDurationSec,
    timestamp = timestamp,
)
