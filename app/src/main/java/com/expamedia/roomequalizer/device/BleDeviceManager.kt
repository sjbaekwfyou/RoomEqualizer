package com.expamedia.roomequalizer.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
import com.expamedia.roomequalizer.R
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

@SuppressLint("StaticFieldLeak")
class BleDeviceManager(context: Context) : ViewModel() {
    private val context: Context = context.applicationContext

    enum class ModeType {
        PLAY_MODE,
        MEASUREMENT_MODE
    }

    enum class EqModeType {
        EQ_MODE_DEFAULT,
        EQ_MODE_USER1,
        EQ_MODE_USER2
    }

    private final val TAG = "BleDeviceManager"
    private final val TAG_BLE_DATA_RX = "BleDataRx"
    private final val TAG_BLE_DATA_TX = "BleDataTx"
    private val parseCmdDatas = mutableListOf<Byte>()

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

    /*
     * App → BT {0xec, 0xa0, 0x01, 0x72}, BT → App Return {0xed, 0xa0, 0x01, 0x71}
     * 통신 성공하면 AI Music Box 위의 Mic Reverb LED 중 Low 가 켜짐
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    val cmdPlayModeTx = ubyteArrayOf(0xecu, 0xa0u, 0x01u, 0x72u).toByteArray()
    @OptIn(ExperimentalUnsignedTypes::class)
    val cmdPlayModeRx = ubyteArrayOf(0xedu, 0xa0u, 0x01u, 0x71u).toByteArray()
    /*
     * App → BT {0xec, 0xa0, 0x02, 0x71}, BT → App Return {0xed, 0xa0, 0x02, 0x70}
     * 통신 성공하면 AI Music Box 위의 Mic Reverb LED 중 Mid 가 켜짐
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    val cmdMeasurementModeTx = ubyteArrayOf(0xedu, 0xa0u, 0x01u, 0x71u).toByteArray()
    @OptIn(ExperimentalUnsignedTypes::class)
    val cmdMeasurementModeRx = ubyteArrayOf(0xedu, 0xa0u, 0x02u, 0x70u).toByteArray()
    /*
     * App → BT {0xec, 0xb0, 0x01, 0x62}, BT → App Return {0xed, 0xb0, 0x01, 0x61}
     * 통신 성공하면 AI Music Box 위의 Vocal Effect LED 중 Minions 가 켜짐
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    val cmdEqModeDefaultTx = ubyteArrayOf(0xecu, 0xb0u, 0x01u, 0x62u).toByteArray()
    @OptIn(ExperimentalUnsignedTypes::class)
    val cmdEqModeDefaultRx = ubyteArrayOf(0xedu, 0xb0u, 0x01u, 0x61u).toByteArray()
    /*
     * App → BT {0xec, 0xb0, 0x02, 0x61}, BT → App Return {0xed, 0xb0, 0x02, 0x60}
     * 통신 성공하면 AI Music Box 위의 Vocal Effect LED 중 Monster 가 켜짐
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    val cmdEqModeUser1Tx = ubyteArrayOf(0xecu, 0xb0u, 0x02u, 0x61u).toByteArray()
    @OptIn(ExperimentalUnsignedTypes::class)
    val cmdEqModeUser1Rx = ubyteArrayOf(0xedu, 0xb0u, 0x02u, 0x60u).toByteArray()
    /*
     * App → BT {0xec, 0xb0, 0x03, 0x60}, BT → App Return {0xed, 0xb0, 0x03, 0x5f}
     * 통신 성공하면 AI Music Box 위의 Vocal Effect LED 중 Monster 가 켜짐
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    val cmdEqModeUser2Tx = ubyteArrayOf(0xecu, 0xb0u, 0x03u, 0x60u).toByteArray()
    @OptIn(ExperimentalUnsignedTypes::class)
    val cmdEqModeUser2Rx = ubyteArrayOf(0xedu, 0xb0u, 0x03u, 0x5fu).toByteArray()
    /*
     * return value : 0(default), 1(User1), 2(User2)
     * App → BT {0xec, 0xb0, 0x05, 0x5e}, BT → App Return {0xed, 0xb0, 0x05, 0x5d}
     * 통신 성공하면 AI Music Box 위의 Vocal Effect LED 중 Duet  켜짐
     * 회신된 4 byte 데이터 중 (3번째 바이트-0x04) return 함 (현재 AI Music Box 펌웨어에서는 회신값이 고정으므로 return 값 항상 1임)
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    val cmdGetEqModeTx = ubyteArrayOf(0xecu, 0xb0u, 0x05u, 0x5eu).toByteArray()
    @OptIn(ExperimentalUnsignedTypes::class)
    val cmdGetEqModeRx = ubyteArrayOf(0xecu, 0xb0u, 0x04u, 0x5du).toByteArray()

    private fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it) }

    /** �� 스캔 시작 (10초 후 자동 중지) */
    @SuppressLint("MissingPermission")
    fun startScan(scalCallback: (BleDevice) -> Unit) {
        val btAdapter = adapter ?: run {
            Log.e(TAG, "❌ BluetoothAdapter is null — BLE not supported on this device.")
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
            Log.w(TAG, "⚠️ Permission denied: $missing")
            Toast.makeText(context, "Please allow nearby devices permission.", Toast.LENGTH_SHORT).show()
            return
        }

        val scanner = btAdapter.bluetoothLeScanner ?: run {
            Log.e(TAG, "❌ BluetoothLeScanner is null — Failed to create BLE scanner.")
            Toast.makeText(context, "Cannot start BLE scan.", Toast.LENGTH_SHORT).show()
            return
        }

        if (_scanState.value == ScanState.Scanning) {
            Log.w(TAG, "⚠️ Already scanning.")
            return
        }

        // 이전 콜백 중복 방지
        scanCallback?.let {
            Log.w(TAG, "⚠️ Stop existing scan and start new one.")
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
                Log.d(TAG, "Discovered: $name [$addr] RSSI:$rssi")

                scalCallback.invoke(bleDevice)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (r in results) {
                    val name = r.device.name ?: r.scanRecord?.deviceName ?: "Unknown"
                    if (name == "Unknown") continue // Filter Unknown

                    val address = r.device.address ?: continue
                    val bleDevice = BleDevice("$name (RSSI:${r.rssi})", address)
                    discovered[address] = bleDevice

                    scalCallback.invoke(bleDevice)
                }
                _devices.value = discovered.values.toList()
                Log.d(TAG, "Batch scan results: ${results.size}")
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "❌ Scan failed (errorCode=$errorCode)")
                _scanState.value = ScanState.Error("Scan failed ($errorCode)")
                when (errorCode) {
                    1 -> Log.e(TAG, "SCAN_FAILED_ALREADY_STARTED — Already scanning")
                    2 -> Log.e(TAG, "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED — BLE permission/system issue")
                    3 -> Log.e(TAG, "SCAN_FAILED_FEATURE_UNSUPPORTED — BLE scan not supported on this device")
                    4 -> Log.e(TAG, "SCAN_FAILED_INTERNAL_ERROR — System BLE stack error (Reboot required)")
                    5 -> Log.e(TAG, "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES — BLE buffer shortage")
                }
                stopScan()
            }
        }

        // 스캔 시작
        scanCallback = callback
        scanner.startScan(null, settings, callback)
        _scanState.value = ScanState.Scanning
        Log.i(TAG, "✅ BLE scan started")

        // �� 10초 후 자동 중지
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(10_000.milliseconds)
            if (_scanState.value == ScanState.Scanning) {
                stopScan()
                if (_devices.value.isEmpty()) {
                    _scanState.value = ScanState.Error("No devices found in 10 seconds.")
                    Log.w(TAG, "⏰ BLE device not found for 10 seconds")
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
            Log.i(TAG, "�� BLE scan stopped")
        }
        scanCallback = null
        timeoutJob?.cancel()
        _scanState.value = ScanState.Idle
    }

    /** �� 기기 연결 (GATT 콜백 포함) */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun connectToDevice(device: BleDevice, connectionResult: (Boolean) -> Unit, connectionReady: () -> Unit, receiveCallback: (ByteArray) -> Unit) {
        val btDevice = adapter?.getRemoteDevice(device.address)
        if (btDevice == null) {
            Log.e(TAG, "❌ getRemoteDevice failed: ${device.address}")
            return
        }

        Log.d(TAG, "�� Attempting connection: ${device.name} (${device.address})")

        val gattCallback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "✅ GATT connected, starting service discovery")
                        this@BleDeviceManager.gatt = gatt
                        gatt.discoverServices()

                        viewModelScope.launch(Dispatchers.Main) {
                            connectionResult.invoke(true)
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.w(TAG, " GATT disconnected")
                        this@BleDeviceManager.gatt = null
                        writeChar = null

                        viewModelScope.launch(Dispatchers.Main) {
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

                Log.i(TAG, "✅ 서비스 및 캐릭터리스틱 찾기 성공")

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
                        Log.d(TAG, "최종 CCCD 구독 결과: $success")

                        if (success) {
                            delay(150.milliseconds)
                            // Status Request (Request All) moved to onMtuChanged
                            gatt.requestMtu(64) // MTU 요청
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                            readyForList.set(true)

                            connectionReady.invoke()
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

                Log.d(TAG_BLE_DATA_RX, "RX (${value.size}B): ${value.hex()}")
                receiveCallback.invoke(value)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "💾 Characteristic write success")
                } else {
                    Log.e(TAG, "❌ Characteristic write failed: status=$status")
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                Log.d(TAG, "onMtuChanged: mtu=$mtu, status=$status")
            }
        }

        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            btDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M)
        } else {
            @Suppress("DEPRECATION")
            btDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
            Log.i(TAG, "�� GATT disconnected")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during disconnect: ${e.message}")
        } finally {
            gatt = null
            writeChar = null
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun write(bytes: ByteArray): Boolean {
        Log.d(TAG_BLE_DATA_TX, "TX (${bytes.size}B): ${bytes.hex()}")

        val g = gatt ?: run {
            Toast.makeText(context, R.string.toast_ble_not_connected, Toast.LENGTH_SHORT).show()
            return false
        }
        val ch = writeChar ?: run {
            Toast.makeText(context, R.string.toast_server_not_ready_yet, Toast.LENGTH_SHORT).show()
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
        Log.d(TAG, "�� write: ${bytes.joinToString(" ") { "%02X".format(it) }} (ok=$ok)")

        if (!ok) {
            Log.w(TAG, "⚠ writeCharacteristic() false - GATT busy or error")
        }

        return ok
    }

    fun parseCmd(cmd: ByteArray, listenerMode: (ModeType) -> Unit, listenerEqMode: (EqModeType) -> Unit, listenerGetEqMode: (EqModeType) -> Unit) {
        parseCmdDatas.addAll(cmd.toList())

        //* App → BT {0xec, 0xb0, 0x05, 0x5e}, BT → App Return {0xed, 0xb0, 0x05, 0x5d}
        while(parseCmdDatas.size >= 4) {
            val extracted = parseCmdDatas.subList(0, 4).toByteArray()
            parseCmdDatas.subList(0, 4).clear()

            Log.d(TAG, "parseCmd. extracted: ${extracted.hex()}")

            if (extracted.contentEquals(cmdPlayModeRx)) {
                Log.d(TAG, "parseCmd. ModeType.PLAY_MODE")
                listenerMode(ModeType.PLAY_MODE)
            } else if (extracted.contentEquals(cmdMeasurementModeRx)) {
                Log.d(TAG, "parseCmd. ModeType.MEASUREMENT_MODE")
                listenerMode(ModeType.MEASUREMENT_MODE)
            } else if (extracted.contentEquals(cmdEqModeDefaultRx)) {
                Log.d(TAG, "parseCmd. EqModeType.EQ_MODE_DEFAULT")
                listenerEqMode(EqModeType.EQ_MODE_DEFAULT)
            } else if (extracted.contentEquals(cmdEqModeUser1Rx)) {
                Log.d(TAG, "parseCmd. EqModeType.EQ_MODE_USER1")
                listenerEqMode(EqModeType.EQ_MODE_USER1)
            } else if (extracted.contentEquals(cmdEqModeUser2Rx)) {
                Log.d(TAG, "parseCmd. EqModeType.EQ_MODE_USER2")
                listenerEqMode(EqModeType.EQ_MODE_USER2)
            } else {
                if(extracted[0] == cmdGetEqModeRx[0] && extracted[1] == cmdGetEqModeRx[1] && extracted[3] == cmdGetEqModeRx[3]) {
                    val eqMode = extracted[2] - cmdGetEqModeRx[2]
                    Log.d(TAG, "parseCmd. getEQMode. received:$eqMode eqMode:$eqMode")

                    when (eqMode) {
                        0 -> listenerGetEqMode(EqModeType.EQ_MODE_DEFAULT)
                        1 -> listenerGetEqMode(EqModeType.EQ_MODE_USER1)
                        2 -> listenerGetEqMode(EqModeType.EQ_MODE_USER2)
                    }
                } else {
                    Toast.makeText(context, "UNKNOWN: ${extracted.hex()}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Bluetooth control(BLE)을 통하여 4byte 제어 데이터를 전송하고 4byte handshaking 데이터를 수신
    suspend fun modeSetting(modeType: ModeType) {
        Log.d(TAG, "modeSetting. $modeType")

        when(modeType) {
            ModeType.PLAY_MODE -> {
                write(cmdPlayModeTx)
            }
            ModeType.MEASUREMENT_MODE -> {
                write(cmdMeasurementModeTx)
            }
        }
    }

    // Bluetooth control(BLE)을 통하여 4byte 제어 데이터를 전송하고 4byte handshaking 데이터를 수신
    suspend fun setEQMode(eqModeType: EqModeType) {
        Log.d(TAG, "setEQMode. $eqModeType")

        when(eqModeType) {
            EqModeType.EQ_MODE_DEFAULT -> {
                write(cmdEqModeDefaultTx)
            }
            EqModeType.EQ_MODE_USER1 -> {
                write(cmdEqModeUser1Tx)
            }
            EqModeType.EQ_MODE_USER2 -> {
                write(cmdEqModeUser2Tx)
            }
        }
    }

    // Bluetooth control(BLE)을 통하여 4byte 제어 데이터를 전송하고 4byte 회신을 수신
    suspend fun getEQMode() {
        write(cmdGetEqModeTx)
    }
}
