package com.m0h31h31.bambucolor.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef

internal fun Tag.buildRawHex(): String {
    val sb = StringBuilder()

    // 1) UID
    sb.appendLine("UID:")
    sb.appendLine(id.toHexString())
    sb.appendLine()

    // 2) Tech 列表
    sb.appendLine("TECH:")
    techList.forEach { tech ->
        sb.appendLine(tech.substringAfterLast('.'))
    }
    sb.appendLine()

    // 3) 如果是 NDEF，尝试读取 NDEF message 并输出 bytes hex（不是所有卡都有）
    val ndef = Ndef.get(this)
    if (ndef != null) {
        try {
            ndef.connect()
            val msg: NdefMessage? = ndef.cachedNdefMessage
            if (msg != null) {
                val bytes = msg.toByteArray()
                sb.appendLine("NDEF (toByteArray):")
                sb.appendLine(bytes.toHexString(group = true))
            } else {
                sb.appendLine("NDEF:")
                sb.appendLine("(no cached message)")
            }
        } catch (_: Throwable) {
            sb.appendLine("NDEF:")
            sb.appendLine("(read failed or not supported)")
        } finally {
            try {
                ndef.close()
            } catch (_: Throwable) {}
        }
    } else {
        sb.appendLine("NDEF:")
        sb.appendLine("(not an NDEF tag)")
    }

    return sb.toString().trimEnd()
}

internal fun ByteArray.toHexString(group: Boolean = false): String {
    if (isEmpty()) return ""
    val sb = StringBuilder(size * 2 + if (group) size else 0)
    forEachIndexed { i, b ->
        val v = b.toInt() and 0xFF
        if (group && i > 0) sb.append(' ')
        sb.append(v.toString(16).padStart(2, '0').uppercase())
    }
    return sb.toString()
}
