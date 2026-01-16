package com.m0h31h31.bambucolor.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsumableDao {

    @Query("SELECT * FROM consumable_config ORDER BY id DESC")
    fun observeAll(): Flow<List<ConsumableEntity>>

    @Query("SELECT * FROM consumable_config WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ConsumableEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ConsumableEntity): Long

    @Update
    suspend fun update(item: ConsumableEntity)

    @Delete
    suspend fun delete(item: ConsumableEntity)

    @Query("""
        SELECT * FROM consumable_config
        WHERE type LIKE '%' || :q || '%'
           OR colorName LIKE '%' || :q || '%'
           OR colorCode LIKE '%' || :q || '%'
        ORDER BY id DESC
    """)
    fun search(q: String): Flow<List<ConsumableEntity>>
}
