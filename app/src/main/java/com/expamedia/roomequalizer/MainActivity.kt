package com.expamedia.roomequalizer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.expamedia.roomequalizer.api.BleApi
import com.expamedia.roomequalizer.data.BluetoothDeviceInfo
import com.expamedia.roomequalizer.databinding.ActivityMainBinding
import com.expamedia.roomequalizer.device.BluetoothDeviceManager
import com.expamedia.roomequalizer.api.NativeEqualizer
import com.expamedia.roomequalizer.popup.PopupLayout1
import com.expamedia.roomequalizer.popup.PopupLayout2
import com.expamedia.roomequalizer.popup.PopupLayout3
import com.expamedia.roomequalizer.popup.PopupLayoutBluetooth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var discoveryReceiver: BroadcastReceiver? = null
    private val discoveredDeviceList = mutableListOf<BluetoothDeviceInfo>()
    private lateinit var bluetoothDeviceManager: BluetoothDeviceManager
    private var bleApi: BleApi? = null

    private lateinit var audioDeviceManager: AudioDeviceManager

    private lateinit var nativeEqualizer: NativeEqualizer
    private lateinit var chirp: FloatArray

    private lateinit var popupLayout1: PopupLayout1
    private lateinit var popupLayout2: PopupLayout2
    private lateinit var popupLayout3: PopupLayout3

    private var popupLayoutBluetooth: PopupLayoutBluetooth? = null

    private var receiveJob: Job? = null // 데이터 수신 루프 관리용 코루틴 Job

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        checkPermissions()

        bluetoothDeviceManager = BluetoothDeviceManager(this)

        audioDeviceManager = AudioDeviceManager(this)
        audioDeviceManager.registerAudioDeviceCallback()

        chirp = FloatArray(NativeEqualizer.ESS_LENGTH)
        nativeEqualizer = NativeEqualizer(this, chirp)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        popupLayout1 = PopupLayout1(this)
        popupLayout2 = PopupLayout2(this, audioDeviceManager, nativeEqualizer)
        popupLayout3 = PopupLayout3(this)

        binding.btnBLEConnection.setOnClickListener {
            showPopupLayoutBluetooth()
        }

        binding.btnPlayMode.setOnClickListener {
            lifecycleScope.launch {
                Toast.makeText(this@MainActivity, getString(R.string.play_mode), Toast.LENGTH_SHORT).show()
                bleApi?.modeSetting(BleApi.ModeType.PLAY_MODE)
            }
        }

        binding.radioEQDefault.isChecked = true
        binding.radioGroupEqSelection.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioEQDefault -> {
                    lifecycleScope.launch {
                        bleApi?.setEQMode(BleApi.EqModeType.EQ_MODE_DEFAULT)
                    }
                }
                R.id.radioEQUser1 -> {
                    lifecycleScope.launch {
                        bleApi?.setEQMode(BleApi.EqModeType.EQ_MODE_USER1)
                    }
                }
                R.id.radioEQUser2 -> {
                    lifecycleScope.launch {
                        bleApi?.setEQMode(BleApi.EqModeType.EQ_MODE_USER2)
                    }
                }
            }
        }

        binding.btnMeasurementMode.setOnClickListener {
            lifecycleScope.launch {
                bleApi?.modeSetting(BleApi.ModeType.MEASUREMENT_MODE)
                showPopupLayoutAudio()
            }
        }

        binding.btnExit.setOnClickListener {
            finishAffinity()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        discoveryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // 이미 해제되었거나 등록되지 않은 경우의 예외 처리
                e.printStackTrace()
            }
            discoveryReceiver = null
        }

        bluetoothDeviceManager.disconnectGeneralDevice()
        audioDeviceManager.releaseAudioDevices()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        val hasAllPermissions = permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasAllPermissions) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
        }
    }

    private fun showPopupLayoutAudio() {
        popupLayout2.showMessage1(
            listenerOK = {
                popupLayout1.show {
                    startMeasurement()
                }
                false
            },
            listenerNG = {
                popupLayout2.showMessage4 {
                    false
                }
                true
            }
        )
    }

    private fun showPopupLayoutBluetooth() {
        popupLayoutBluetooth?.dismiss()

        val isScanPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 이상: BLUETOOTH_SCAN 권한 검사
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 11 이하: ACCESS_FINE_LOCATION 위치 권한 검사
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (isScanPermissionGranted) {
            discoveredDeviceList.clear()

            bluetoothDeviceManager.startDiscovery(
                onDeviceFound = { device ->
                    // [콜백 1] 새로운 기기를 발견할 때마다 매번 호출됨
                    val deviceName = device.name ?: getString(R.string.unknown_device)
                    val deviceAddress = device.address

                    // 중복 추가 방지
                    if (discoveredDeviceList.none { it.address == deviceAddress }) {
                        val deviceInfo = BluetoothDeviceInfo(
                            name = deviceName,
                            address = deviceAddress,
                            originalDevice = device
                        )
                        discoveredDeviceList.add(deviceInfo)

                        runOnUiThread {
                            popupLayoutBluetooth?.addDevice(deviceInfo)
                        }
                    }
                },
                receiverOut = { receiver ->
                    discoveryReceiver = receiver
                }
            )
        }

        popupLayoutBluetooth = PopupLayoutBluetooth(this, getString(R.string.ble_devices))

        val pairedDevices: List<BluetoothDeviceInfo> = bluetoothDeviceManager.getPairedBluetoothDevices()
        popupLayoutBluetooth?.show(pairedDevices) { selectedDevice ->
            val deviceName = selectedDevice.name ?: getString(R.string.unknown_device)
            Toast.makeText(this, "Connecting to $deviceName", Toast.LENGTH_SHORT).show()

            receiveJob?.cancel() // 기존 수신 작업이 있다면 취소
            receiveJob = lifecycleScope.launch(Dispatchers.IO) {
                val isConnected = bluetoothDeviceManager.connectGeneralDevice(selectedDevice)
                if (!isConnected) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(getString(R.string.fail))
                            .setMessage(getString(R.string.alert_bluetooth_connection_failed))
                            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                                dialog.dismiss()
                            }
                            .setCancelable(true)
                            .show()
                    }
                    return@launch
                }

                Toast.makeText(this@MainActivity, getString(R.string.toast_bluetooth_connected), Toast.LENGTH_SHORT).show()

                bluetoothDeviceManager.setupStreams()

                bleApi = BleApi(this@MainActivity, bluetoothDeviceManager)
                bleApi?.getEQMode()

                bluetoothDeviceManager.startListening { receivedData ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        //Toast.makeText(this@MainActivity, "\n[수신]: ${receivedData.size}", Toast.LENGTH_SHORT).show()
                        bleApi?.parseCmd(receivedData, listenerMode = { modeType ->
                            Toast.makeText(this@MainActivity, "Mode set to $modeType", Toast.LENGTH_SHORT).show()
                        }, listenerEqMode = { eqModeType ->
                            Toast.makeText(this@MainActivity, "EqMode set to $eqModeType", Toast.LENGTH_SHORT).show()
                        }, listenerGetEqMode = { eqModeType ->
                            when(eqModeType) {
                                BleApi.EqModeType.EQ_MODE_DEFAULT -> binding.radioEQDefault.isChecked = true
                                BleApi.EqModeType.EQ_MODE_USER1 -> binding.radioEQUser1.isChecked = true
                                BleApi.EqModeType.EQ_MODE_USER2 -> binding.radioEQUser2.isChecked = true
                            }
                        })
                    }
                }
            }
        }
    }

    private fun startMeasurement() {
        fun completeCalculatePEQ(chirp: FloatArray, result: FloatArray, IIRcoef: FloatArray, value: Int): Boolean {
            when (value) {
                0 -> {
                    popupLayout3.show { eqUser ->
                        when (eqUser) {
                            PopupLayout3.EqUser.Cancel -> {
                                Toast.makeText(this, "CANCEL", Toast.LENGTH_SHORT).show()
                            }
                            PopupLayout3.EqUser.User1 -> {
                                binding.radioEQUser1.isChecked = true
                            }
                            PopupLayout3.EqUser.User2 -> {
                                binding.radioEQUser2.isChecked = true
                            }
                        }
                        false
                    }
                    return false
                }
                1 -> {
                    popupLayout2.showMessage5 {
                        startMeasurement()
                        true
                    }
                    return true
                }
                2 -> {
                    popupLayout2.showMessage6 {
                        startMeasurement()
                        true
                    }
                    return true
                }
                3 -> {
                    popupLayout2.showMessage7 {
                        startMeasurement()
                        true
                    }
                    return true
                }
            }
            return false
        }

        fun calculatePEQ(result: FloatArray) {
            var isCanceled = false
            popupLayout2.showMessage3(chirp, result,
                listenerCanceled = {
                    isCanceled = true
                },
                listener = { value, IIRcoef ->
                    if (isCanceled) return@showMessage3 false

                    return@showMessage3 completeCalculatePEQ(chirp, result, IIRcoef, value)
                }
            )
        }

        popupLayout2.showMessage2(chirp) { result ->
            calculatePEQ(result)
            return@showMessage2 true
        }
    }
}