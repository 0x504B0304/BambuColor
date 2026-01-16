package com.m0h31h31.bambucolor.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 耗材配置库
 * id | 耗材类型 | 色名 | 色值 | 色号
 *
 * 色值建议存 Int（ARGB），例如 0xFF00FF00
 */
@Entity(
    tableName = "consumable_config",
    indices = [
        Index(value = ["type"]),
        Index(value = ["colorName"]),
        Index(value = ["colorCode"]),
    ]
)
data class ConsumableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,          // e.g. PLA, PETG, ABS...
    val colorName: String,     // e.g. "Jade Green"
    val colorValueArgb: Int,   // e.g. 0xFFRRGGBB
    val colorCode: String,     // e.g. "A12" or厂家的色号
    val colorValuesHex: String // e.g. "#FF112233,#FF445566" for multi-color
)
