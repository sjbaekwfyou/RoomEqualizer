package com.expamedia.roomequalizer.popup

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.expamedia.roomequalizer.R
import com.expamedia.roomequalizer.data.BluetoothDeviceInfo
import com.expamedia.roomequalizer.databinding.PopupLayoutBluetoothBinding

class PopupLayoutBluetooth (
    private val context: Context,
    private val title: String
) {
    private val binding: PopupLayoutBluetoothBinding by lazy {
        PopupLayoutBluetoothBinding.inflate(LayoutInflater.from(context))
    }

    private val dialog: AlertDialog by lazy {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(binding.root)
            .create()
    }

    private val displayDeviceList = mutableListOf<BluetoothDeviceInfo>()
    private val displayNames = mutableListOf<String>()
    private var adapter: ArrayAdapter<String>? = null

    private fun clearDevices() {
        displayDeviceList.clear()
        adapter?.clear()
        adapter?.notifyDataSetChanged()
    }

    @SuppressLint("MissingPermission")
    fun show(deviceList: List<BluetoothDeviceInfo>, onDeviceSelected: (BluetoothDeviceInfo) -> Unit) {
        clearDevices()

        displayDeviceList.addAll(deviceList)

        // 기기 이름 및 MAC 주소 문자열 목록 생성
        val deviceNames = displayDeviceList.map { device ->
            val name = device.name ?: (context as AppCompatActivity).getString(R.string.unknown_device)
            "$name\n(${device.address})"
        }

        adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, deviceNames)
        binding.listViewDevices.adapter = adapter

        binding.listViewDevices.setOnItemClickListener { _, _, position, _ ->
            val selectedDevice = displayDeviceList[position]
            onDeviceSelected(selectedDevice) // 선택된 장비 전달
            dialog.dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }

    /**
     * 💡 3. 외부(스캔 콜백)에서 실시간으로 새 기기를 추가할 때 호출하는 함수
     */
    fun addDevice(deviceInfo: BluetoothDeviceInfo) {
        // 중복 추가 방지 (MAC 주소 기준)
        if (displayDeviceList.any { it.address == deviceInfo.address }) return

        displayDeviceList.add(deviceInfo)

        val name = deviceInfo.name.ifBlank { (context as AppCompatActivity).getString(R.string.unknown_device) }
        val displayText = "$name\n(${deviceInfo.address})"

        // UI 스레드에서 어댑터에 데이터 추가 및 갱신
        adapter?.add(displayText)
        adapter?.notifyDataSetChanged()
    }
}
