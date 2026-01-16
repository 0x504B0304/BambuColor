package com.m0h31h31.bambucolor.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.util.Log

class NfcReader(
    private val activity: Activity,
    private val onTagRead: (uidHex: String, rawHex: String) -> Unit,
    private val onError: (message: String) -> Unit = {}
) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun isSupported(): Boolean = adapter != null
    fun isEnabled(): Boolean = adapter?.isEnabled == true

    fun enable() {
        val nfcAdapter = adapter ?: run {
            onError("该设备不支持 NFC")
            return
        }
        if (!nfcAdapter.isEnabled) {
            onError("NFC 未开启，请在系统设置中开启")
            return
        }

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE

        nfcAdapter.enableReaderMode(activity, { tag ->
            try {
                val uidHex = tag.id.toHexString()
                val rawHex = tag.buildRawHex()
                Log.d("NFC", "UID=$uidHex")
                onTagRead(uidHex, rawHex)
            } catch (t: Throwable) {
                onError("读取标签失败：${t.message ?: t.javaClass.simpleName}")
            }
        }, flags, null)
    }

    fun disable() {
        adapter?.disableReaderMode(activity)
    }
}
