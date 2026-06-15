package com.dada.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * IM 当前登录用户的资料 Entity
 *
 * 表中只会有一行记录（主键固定为 [SINGLETON_ID]），用于「我」页面回显，
 * 也可作为后续扩展（多账号切换时改为多行存储）的基础。
 *
 * @property userId    我的用户 ID
 * @property deviceId  设备 ID
 * @property username  昵称
 * @property avatar    头像
 * @property updateTime 最近一次更新时间
 */
@Entity(tableName = "im_user_profile")
data class ImUserProfileEntity(
    @PrimaryKey
    val rowId: Int = SINGLETON_ID,
    val userId: Long,
    val deviceId: String,
    val username: String,
    val avatar: String? = null,
    val coverImage: String? = null,
    val updateTime: Long = System.currentTimeMillis(),
) {
    companion object {
        /** 单行表的固定主键 */
        const val SINGLETON_ID: Int = 1
    }
}
