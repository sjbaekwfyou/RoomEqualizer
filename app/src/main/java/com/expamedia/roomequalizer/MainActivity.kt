package com.expamedia.roomequalizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.expamedia.roomequalizer.data.BluetoothDeviceInfo
import com.expamedia.roomequalizer.databinding.ActivityMainBinding
import com.expamedia.roomequalizer.device.BluetoothDeviceManager
import com.expamedia.roomequalizer.native.NativeEqualizer
import com.expamedia.roomequalizer.popup.PopupLayout1
import com.expamedia.roomequalizer.popup.PopupLayout2
import com.expamedia.roomequalizer.popup.PopupLayout3
import com.expamedia.roomequalizer.popup.PopupLayoutBluetooth
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private final val resultCodeAudio: Int = 1001
    private final val resultCodeBluetooth: Int = 1002

    private lateinit var binding: ActivityMainBinding
    private lateinit var bluetoothDeviceManager: BluetoothDeviceManager
    private lateinit var audioDeviceManager: AudioDeviceManager

    private lateinit var nativeEqualizer: NativeEqualizer
    private lateinit var chirp: DoubleArray

    private lateinit var popupLayout1: PopupLayout1
    private lateinit var popupLayout2: PopupLayout2
    private lateinit var popupLayout3: PopupLayout3

    private var receiveJob: Job? = null // 데이터 수신 루프 관리용 코루틴 Job

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        checkPermissionsAudio()

        bluetoothDeviceManager = BluetoothDeviceManager(this)

        audioDeviceManager = AudioDeviceManager(this)
        audioDeviceManager.initAudioDevices()

        nativeEqualizer = NativeEqualizer()
        chirp = DoubleArray(nativeEqualizer.getEssLength())
        nativeEqualizer.generateESS(chirp)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        popupLayout1 = PopupLayout1(this)
        popupLayout2 = PopupLayout2(this, audioDeviceManager, nativeEqualizer)
        popupLayout3 = PopupLayout3(this)

        binding.btnBLEConnection.setOnClickListener {
            checkPermissionsBluetooth(resultCodeBluetooth)
        }

        binding.btnPlayMode.setOnClickListener {
            // 실행할 로직 작성
        }

        binding.radioEQDefault.isChecked = true
        binding.radioGroupEqSelection.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioEQDefault -> {
                    Toast.makeText(this, "Default", Toast.LENGTH_SHORT).show()
                }
                R.id.radioEQUser1 -> {
                    Toast.makeText(this, "User1", Toast.LENGTH_SHORT).show()
                }
                R.id.radioEQUser2 -> {
                    Toast.makeText(this, "User2", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnMeasurementMode.setOnClickListener {
            checkPermissionsBluetooth(resultCodeAudio)
        }

        binding.btnExit.setOnClickListener {
            finishAffinity()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        bluetoothDeviceManager.disconnectAudioDevice()
        bluetoothDeviceManager.disconnectGeneralDevice()
        audioDeviceManager.releaseAudioDevices()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == resultCodeAudio || requestCode == resultCodeBluetooth) {
            var isAllGranted = true

            // 요청한 모든 권한이 승인되었는지 확인
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    isAllGranted = false
                    break
                }
            }

            if (isAllGranted) {
                if(requestCode == resultCodeAudio) {
                    showPopupLayoutAudio()
                }
                else {
                    showPopupLayoutBluetooth()
                }
            } else {
                // 권한이 거부되었을 때 예외 처리
                Toast.makeText(this, "Unable to find devices because Bluetooth permission was denied.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAudio() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val hasAllPermissions = permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasAllPermissions) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), resultCodeBluetooth)
        }
    }

    private fun checkPermissionsBluetooth(resultCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                resultCode
            )
        } else {
            showPopupLayoutBluetooth()
        }
    }

    private fun showPopupLayoutAudio() {
        popupLayout2.showMessage1(
            listenerOK = {
                popupLayout1.show {
                    startMeasurement()
                }
            },
            listenerNG = {
                Handler(Looper.getMainLooper()).post {
                    popupLayout2.showMessage4 {}
                }
            }
        )

        /*val pairedDevices: List<BluetoothDeviceInfo> = bluetoothDeviceManager.getAudioPairedDevices()
        if (pairedDevices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_no_audio))
                .setMessage(getString(R.string.alert_no_audio))
                .setPositiveButton(getString(R.string.button_no_bluetooth)) { dialog, _ ->
                    // 안드로이드 블루투스 설정 화면으로 이동
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    startActivity(intent)
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(true)
                .show()
            return
        }

        PopupLayoutBluetooth(this, "Speaker Devices", pairedDevices) { selectedDevice ->
            val deviceName = selectedDevice.name ?: "알 수 없는 기기"
            //Toast.makeText(this, "Connecting to $deviceName", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                val isConnected = bluetoothDeviceManager.connectGeneralDevice(selectedDevice)
                if (isConnected) {
                    //Toast.makeText(this@MainActivity, "Connected.", Toast.LENGTH_SHORT).show()

                    popupLayout2.showMessage1(
                        listenerOK = {
                            popupLayout1.show {
                                startMeasurement()
                            }
                        },
                        listenerNG = {
                            Handler(Looper.getMainLooper()).post {
                                popupLayout2.showMessage4 {}
                            }
                        }
                    )
                } else {
                    Toast.makeText(this@MainActivity, "Socket connection failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }.show()*/
    }

    private fun showPopupLayoutBluetooth() {
        val pairedDevices: List<BluetoothDeviceInfo> = bluetoothDeviceManager.getNonAudioPairedDevices()
        if (pairedDevices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_no_bluetooth))
                .setMessage(getString(R.string.alert_no_bluetooth))
                .setPositiveButton(getString(R.string.button_no_bluetooth)) { dialog, _ ->
                    // 안드로이드 블루투스 설정 화면으로 이동
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    startActivity(intent)
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(true)
                .show()
            return
        }

        PopupLayoutBluetooth(this, "BLE Devices", pairedDevices) { selectedDevice ->
            val deviceName = selectedDevice.name ?: "알 수 없는 기기"
            Toast.makeText(this, "Connecting to $deviceName", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                val isConnected = bluetoothDeviceManager.connectGeneralDevice(selectedDevice)
                if (isConnected) {
                    bluetoothDeviceManager.setupStreams()
                    Toast.makeText(this@MainActivity, "Connected! Awaiting data stream...", Toast.LENGTH_SHORT).show()

                    receiveJob?.cancel() // 기존 수신 작업이 있다면 취소
                    receiveJob = lifecycleScope.launch {
                        bluetoothDeviceManager.startListening { receivedData ->
                            Toast.makeText(this@MainActivity, "\n[수신]: $receivedData", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.fail))
                        .setMessage(getString(R.string.alert_bluetooth_connection_failed))
                        .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setCancelable(true)
                        .show()
                }
            }
        }.show()
    }

    private fun startMeasurement() {
        fun completeCalculatePEQ(chirp: DoubleArray, result: DoubleArray, IIRcoef: DoubleArray, value: Int) {
            Handler(Looper.getMainLooper()).post {
                when (value) {
                    0 -> {
                        popupLayout3.show { eqUser ->
                            when (eqUser) {
                                PopupLayout3.EqUser.Cancel -> {
                                    Toast.makeText(this, "CANCEL", Toast.LENGTH_SHORT).show()
                                }

                                PopupLayout3.EqUser.User1 -> {
                                    Toast.makeText(this, "User1", Toast.LENGTH_SHORT).show()
                                }

                                PopupLayout3.EqUser.User2 -> {
                                    Toast.makeText(this, "User2", Toast.LENGTH_SHORT).show()
                                }
                            }
                            popupLayout3.dismiss()
                        }
                    }
                    1 -> {
                        popupLayout2.showMessage5 {
                            Handler(Looper.getMainLooper()).post {
                                startMeasurement()
                            }
                        }
                    }
                    2 -> {
                        popupLayout2.showMessage6 {
                            Handler(Looper.getMainLooper()).post {
                                startMeasurement()
                            }
                        }
                    }
                    3 -> {
                        popupLayout2.showMessage7 {
                            Handler(Looper.getMainLooper()).post {
                                startMeasurement()
                            }
                        }
                    }
                }
            }
        }

        fun calculatePEQ(result: DoubleArray) {
            var isCanceled = false
            popupLayout2.showMessage3(chirp, result,
                listenerCanceled = {
                    isCanceled = true
                },
                listener = { value, IIRcoef ->
                    if (isCanceled) return@showMessage3

                    completeCalculatePEQ(chirp, result, IIRcoef, value)
                }
            )
        }

        popupLayout2.showMessage2(chirp) { result ->
            Handler(Looper.getMainLooper()).post {
                calculatePEQ(result)
            }
        }
    }
}