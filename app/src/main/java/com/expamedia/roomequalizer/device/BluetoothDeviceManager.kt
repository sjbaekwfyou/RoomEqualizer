package com.expamedia.roomequalizer.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import com.expamedia.roomequalizer.data.BluetoothDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothDeviceManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // 일반 블루투스 SPP(Serial Port Profile) 표준 UUID
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    /**
     * 이미 페어링되어 등록된 블루투스 장비 리스트 가져오기
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        var pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        return pairedDevices.map { device ->
            @SuppressLint("MissingPermission")
            BluetoothDeviceInfo(device.name ?: "Unknown", device.address, device)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun isAudioDevice(device: BluetoothDevice): Boolean {
        val bluetoothClass = device.bluetoothClass ?: return false
        val deviceClass = bluetoothClass.deviceClass

        return when (deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> true

            else -> {
                bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getNonAudioPairedDevices(): List<BluetoothDeviceInfo> {
        val pairedDevices = bluetoothAdapter?.bondedDevices ?: return emptyList()

        if(pairedDevices.isEmpty()) {
            return listOf(
                BluetoothDeviceInfo("Sony WH-1000XM5 (Dummy)", "00:11:22:33:44:55"),
                BluetoothDeviceInfo("Galaxy Buds2 Pro (Dummy)", "AA:BB:CC:DD:EE:FF"),
                BluetoothDeviceInfo("AirPods Max (Dummy)", "12:34:56:78:90:AB"),
                BluetoothDeviceInfo("Bose QuietComfort 45 (Dummy)", "98:76:54:32:10:FE")
            )
        }

        return pairedDevices
            .filter { device ->
                // 오디오(마이크/스피커) 기기가 아닌 장치만 필터링
                !isAudioDevice(device)
            }
            .map { device ->
                BluetoothDeviceInfo(
                    name = device.name ?: "알 수 없는 기기",
                    address = device.address,
                    originalDevice = device
                )
            }
    }

    @SuppressLint("MissingPermission")
    fun getAudioPairedDevices(): List<BluetoothDeviceInfo> {
        val pairedDevices = bluetoothAdapter?.bondedDevices ?: return emptyList()

        return pairedDevices
            .filter { device ->
                // 오디오(마이크/스피커) 기기 장치만 필터링
                isAudioDevice(device)
            }
            .map { device ->
                BluetoothDeviceInfo(
                    name = device.name ?: "알 수 없는 기기",
                    address = device.address,
                    originalDevice = device
                )
            }
    }

    /**
     * 주변 신규 블루투스 장비 스캔 시작
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery(onDeviceFound: (BluetoothDevice) -> Unit, receiverOut: (BroadcastReceiver) -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let { onDeviceFound(it) }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
        receiverOut(receiver)

        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        bluetoothAdapter?.startDiscovery()
    }

    /**
     * 블루투스 마이크/스피커 오디오 입출력 경로 연결
     */
    @SuppressLint("MissingPermission")
    fun connectAudioDevice(deviceInfo: BluetoothDeviceInfo, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 이상 (API 31+): CommunicationDevice 설정
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS or AudioManager.GET_DEVICES_INPUTS)
            val targetAudioDevice = audioDevices.find { audioDev ->
                (deviceInfo.address == audioDev.address) ||
                        (audioDev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                audioDev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                audioDev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            }

            if (targetAudioDevice != null) {
                val success = audioManager.setCommunicationDevice(targetAudioDevice)
                onResult(success)
            } else {
                startBluetoothSco()
                onResult(true)
            }
        } else {
            // Android 11 이하: SCO 모드 활성화
            startBluetoothSco()
            onResult(true)
        }
    }

    @Suppress("DEPRECATION")
    private fun startBluetoothSco() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
    }

    /**
     * 오디오 연결 해제 및 기본 내장 스피커/마이크 복구
     */
    @Suppress("DEPRECATION")
    fun disconnectAudioDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        if (audioManager.isBluetoothScoOn) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }


    // =========================================================================
    // 2. 블루투스 "일반 장치" 연결 (소켓 통신 / 데이터 제어용)
    // =========================================================================

    /**
     * 일반 블루투스 장치와 RFCOMM(SPP) 소켓 연결 (코루틴 비동기 처리)
     */
    @SuppressLint("MissingPermission")
    suspend fun connectGeneralDevice(deviceInfo: BluetoothDeviceInfo): Boolean = withContext(
        Dispatchers.IO) {
        val device: BluetoothDevice = deviceInfo.originalDevice ?: run {
            // 더미 데이터이거나 객체가 없을 때 예외 처리
            return@withContext false
        }

        // 스캔 진행 중이라면 중지
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }

        try {
            // 기존 소켓 닫기
            disconnectGeneralDevice()

            // RFCOMM 소켓 생성 및 연결
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket?.connect()
            true
        } catch (e: IOException) {
            e.printStackTrace()
            disconnectGeneralDevice()
            false
        }
    }

    /**
     * 일반 블루투스 소켓 연결 후 입출력 스트림 초기화
     */
    fun setupStreams() {
        try {
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * 1. 데이터 전송 (OutputStream.write)
     * 문자열 데이터를 텍스트(ByteArray) 형태로 변환하여 전송합니다.
     */
    suspend fun sendData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            outputStream?.write(data)
            outputStream?.flush() // 버퍼 비우기
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 1. 데이터 전송 (OutputStream.write)
     * 문자열 데이터를 텍스트(ByteArray) 형태로 변환하여 전송합니다.
     */
    suspend fun sendData(data: String): Boolean = withContext(Dispatchers.IO) {
        val bytesToSend = data.toByteArray(Charsets.UTF_8)
        sendData(bytesToSend)
    }

    /**
     * 2. 데이터 수신 대기 (InputStream.read)
     * 소켓이 닫히거나 코루틴이 취소될 때까지 백그라운드에서 데이터를 지속적으로 수신합니다.
     */
    suspend fun startListening(onDataReceived: (String) -> Unit) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(1024) // 1KB 버퍼
        var bytes: Int

        while (isActive && bluetoothSocket?.isConnected == true) {
            try {
                // 데이터가 들어올 때까지 READ_BLOCKING 방식으로 대기
                bytes = inputStream?.read(buffer) ?: -1
                if (bytes > 0) {
                    val receivedMessage = String(buffer, 0, bytes, Charsets.UTF_8)

                    // 수신된 데이터를 메인 UI 스레드로 전달할 수 있도록 콜백 호출
                    withContext(Dispatchers.Main) {
                        onDataReceived(receivedMessage)
                    }
                } else if (bytes == -1) {
                    // 스트림 끝 (연결 종료됨)
                    break
                }
            } catch (e: IOException) {
                e.printStackTrace()
                break
            }
        }
    }

    /**
     * 소켓 및 스트림 자원 해제
     */
    fun disconnectGeneralDevice() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            inputStream = null
            outputStream = null
            bluetoothSocket = null
        }
    }
}
