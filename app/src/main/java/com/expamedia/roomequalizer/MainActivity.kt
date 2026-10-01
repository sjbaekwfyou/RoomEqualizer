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
    private lateinit var binding: ActivityMainBinding
    private lateinit var btManager: BluetoothDeviceManager
    private var receiveJob: Job? = null // 데이터 수신 루프 관리용 코루틴 Job
    private lateinit var popupLayout2: PopupLayout2

    private lateinit var nativeEqulizer: NativeEqualizer
    private lateinit var chirp: DoubleArray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nativeEqulizer = NativeEqualizer()

        chirp = DoubleArray(nativeEqulizer.ESS_LENGTH) { 0.0 }
        nativeEqulizer.GenerateESS(chirp)

        popupLayout2 = PopupLayout2(this, nativeEqulizer)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        btManager = BluetoothDeviceManager(this)

        // 권한 확인 및 요청
        checkPermissionsAudio()

        binding.btnBLEConnection.setOnClickListener {
            checkPermissionsBluetooth()
        }

        binding.btnPlayMode.setOnClickListener {
            // 실행할 로직 작성
        }

        binding.radioGroupEqSelection.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioEQDefault -> { /* 옵션 1 선택 처리 */ }
                R.id.radioEQUser1 -> { /* 옵션 2 선택 처리 */ }
                R.id.radioEQUser2 -> { /* 옵션 3 선택 처리 */ }
            }
        }

        binding.btnMeasurementMode.setOnClickListener {
            popupLayout2.showMessage1(
                listenerOK = {
                    showPopupLayout1()
                },
                listenerNG = {
                    Handler(Looper.getMainLooper()).post {
                        popupLayout2.showMessage4 {}
                    }
                }
            )
        }

        binding.btnExit.setOnClickListener {
            finishAffinity()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        btManager.disconnectAudioDevice()
        btManager.disconnectGeneralDevice()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // 1001번 요청 코드 확인
        if (requestCode == 1001) {
            var isAllGranted = true

            // 요청한 모든 권한이 승인되었는지 확인
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    isAllGranted = false
                    break
                }
            }

            if (isAllGranted) {
                showPopupLayoutBluetooth()
            } else {
                // 권한이 거부되었을 때 예외 처리
                Toast.makeText(this, "블루투스 권한이 거부되어 기기를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
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
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
        }
    }

    private fun checkPermissionsBluetooth() {
        // 필수 권한 요청 (Android 12 대응)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                1001
            )
        } else {
            showPopupLayoutBluetooth()
        }
    }

    private fun showPopupLayoutBluetooth() {
        var pairedDevices: List<BluetoothDeviceInfo> = btManager.getNonAudioPairedDevices()

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

        // 2. 팝업 다이얼로그 표시
        PopupLayoutBluetooth(this, pairedDevices) { selectedDevice ->
            // 사용자가 장비를 선택했을 때 실행되는 콜백
            val deviceName = selectedDevice.name ?: "알 수 없는 기기"
            Toast.makeText(this, "$deviceName 연결 시도 중...", Toast.LENGTH_SHORT).show()

            /*btManager.connectAudioDevice(selectedDevice) { isSuccess ->
                if (isSuccess) {
                    Toast.makeText(this, "오디오 경로가 블루투스로 설정되었습니다.", Toast.LENGTH_SHORT).show()
                    // Record / Playback 동작 실행
                    audioDeviceManager.initAudioDevices()
                } else {
                    Toast.makeText(this, "오디오 경로 설정 실패", Toast.LENGTH_SHORT).show()
                }
            }*/
            lifecycleScope.launch {
                Toast.makeText(this@MainActivity, "소켓 연결 시도 중...", Toast.LENGTH_SHORT).show()

                val isConnected = btManager.connectGeneralDevice(selectedDevice)
                if (isConnected) {
                    // 2. 입출력 스트림 바인딩
                    btManager.setupStreams()
                    Toast.makeText(this@MainActivity, "연결 성공! 데이터 수신 대기 중...", Toast.LENGTH_SHORT).show()

                    // 3. 백그라운드 데이터 수신 루프 실행
                    receiveJob?.cancel() // 기존 수신 작업이 있다면 취소
                    receiveJob = lifecycleScope.launch {
                        btManager.startListening { receivedData ->
                            // 장치로부터 데이터 수신 시 호출됨 (UI 스레드)
                            Toast.makeText(this@MainActivity, "\n[수신]: $receivedData", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this@MainActivity, "소켓 연결 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }.show()
    }

    private fun startMeasurement() {
        popupLayout2.showMessage2(chirp) { result ->
            Handler(Looper.getMainLooper()).post {
                var isCanceled = false
                var IIRcoef = DoubleArray(chirp.size) { 0.0 }

                popupLayout2.showMessage3(
                    chirp = chirp,
                    result = result,
                    IIRcoef = IIRcoef,
                    listenerCanceled = {
                        isCanceled = true
                    },
                    listener = { value ->
                        if (isCanceled) return@showMessage3

                        when (value) {
                            0 -> showPopupLayout3(IIRcoef)
                            1, 2, 3 -> {
                                Handler(Looper.getMainLooper()).post {
                                    popupLayout2.showMessage5 {
                                        Handler(Looper.getMainLooper()).post {
                                            startMeasurement()
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private fun showPopupLayout1() {
        val popupLayout1 = PopupLayout1(this)

        popupLayout1.show {
            startMeasurement()
        }
    }

    private fun showPopupLayout3(IIRcoef: DoubleArray) {
        val popupLayout3 = PopupLayout3(this)

        popupLayout3.setOnConfirmListener { eqUser ->
            when(eqUser) {
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
        popupLayout3.show()
    }
}