package com.dada.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dada.core.database.entity.ImUserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * IM 当前登录用户资料 DAO
 *
 * 表中只有一行，主键固定为 [ImUserProfileEntity.SINGLETON_ID]
 */
@Dao
interface ImUserProfileDao {

    /**
     * 监听用户资料；未登录或未缓存时返回 null
     */
    @Query("SELECT * FROM im_user_profile WHERE rowId = :rowId LIMIT 1")
    fun observe(rowId: Int = ImUserProfileEntity.SINGLETON_ID): Flow<ImUserProfileEntity?>

    /**
     * 一次性获取
     */
    @Query("SELECT * FROM im_user_profile WHERE rowId = :rowId LIMIT 1")
    suspend fun get(rowId: Int = ImUserProfileEntity.SINGLETON_ID): ImUserProfileEntity?

    /**
     * 插入或更新
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ImUserProfileEntity)

    /**
     * 清空（登出时调用）
     */
    @Query("DELETE FROM im_user_profile")
    suspend fun clear()
}
