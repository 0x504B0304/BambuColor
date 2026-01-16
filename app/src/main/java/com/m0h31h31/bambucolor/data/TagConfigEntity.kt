package com.m0h31h31.bambucolor.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 标签配置库
 * id | 序列号(UID) | 耗材配置库_id
 */
@Entity(
    tableName = "tag_config",
    foreignKeys = [
        ForeignKey(
            entity = ConsumableEntity::class,
            parentColumns = ["id"],
            childColumns = ["consumableId"],
            onDelete = ForeignKey.RESTRICT, // 有绑定时不允许删耗材（更安全）
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uidHex"], unique = true),
        Index(value = ["consumableId"])
    ]
)
data class TagConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uidHex: String,          // NFC Tag UID（建议统一大写HEX）
    val consumableId: Long       // 外键指向 consumable_config.id
)
