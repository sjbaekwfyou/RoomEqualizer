package com.expamedia.roomequalizer.data

import android.bluetooth.BluetoothDevice

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val originalDevice: BluetoothDevice? = null // 실제 기기 객체 (가 데이터일 땐 null)
)