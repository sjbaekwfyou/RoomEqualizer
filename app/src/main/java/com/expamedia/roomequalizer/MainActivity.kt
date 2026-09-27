package com.expamedia.roomequalizer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.expamedia.roomequalizer.databinding.ActivityMainBinding
import com.expamedia.roomequalizer.popup.LayoutPop1
import com.expamedia.roomequalizer.popup.LayoutPop2
import com.expamedia.roomequalizer.popup.LayoutPop3

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 권한 확인 및 요청
        checkAudioPermissions()

        binding.btnBLEConnection.setOnClickListener {
            Toast.makeText(this, "첫 번째 버튼이 클릭되었습니다!", Toast.LENGTH_SHORT).show()
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
            showLayoutPop1()
        }

        binding.btnExit.setOnClickListener {
            finishAffinity()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun checkAudioPermissions() {
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

    private fun showLayoutPop1() {
        val layoutPop1 = LayoutPop1(this)

        layoutPop1.setOnConfirmListener { message ->
            //Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            showLayoutPop2()
        }

        layoutPop1.show()
    }

    private fun showLayoutPop2() {
        val layoutPop2 = LayoutPop2(this)

        layoutPop2.setOnConfirmListener { success ->
            layoutPop2.dismiss()

            if (success) {
                showLayoutPop3()
            }
        }

        layoutPop2.show()
    }

    private fun showLayoutPop3() {
        val layoutPop3 = LayoutPop3(this)

        layoutPop3.setOnConfirmListener { eqUser ->
            when(eqUser) {
                LayoutPop3.EqUser.Cancel -> {
                    Toast.makeText(this, "CANCEL", Toast.LENGTH_SHORT).show()
                }
                LayoutPop3.EqUser.User1 -> {
                    Toast.makeText(this, "User1", Toast.LENGTH_SHORT).show()
                }
                LayoutPop3.EqUser.User2 -> {
                    Toast.makeText(this, "User2", Toast.LENGTH_SHORT).show()
                }
            }

            layoutPop3.dismiss()
        }
        layoutPop3.show()
    }

    /**
     * A native method that is implemented by the 'roomequalizer' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'roomequalizer' library on application startup.
        init {
            System.loadLibrary("roomequalizer")
        }
    }
}