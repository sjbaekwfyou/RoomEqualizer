package com.expamedia.roomequalizer.data

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val name: String,
    val address: String
)

sealed class ScanState {
    data object Idle : ScanState()
    data object Scanning : ScanState()
    data class Error(val msg: String) : ScanState()
}
