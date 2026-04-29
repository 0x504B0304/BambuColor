package com.m0h31h31.bambucolor.nfc

data class TagDataParsed(
    val uidHex: String,
    val materialId: String,
    val variantId: String,
    val filamentType: String,
    val detailedFilamentType: String,
    val colorArgb: Int,
    val colorArgbSecond: Int?,
    val colorCount: Int,
    val spoolWeightG: Int,
    val filamentDiameterMm: Float,
    val filamentLengthM: Int,
    val spoolWidthMm: Float,
    val nozzleDiameterMm: Float,
    val dryingTempC: Int,
    val dryingTimeH: Int,
    val bedTempC: Int,
    val minHotendC: Int,
    val maxHotendC: Int,
    val productionDate: String,
    val trayUid: String,
)
