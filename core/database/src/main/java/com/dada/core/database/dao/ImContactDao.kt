package com.dada.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dada.core.database.entity.ImContactEntity
import kotlinx.coroutines.flow.Flow

/**
 * IM 通讯录 DAO
 *
 * 缓存「在线用户列表」结果。
 */
@Dao
interface ImContactDao {

    /**
     * 监听全部联系人（按用户名排序）
     */
    @Query("SELECT * FROM im_contacts ORDER BY username ASC")
    fun observeAll(): Flow<List<ImContactEntity>>

    /**
     * 一次性获取所有联系人
     */
    @Query("SELECT * FROM im_contacts ORDER BY username ASC")
    suspend fun getAll(): List<ImContactEntity>

    /**
     * 按 ID 查询
     */
    @Query("SELECT * FROM im_contacts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ImContactEntity?

    /**
     * 插入或更新单个
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ImContactEntity)

    /**
     * 批量插入或更新
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(contacts: List<ImContactEntity>)

    /**
     * 清空表
     */
    @Query("DELETE FROM im_contacts")
    suspend fun clearAll()

    /**
     * 用一次网络结果整体替换本地缓存：
     * 把全部数据先清空再批量写入，事务保证原子性。
     */
    @androidx.room.Transaction
    suspend fun replaceAll(contacts: List<ImContactEntity>) {
        clearAll()
        if (contacts.isNotEmpty()) upsertAll(contacts)
    }
}
