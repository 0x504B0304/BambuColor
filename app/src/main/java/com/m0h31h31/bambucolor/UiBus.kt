package com.m0h31h31.bambucolor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object UiBus {
    private val _lastUid = MutableStateFlow<String?>(null)
    val lastUid: StateFlow<String?> = _lastUid

    fun publishUid(uidHex: String) {
        _lastUid.value = uidHex
    }
}