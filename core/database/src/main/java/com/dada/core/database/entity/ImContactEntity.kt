package com.dada.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * IM 通讯录联系人 Entity
 *
 * 缓存「在线用户列表」，下次进入通讯录页可立即从本地展示，再请求网络刷新。
 *
 * @property id          用户 ID（主键）
 * @property deviceId    设备 ID
 * @property username    昵称
 * @property avatar      头像 URL
 * @property online      最近一次拉取时是否在线
 * @property updateTime  本地最后一次更新时间（毫秒），用于过期判断
 */
@Entity(tableName = "im_contacts")
data class ImContactEntity(
    @PrimaryKey
    val id: Long,
    val deviceId: String = "",
    val username: String,
    val avatar: String? = null,
    val coverImage: String? = null,
    val online: Boolean = false,
    val updateTime: Long = System.currentTimeMillis(),
)
