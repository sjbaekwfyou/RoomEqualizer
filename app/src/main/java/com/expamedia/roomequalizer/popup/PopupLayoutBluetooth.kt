package com.expamedia.roomequalizer.popup

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.expamedia.roomequalizer.data.BluetoothDeviceInfo
import com.expamedia.roomequalizer.databinding.PopupLayoutBluetoothBinding

class PopupLayoutBluetooth (
    private val context: Context,
    private val title: String,
    private val deviceList: List<BluetoothDeviceInfo>,
    private val onDeviceSelected: (BluetoothDeviceInfo) -> Unit
) {

    @SuppressLint("MissingPermission")
    fun show() {
        val binding = PopupLayoutBluetoothBinding.inflate(LayoutInflater.from(context))

        // 기기 이름 및 MAC 주소 문자열 목록 생성
        val deviceNames = deviceList.map { device ->
            val name = device.name ?: "알 수 없는 기기"
            "$name\n(${device.address})"
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, deviceNames)
        binding.listViewDevices.adapter = adapter

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(binding.root)
            .create()

        binding.listViewDevices.setOnItemClickListener { _, _, position, _ ->
            val selectedDevice = deviceList[position]
            onDeviceSelected(selectedDevice) // 선택된 장비 전달
            dialog.dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
