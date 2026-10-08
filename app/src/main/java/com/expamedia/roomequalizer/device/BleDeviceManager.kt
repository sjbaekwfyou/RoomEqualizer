package com.expamedia.roomequalizer.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import android.os.Build
import android.bluetooth.BluetoothStatusCodes
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import com.expamedia.roomequalizer.data.BleDevice
import com.expamedia.roomequalizer.data.ScanState
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

class BleDeviceManager(private val context: Context) : ViewModel() {

    // ✅ ESP32 쪽에서 사용한 Service / Characteristic UUID 로 교체해야 함
    private val SERVICE_UUID = UUID.fromString("e49a25f8-f69a-11e8-8eb2-f2801f1b9fd1")
    private val WRITE_CHAR_UUID = UUID.fromString("e49a25e0-f69a-11e8-8eb2-f2801f1b9fd1")  // RX (Write)
    private val NOTIFY_CHAR_UUID = UUID.fromString("e49a28e1-f69a-11e8-8eb2-f2801f1b9fd1") // TX (Notify)

    // ✅ GATT 연결 객체 & write용 characteristic
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    fun isConnected(): Boolean = gatt != null

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    private var scanCallback: ScanCallback? = null
    private var timeoutJob: Job? = null

    val readyForList = AtomicBoolean(false)
    private fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it) }

    /** �� 스캔 시작 (10초 후 자동 중지) */
    @SuppressLint("MissingPermission")
    fun startScan(scalCallback: (BleDevice) -> Unit) {
        val btAdapter = adapter ?: run {
            Log.e("BLE_test", "❌ BluetoothAdapter is null — BLE not supported on this device.")
            Toast.makeText(context, "This device does not support BLE.", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ 권한 체크 (Android 12+는 위치 권한 요구 X)
        val requiredPerms = buildList {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            // Android 11 이하만 위치 권한 필요
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        val missing = requiredPerms.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            Log.w("BLE_test", "⚠️ Permission denied: $missing")
            Toast.makeText(context, "Please allow nearby devices permission.", Toast.LENGTH_SHORT).show()
            return
        }

        val scanner = btAdapter.bluetoothLeScanner ?: run {
            Log.e("BLE_test", "❌ BluetoothLeScanner is null — Failed to create BLE scanner.")
            Toast.makeText(context, "Cannot start BLE scan.", Toast.LENGTH_SHORT).show()
            return
        }

        if (_scanState.value == ScanState.Scanning) {
            Log.w("BLE_test", "⚠️ Already scanning.")
            return
        }

        // 이전 콜백 중복 방지
        scanCallback?.let {
            Log.w("BLE_test", "⚠️ Stop existing scan and start new one.")
            scanner.stopScan(it)
        }

        val discovered = LinkedHashMap<String, BleDevice>()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName ?: "Unknown"
                if (name == "Unknown") return // Filter Unknown

                val addr = result.device.address ?: return
                val rssi = result.rssi
                val bleDevice = BleDevice("$name (RSSI:$rssi)", addr)

                discovered[addr] = bleDevice
                _devices.value = discovered.values.toList()
                Log.d("BLE_test", "Discovered: $name [$addr] RSSI:$rssi")

                scalCallback.invoke(bleDevice)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (r in results) {
                    val name = r.device.name ?: r.scanRecord?.deviceName ?: "Unknown"
                    if (name == "Unknown") continue // Filter Unknown

                    val addr = r.device.address ?: continue
                    val bleDevice = BleDevice("$name (RSSI:${r.rssi})", addr)
                    discovered[addr] = bleDevice

                    scalCallback.invoke(bleDevice)
                }
                _devices.value = discovered.values.toList()
                Log.d("BLE_test", "Batch scan results: ${results.size}")
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE_test", "❌ Scan failed (errorCode=$errorCode)")
                _scanState.value = ScanState.Error("Scan failed ($errorCode)")
                when (errorCode) {
                    1 -> Log.e("BLE_test", "SCAN_FAILED_ALREADY_STARTED — Already scanning")
                    2 -> Log.e("BLE_test", "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED — BLE permission/system issue")
                    3 -> Log.e("BLE_test", "SCAN_FAILED_FEATURE_UNSUPPORTED — BLE scan not supported on this device")
                    4 -> Log.e("BLE_test", "SCAN_FAILED_INTERNAL_ERROR — System BLE stack error (Reboot required)")
                    5 -> Log.e("BLE_test", "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES — BLE buffer shortage")
                }
                stopScan()
            }
        }

        // 스캔 시작
        scanCallback = callback
        scanner.startScan(null, settings, callback)
        _scanState.value = ScanState.Scanning
        Log.i("BLE_test", "✅ BLE scan started")

        // �� 10초 후 자동 중지
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(10_000.milliseconds)
            if (_scanState.value == ScanState.Scanning) {
                stopScan()
                if (_devices.value.isEmpty()) {
                    _scanState.value = ScanState.Error("No devices found in 10 seconds.")
                    Log.w("BLE_test", "⏰ BLE device not found for 10 seconds")
                    Toast.makeText(context, "No BLE devices found nearby.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** �� 스캔 중지 */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        val btAdapter = adapter ?: return
        val scanner = btAdapter.bluetoothLeScanner ?: return
        scanCallback?.let {
            scanner.stopScan(it)
            Log.i("BLE_test", "�� BLE scan stopped")
        }
        scanCallback = null
        timeoutJob?.cancel()
        _scanState.value = ScanState.Idle
    }

    /** �� 기기 연결 (GATT 콜백 포함) */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BleDevice, connectionResult: (Boolean) -> Unit, receiveCallback: (ByteArray) -> Unit) {
        val btDevice = adapter?.getRemoteDevice(device.address)
        if (btDevice == null) {
            Log.e("BLE_test", "❌ getRemoteDevice failed: ${device.address}")
            return
        }

        Log.d("BLE_test", "�� Attempting connection: ${device.name} (${device.address})")

        gatt = btDevice.connectGatt(context, false, object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i("BLE_test", "✅ GATT connected, starting service discovery")
                        this@BleDeviceManager.gatt = gatt
                        gatt.discoverServices()

                        viewModelScope.launch(Dispatchers.Main) {
                            /*Toast.makeText(
                                context,
                                "${device.name} connected.",
                                Toast.LENGTH_SHORT
                            ).show()*/
                            connectionResult.invoke(true)
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.w("BLE_test", "�� GATT disconnected")
                        this@BleDeviceManager.gatt = null
                        writeChar = null

                        viewModelScope.launch(Dispatchers.Main) {
                            /*Toast.makeText(
                                context,
                                "Disconnected.",
                                Toast.LENGTH_SHORT
                            ).show()*/
                            connectionResult.invoke(false)
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status != BluetoothGatt.GATT_SUCCESS) return

                val service = gatt.getService(SERVICE_UUID) ?: run {
                    Toast.makeText(
                        context,
                        "Unsupported device or connection failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                writeChar = service.getCharacteristic(WRITE_CHAR_UUID)
                val notifyChar = service.getCharacteristic(NOTIFY_CHAR_UUID) ?: return

                Log.i("BLE_test", "✅ 서비스 및 캐릭터리스틱 찾기 성공")

                // 1. 앱 레벨 알림 등록
                gatt.setCharacteristicNotification(notifyChar, true)

                // 2. 코루틴으로 안전한 지연 후 기기(ESP32) 구독 설정
                viewModelScope.launch(Dispatchers.Main) {
                    delay(300.milliseconds) // GATT Busy 방지를 위한 대기

                    val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    val cccd = notifyChar.getDescriptor(cccdUuid)

                    if (cccd != null) {
                        val supportsIndicate = (notifyChar.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                        val value = if (supportsIndicate)
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        else
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
                        } else {
                            @Suppress("DEPRECATION")
                            cccd.value = value
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(cccd)
                        }
                        Log.d("BLE_test", "최종 CCCD 구독 결과: $success")

                        if (success) {
                            delay(150.milliseconds)
                            // Status Request (Request All) moved to onMtuChanged
                            gatt.requestMtu(64) // MTU 요청
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                            readyForList.set(true)
                        }
                    }
                }
            }

            // [CHG] 콜백 내부의 onCharacteristicChanged 를 아래로 교체
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (characteristic.uuid != NOTIFY_CHAR_UUID) return
                if (value.isEmpty()) return

                when (val header = value[0].toInt() and 0xFF) {
                    0xF1 -> {
                        // 파일 리스트 (기존 로직 그대로)
                        Log.d("BLE_file", "RX chunk F1 (${value.size}B): ${value.hex()}")
                        receiveCallback.invoke(value)
                    }
                    0xED -> {
                        Log.d("BLE_effect", "RX ED frame (${value.size}B): ${value.hex()}")
                        receiveCallback.invoke(value)
                    }
                    else -> {
                        Log.w("BLE_misc", "unknown header=0x${"%02X".format(header)} : ${value.hex()}")
                    }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i("BLE_test", "💾 Characteristic write success")
                } else {
                    Log.e("BLE_test", "❌ Characteristic write failed: status=$status")
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                Log.d("BLE_test", "onMtuChanged: mtu=$mtu, status=$status")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
            Log.i("BLE_test", "�� GATT disconnected")

            /*viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Disconnected.",
                    Toast.LENGTH_SHORT
                ).show()
            }*/

        } catch (e: Exception) {
            Log.e("BLE_test", "❌ Error during disconnect: ${e.message}")
        } finally {
            gatt = null
            writeChar = null
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    public fun write(bytes: ByteArray): Boolean {
        val g = gatt ?: run {
            Toast.makeText(context, "Not connected.", Toast.LENGTH_SHORT).show()
            return false
        }
        val ch = writeChar ?: run {
            Toast.makeText(context, "Service not ready yet.", Toast.LENGTH_SHORT).show()
            return false
        }

        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            status == BluetoothStatusCodes.SUCCESS
        } else {
            ch.value = bytes
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            g.writeCharacteristic(ch)
        }
        Log.d("BLE_test", "�� write: ${bytes.joinToString(" ") { "%02X".format(it) }} (ok=$ok)")

        if (!ok) {
            Log.w("BLE_test", "⚠ writeCharacteristic() false - GATT busy or error")
        }

        return ok
    }
}
