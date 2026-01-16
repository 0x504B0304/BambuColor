package com.m0h31h31.bambucolor.data


/**
 * 首页常用：通过 UID 查绑定 + 联表取耗材信息
 */
data class TagWithConsumable(
    val tagId: Long,
    val uidHex: String,
    val consumableId: Long,
    val type: String,
    val colorName: String,
    val colorValueArgb: Int,
    val colorCode: String,
    val colorValuesHex: String
)
