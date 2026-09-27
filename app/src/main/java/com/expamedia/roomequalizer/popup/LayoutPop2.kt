package com.expamedia.roomequalizer.popup

import android.util.Log
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.lifecycle.LifecycleOwner
import com.expamedia.roomequalizer.AudioDeviceManager
import com.expamedia.roomequalizer.R
import com.expamedia.roomequalizer.databinding.LayoutPop2Binding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LayoutPop2(private val context: Context) {
    enum class ButtonType(val message: String) {
        NONE("NONE"),
        QUIT("QUIT"),
        OK("OK"),
        RESTART("RESTART");
    }

    enum class MessageType(
        @ColorRes val colorBackground: Int,
        @StringRes val tvMessage: Int,
        @StringRes val btMessage: Int,
        val btVisibility: Int,
        val buttonType: ButtonType
    ) {
        NONE(R.color.white, R.string.no_message, R.string.no_message, View.GONE, ButtonType.NONE),
        Message1(R.color.white, R.string.noti_message1, R.string.no_message, View.GONE, ButtonType.NONE),
        Message2(R.color.white, R.string.noti_message2, R.string.quit, View.VISIBLE, ButtonType.QUIT),
        Message3(R.color.white, R.string.noti_message3, R.string.quit, View.VISIBLE, ButtonType.QUIT),
        Message4(R.color.orange, R.string.noti_message4, R.string.ok, View.VISIBLE, ButtonType.OK),
        Message5(R.color.orange, R.string.noti_message5, R.string.restart, View.VISIBLE, ButtonType.RESTART),
        Message6(R.color.orange, R.string.noti_message6, R.string.restart, View.VISIBLE, ButtonType.RESTART),
        Message7(R.color.orange, R.string.noti_message7, R.string.restart, View.VISIBLE, ButtonType.RESTART);
    }

   private val binding: LayoutPop2Binding by lazy {
        LayoutPop2Binding.inflate(LayoutInflater.from(context))
    }

    private val dialog: AlertDialog by lazy {
        AlertDialog.Builder(context)
            .setView(binding.root)
            .create().apply {
                // 필요시 배경을 투명하게 만들어 둥근 모서리 적용 가능
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
    }

    private lateinit var audioDeviceManager: AudioDeviceManager

    // 콜백 함수 정의 (다이얼로그 안에서 발생하는 이벤트를 외부로 전달할 때 사용)
    private var onConfirmListener: ((Boolean) -> Unit)? = null

    private var messageType: MessageType = MessageType.NONE

    init {
        audioDeviceManager = AudioDeviceManager(context)
        setupListeners()
    }

    private fun setupListeners() {
        // 서브 레이아웃 내부의 닫기 버튼 이벤트 (ID가 btnClose인 경우)
        binding.btnButtonID.setOnClickListener {
            this.onConfirmListener?.invoke(false)
            dismiss()
        }
    }

    private fun setMessage(messageType: MessageType) {
        this.messageType = messageType

        binding.background.setBackgroundColor(context.getColor(this.messageType.colorBackground))
        binding.tvMessage.text = context.getString(this.messageType.tvMessage)
        binding.btnButtonID.visibility = this.messageType.btVisibility
        binding.btnButtonID.text = context.getString(this.messageType.btMessage)
    }

    private fun readRecordBuffer(floatBuffer: FloatArray, listener: (FloatArray) -> Unit) {
        (context as? LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) {
            // 24,000개 Float 데이터 재생 및 수신 버퍼 저장
            val recordedFloatBuffer: FloatArray = audioDeviceManager.playAndRecord(floatBuffer)

            // [Main Thread] 스레드 전환
            withContext(Dispatchers.Main) {
                // UI 변경 및 콜백 호출
                // 수신받은 recordedFloatBuffer (FloatArray)로 후속 EQ/FFT 연산 수행
                Log.d("RoomEqualizer", "수집된 Float 버퍼 크기: ${recordedFloatBuffer.size}")

                listener.invoke(recordedFloatBuffer)
            }
        }
    }

    /**
     * 다이얼로그 표시
     */
    fun show() {
        if (!dialog.isShowing) {
            dialog.show()

            audioDeviceManager.initAudioDevices()
            setMessage(MessageType.Message1)

            // 2. 비동기 측정 실행
            (context as? LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) {
                // 1. 48kHz, 72,000개 (-1.0f ~ +1.0f) Float 신호 데이터 생성
                val float24000Samples = FloatArray(24000) { index ->
                    // 1kHz Sine wave 생성 예시
                    Math.sin(2.0 * Math.PI * 1000.0 * index / 48000.0).toFloat()
                }

                readRecordBuffer(float24000Samples) { recordedFloatBuffer ->
                    Log.d("RoomEqualizer", "수집된 Float 버퍼 크기: ${recordedFloatBuffer.size}")
                    onConfirmListener?.invoke(true)
                    dismiss()
                }
            }
        }
    }

    /**
     * 다이얼로그 닫기
     */
    fun dismiss() {
        if (dialog.isShowing) {
            // 앱 종료 시 오디오 환경 원복
            audioDeviceManager.releaseAudioDevices()

            dialog.dismiss()
        }
    }

    /**
     * 외부에서 다이얼로그 이벤트 결과값을 전달받는 리스너 설정
     */
    fun setOnConfirmListener(listener: (Boolean) -> Unit) {
        this.onConfirmListener = listener
    }
}