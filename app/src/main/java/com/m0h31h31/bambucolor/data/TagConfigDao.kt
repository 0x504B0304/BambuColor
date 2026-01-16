package com.m0h31h31.bambucolor.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagConfigDao {

    @Query("SELECT * FROM tag_config ORDER BY id DESC")
    fun observeAll(): Flow<List<TagConfigEntity>>

    @Query("SELECT * FROM tag_config WHERE uidHex = :uidHex LIMIT 1")
    suspend fun getByUid(uidHex: String): TagConfigEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: TagConfigEntity): Long

    @Update
    suspend fun update(item: TagConfigEntity)

    @Delete
    suspend fun delete(item: TagConfigEntity)

    /**
     * 首页关键查询：通过 UID 联表获取耗材信息
     */
    @Query("""
        SELECT 
            t.id AS tagId,
            t.uidHex AS uidHex,
            c.id AS consumableId,
            c.type AS type,
            c.colorName AS colorName,
            c.colorValueArgb AS colorValueArgb,
            c.colorCode AS colorCode,
            c.colorValuesHex AS colorValuesHex
        FROM tag_config t
        JOIN consumable_config c ON c.id = t.consumableId
        WHERE t.uidHex = :uidHex
        LIMIT 1
    """)
    suspend fun getTagWithConsumable(uidHex: String): TagWithConsumable?

    /**
     * 绑定/重绑：如果 UID 已存在则更新 consumableId
     */
    @Transaction
    suspend fun upsertBinding(uidHex: String, consumableId: Long) {
        val existing = getByUid(uidHex)
        if (existing == null) {
            insert(TagConfigEntity(uidHex = uidHex, consumableId = consumableId))
        } else {
            update(existing.copy(consumableId = consumableId))
        }
    }
}
