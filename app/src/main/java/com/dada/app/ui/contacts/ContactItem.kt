package com.dada.app.ui.contacts

import com.dada.core.database.entity.ImContactEntity

/**
 * UI 层使用的「联系人」简单模型
 *
 * 与 Entity 解耦，方便后续追加 UI 衍生字段（在线状态颜色、是否星标等）
 *
 * @property id        用户 ID
 * @property username  昵称
 * @property avatar    头像
 * @property online    是否在线
 */
data class ContactItem(
    val id: Long,
    val username: String,
    val avatar: String? = null,
    val online: Boolean = false,
)

/**
 * Entity -> UI 模型
 */
fun ImContactEntity.toContactItem(): ContactItem = ContactItem(
    id = id,
    username = username,
    avatar = avatar,
    online = online,
)
