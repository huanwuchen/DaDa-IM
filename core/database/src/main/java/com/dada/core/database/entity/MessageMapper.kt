package com.dada.core.database.entity

import com.dada.core.common.domain.model.Message

fun ImMessageEntity.toDomain(): Message = Message(
    id = id,
    fromId = fromId,
    toId = toId,
    content = content,
    type = type,
    time = timestamp,
    thumbUrl = thumbUrl,
    duration = duration,
    size = size,
    width = width,
    height = height,
    fileName = fileName,
    avatar = avatar,
    iconRes = iconRes,
)
