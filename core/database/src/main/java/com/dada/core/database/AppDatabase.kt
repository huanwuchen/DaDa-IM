package com.dada.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dada.core.database.dao.ChatMessageDao
import com.dada.core.database.dao.ImContactDao
import com.dada.core.database.dao.ImConversationDao
import com.dada.core.database.dao.ImMessageDao
import com.dada.core.database.dao.ImUserProfileDao
import com.dada.core.database.entity.ChatMessageEntity
import com.dada.core.database.entity.ImContactEntity
import com.dada.core.database.entity.ImConversationEntity
import com.dada.core.database.entity.ImMessageEntity
import com.dada.core.database.entity.ImUserProfileEntity

/**
 * 应用数据库
 *
 * 包含的表：
 *  - chat_messages       AI 聊天消息（旧）
 *  - im_contacts         IM 通讯录
 *  - im_conversations    IM 会话列表
 *  - im_messages         IM 单条消息
 *  - im_user_profile     IM 当前登录用户资料（单行表）
 *
 * 版本变更：
 *  - 1: 仅 chat_messages
 *  - 2: 新增 im_contacts / im_conversations / im_messages / im_user_profile
 */
@Database(
    entities = [
        ChatMessageEntity::class,
        ImContactEntity::class,
        ImConversationEntity::class,
        ImMessageEntity::class,
        ImUserProfileEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun imContactDao(): ImContactDao
    abstract fun imConversationDao(): ImConversationDao
    abstract fun imMessageDao(): ImMessageDao
    abstract fun imUserProfileDao(): ImUserProfileDao
}
