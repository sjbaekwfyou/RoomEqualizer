package com.expamedia.roomequalizer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.expamedia.roomequalizer.data.BleDevice
import com.expamedia.roomequalizer.databinding.ActivityMainBinding
import com.expamedia.roomequalizer.device.BleDeviceManager
import com.expamedia.roomequalizer.native.NativeEqualizer
import com.expamedia.roomequalizer.device.AudioDeviceManager
import com.expamedia.roomequalizer.popup.PopupLayout1
import com.expamedia.roomequalizer.popup.PopupLayout2
import com.expamedia.roomequalizer.popup.PopupLayout3
import com.expamedia.roomequalizer.popup.PopupLayoutBluetooth
import com.expamedia.roomequalizer.popup.PopupLayoutEQBankStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val discoveredDeviceList = mutableListOf<BleDevice>()
    private lateinit var bleDeviceManager: BleDeviceManager
    private var selectedDevice: BleDevice? = null

    private lateinit var audioDeviceManager: AudioDeviceManager

    private lateinit var nativeEqualizer: NativeEqualizer
    private lateinit var chirp: FloatArray

    private lateinit var popupLayout1: PopupLayout1
    private lateinit var popupLayout2: PopupLayout2
    private lateinit var popupLayout3: PopupLayout3

    private var popupLayoutBluetooth: PopupLayoutBluetooth? = null

    private lateinit var popupLayoutEQBankStatus: PopupLayoutEQBankStatus

    private var receiveJob: Job? = null // 데이터 수신 루프 관리용 코루틴 Job

    object ToastUtil {
        private var toast: Toast? = null

        /**
         * 메세지를 즉시 덮어씌워 출력하는 싱글톤 Toast 메서드
         * @param context Context (내부에서 applicationContext로 변환하여 메모리 누수 방지)
         * @param message 표시할 텍스트 내용
         * @param duration Toast.LENGTH_SHORT 또는 Toast.LENGTH_LONG (기본값: LENGTH_SHORT)
         */
        fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
            // 기존에 출력 중인 Toast가 있다면 텍스트만 업데이트 후 표시
            toast?.cancel() // 진행 중인 토스트 즉시 취소

            // ApplicationContext를 사용하여 Activity 메모리 누수 방지
            val appContext = context.applicationContext

            toast = Toast.makeText(appContext, message, duration).apply {
                show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        checkPermissions()

        bleDeviceManager = BleDeviceManager(this)

        audioDeviceManager = AudioDeviceManager(this)

        chirp = FloatArray(NativeEqualizer.ESS_LENGTH)
        nativeEqualizer = NativeEqualizer(this, chirp)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
        binding.tvVersion.text = "version:" + versionName

        showBLEConnectivity(false)

        popupLayout1 = PopupLayout1(this)
        popupLayout2 = PopupLayout2(this, audioDeviceManager, nativeEqualizer)
        popupLayout3 = PopupLayout3(this)

        popupLayoutEQBankStatus = PopupLayoutEQBankStatus(this)

        binding.btnBLEScan.setOnClickListener {
            bleDeviceManager.disconnect()
            selectedDevice = null;

            showPopupLayoutBluetooth()
        }

        binding.btnBLEDisconnect.setOnClickListener {
            if (!bleDeviceManager.isConnected()) {
                ToastUtil.show(
                    this@MainActivity,
                    getString(R.string.toast_ble_not_connected)
                )
            } else {
                ToastUtil.show(
                    this@MainActivity,
                    getString(R.string.toast_ble_disconnected)
                )
            }

            bleDeviceManager.disconnect()
            selectedDevice = null;

            showBLEConnectivity(false)
        }

        binding.btnBLERefresh.setOnClickListener {
            if (!bleDeviceManager.isConnected()) {
                ToastUtil.show(
                    this@MainActivity,
                    getString(R.string.toast_ble_not_connected)
                )
            } else {
                ToastUtil.show(
                    this@MainActivity,
                    getString(R.string.toast_ble_refreshed)
                )
            }

            bleDeviceManager.disconnect()

            //selectedDevice = null;
            if (selectedDevice != null) {
                connectBLE()
            }

            showBLEConnectivity(false)
        }

        binding.btnPlayMode.setOnClickListener {
            lifecycleScope.launch {
                bleDeviceManager.modeSetting(BleDeviceManager.ModeType.PLAY_MODE)
            }
        }

        binding.radioEQDefault.isChecked = true
        binding.radioGroupEqSelection.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioEQDefault -> {
                    lifecycleScope.launch {
                        bleDeviceManager.setEQMode(BleDeviceManager.EqModeType.EQ_MODE_DEFAULT)
                    }
                }
                R.id.radioEQUser1 -> {
                    lifecycleScope.launch {
                        bleDeviceManager.setEQMode(BleDeviceManager.EqModeType.EQ_MODE_USER1)
                    }
                }
                R.id.radioEQUser2 -> {
                    lifecycleScope.launch {
                        bleDeviceManager.setEQMode(BleDeviceManager.EqModeType.EQ_MODE_USER2)
                    }
                }
            }
        }

        binding.btnMeasurementMode.setOnClickListener {
            if (!bleDeviceManager.isConnected()) {
                ToastUtil.show(
                    this@MainActivity,
                    getString(R.string.toast_ble_not_connected)
                )
//                return@setOnClickListener
            }

            lifecycleScope.launch {
                bleDeviceManager.modeSetting(BleDeviceManager.ModeType.MEASUREMENT_MODE)
            }
            showPopupLayoutMeasurement()
        }

        binding.btnExit.setOnClickListener {
            finishAffinity()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        bleDeviceManager.disconnect()
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

    /*
     * 최초 BLE 상태
     * 최초 BLE 연결 및 measurement 완료 후 호출
     */
    private suspend fun firstInitBLE() {
        bleDeviceManager.getEQBankStatus()
        bleDeviceManager.modeSetting(BleDeviceManager.ModeType.PLAY_MODE)
        bleDeviceManager.getEQMode()
    }

    private fun showBLEConnectivity(connected: Boolean) {
        var resId = R.drawable.bg_button_not_connected
        var bleName = ""

        if (connected) {
            resId = R.drawable.bg_button_connected
            bleName = selectedDevice?.name ?: ""
        }

        binding.btnBLEDisconnect.isEnabled = connected
        binding.btnBLERefresh.isEnabled = connected
        binding.btnPlayMode.isEnabled = connected
        binding.radioEQDefault.isEnabled = connected
        binding.radioEQUser1.isEnabled = connected
        binding.radioEQUser2.isEnabled = connected
        binding.btnMeasurementMode.isEnabled = connected

        binding.btnBLEDisconnect.background = AppCompatResources.getDrawable(this, resId)
        binding.btnBLERefresh.background = AppCompatResources.getDrawable(this, resId)
        binding.btnPlayMode.background = AppCompatResources.getDrawable(this, resId)
        binding.btnMeasurementMode.background = AppCompatResources.getDrawable(this, resId)

        binding.textViewBLEName.text = bleName
    }

    private fun connectBLE() {
        val deviceName = selectedDevice?.name ?: getString(R.string.unknown_device)
        ToastUtil.show(
            this,
            "Connecting to $deviceName"
        )

        receiveJob?.cancel() // 기존 수신 작업이 있다면 취소
        receiveJob = lifecycleScope.launch(Dispatchers.IO) {
            selectedDevice?.let { device ->
                bleDeviceManager.connectToDevice(device, connectionResult = { isConnected ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (!isConnected) {
                            showBLEConnectivity(false)

                            ToastUtil.show(
                                this@MainActivity,
                                getString(R.string.alert_bluetooth_connection_failed)
                            )

                            return@launch
                        }

                        ToastUtil.show(
                            this@MainActivity,
                            getString(R.string.toast_bluetooth_connected)
                        )
                    }
                }, connectionReady = {
                    lifecycleScope.launch(Dispatchers.Main) {
                        showBLEConnectivity(true)
                        firstInitBLE()

                        ToastUtil.show(
                            this@MainActivity,
                            getString(R.string.toast_bluetooth_service_ready)
                        )
                    }
                }, receiveCallback = { receivedData ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        //ToastUtil.show(this@MainActivity, "\n[수신]: ${receivedData.size}")
                        bleDeviceManager.parseCmd(receivedData,
                            listenerMode = { modeType ->
                                ToastUtil.show(
                                    this@MainActivity,
                                    "Mode set to $modeType"
                                )
                            }, listenerEqMode = { eqModeType ->
                                ToastUtil.show(
                                    this@MainActivity,
                                    "EqMode set to $eqModeType"
                                )
                            }, listenerGetEqMode = { eqModeType ->
                                when (eqModeType) {
                                    BleDeviceManager.EqModeType.EQ_MODE_DEFAULT -> binding.radioEQDefault.isChecked = true
                                    BleDeviceManager.EqModeType.EQ_MODE_USER1 -> binding.radioEQUser1.isChecked = true
                                    BleDeviceManager.EqModeType.EQ_MODE_USER2 -> binding.radioEQUser2.isChecked = true
                                }
                            })
                    }
                })
            }
        }
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

            bleDeviceManager.startScan { device ->
                // [콜백 1] 새로운 기기를 발견할 때마다 매번 호출됨
                val deviceName = device.name ?: getString(R.string.unknown_device)
                val deviceAddress = device.address

                // 중복 추가 방지
                if (discoveredDeviceList.none { it.address == deviceAddress }) {
                    val deviceInfo = BleDevice(
                        name = deviceName,
                        address = deviceAddress
                    )
                    discoveredDeviceList.add(deviceInfo)

                    runOnUiThread {
                        popupLayoutBluetooth?.addDevice(deviceInfo)
                    }
                }
            }
        }

        popupLayoutBluetooth = PopupLayoutBluetooth(this, getString(R.string.title_ble_devices))
        popupLayoutBluetooth?.show(emptyList()) { device ->
            selectedDevice = device;
            connectBLE()
        }
    }

    private fun showPopupLayoutMeasurement() {
        popupLayout2.showMessage1(
            listenerOK = {
                popupLayout1.show(
                    listenerStart = {
                        startMeasurement()
                        false
                    },
                    listenerCancel = {
                        lifecycleScope.launch {
                            firstInitBLE()
                        }
                        false
                    }
                )
                false
            },
            listenerNG = {
                popupLayout2.showMessage4 {
                    lifecycleScope.launch {
                        firstInitBLE()
                    }
                    false
                }
                true
            }
        )
    }

    private fun startMeasurement() {
        fun completeCalculatePEQ(
            IIRcoef: FloatArray,
            value: Int,
            dbValue: Int
        ): Boolean {
            when (value) {
                0 -> {
                    popupLayout3.show { eqUser ->
                        fun checkEQBankStatusAndSaveEQMode(eqModeType: BleDeviceManager.EqModeType, IIRcoef: FloatArray): Boolean {
                            if (bleDeviceManager.eqBankStatus(eqModeType)) {
                                popupLayoutEQBankStatus.show(eqModeType,
                                    listener = {
                                        lifecycleScope.launch {
                                            bleDeviceManager.saveEQMode(eqModeType, IIRcoef)
                                            firstInitBLE()
                                        }

                                        popupLayout3.dismiss()
                                        return@show false
                                    }, listenerCancel =  {
                                        return@show false
                                    })
                                return true
                            }

                            lifecycleScope.launch {
                                bleDeviceManager.saveEQMode(eqModeType, IIRcoef)
                                firstInitBLE()
                            }
                            return false
                        }

                        when (eqUser) {
                            PopupLayout3.EqUser.Cancel -> {
                                ToastUtil.show(
                                    this,
                                    "CANCEL"
                                )
                            }
                            PopupLayout3.EqUser.User1 -> {
                                return@show checkEQBankStatusAndSaveEQMode(BleDeviceManager.EqModeType.EQ_MODE_USER1, IIRcoef)
                                //binding.radioEQUser1.isChecked = true
                            }
                            PopupLayout3.EqUser.User2 -> {
                                return@show checkEQBankStatusAndSaveEQMode(BleDeviceManager.EqModeType.EQ_MODE_USER2, IIRcoef)
                                //binding.radioEQUser2.isChecked = true
                            }
                        }

                        lifecycleScope.launch {
                            firstInitBLE()
                        }
                        false
                    }
                }
                1 -> {
                    popupLayout2.showMessage5 {
                        startMeasurement()
                        true
                    }
                    return true
                }
                2 -> {
                    popupLayout2.showMessage6(dbValue) {
                        startMeasurement()
                        true
                    }
                    return true
                }
                3 -> {
                    popupLayout2.showMessage7(dbValue) {
                        startMeasurement()
                        true
                    }
                    return true
                }
            }
            return false
        }

        popupLayout2.showMessage2(
            chirp,
            listenerQuit = {
                lifecycleScope.launch {
                    firstInitBLE()
                }

                return@showMessage2 false
            },
            listener = { result ->
                var isCanceled = false
                popupLayout2.showMessage3(
                    chirp, result,
                    listenerCanceled = {
                        isCanceled = true

                        lifecycleScope.launch {
                            firstInitBLE()
                        }

                        return@showMessage3 false
                    },
                    listener = { value, dbValue, IIRcoef ->
                        if (isCanceled) return@showMessage3 false

                        return@showMessage3 completeCalculatePEQ(IIRcoef, value, dbValue)
                    }
                )

                return@showMessage2 true
            }
        )
    }
}