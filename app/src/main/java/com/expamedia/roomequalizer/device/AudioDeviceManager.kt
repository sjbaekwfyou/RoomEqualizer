package com.expamedia.roomequalizer

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioDeviceManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        private const val TAG = "AudioDeviceManager"
        private const val SAMPLE_RATE = 48000 // 48kHz
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
    }

    fun setSpeakerphoneEnabled(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enable) {
                val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                speakerDevice?.let {
                    try {
                        audioManager.setCommunicationDevice(it)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to set speaker communication device: ${e.message}")
                    }
                }
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enable
        }
    }

    private fun initAudioDevices() {
        // 1. 일반 미디어 모드로 설정 (A2DP 자동 전환 기본 조건)
        audioManager.mode = AudioManager.MODE_NORMAL

        // 레거시 스피커폰 설정 해제
        @Suppress("DEPRECATION")
        if (audioManager.isSpeakerphoneOn) {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }

        // 2. Android 12 (API 31) 이상 대응
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // setCommunicationDevice는 availableCommunicationDevices에 포함된 통신 전용 장치만 수락합니다.
            // TYPE_BLUETOOTH_A2DP(8) 등 미디어 전용 출력 장치를 전달하면 IllegalArgumentException: invalid device type: 8 예외가 발생합니다.
            val commDevices = audioManager.availableCommunicationDevices

            // 연결된 블루투스 통신 장치(BLE 헤드셋/스피커, 블루투스 SCO 등) 확인
            val btCommDevice = commDevices.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }

            if (btCommDevice != null) {
                try {
                    val result = audioManager.setCommunicationDevice(btCommDevice)
                    Log.d("AudioRouting", "출력: 블루투스 통신 장치 (${btCommDevice.productName}), 성공: $result")
                } catch (e: Exception) {
                    Log.e("AudioRouting", "setCommunicationDevice 실패: ${e.message}")
                }
            } else {
                // 블루투스 통신 장치가 없으면 라우팅 해제 (기본 오디오/내장 스피커로 자동 출력)
                audioManager.clearCommunicationDevice()
                Log.d("AudioRouting", "출력: 내장 스피커/기본 오디오")
            }
        }
    }

    fun registerAudioDeviceCallback() {
        audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                // 새로운 오디오 장치(블루투스 스피커 등)가 연결되었을 때
                initAudioDevices()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                // 오디오 장치 연결이 해제되었을 때 (내장 스피커로 복구)
                initAudioDevices()
            }
        }, null)
    }

    fun releaseAudioDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        }
        setSpeakerphoneEnabled(false)
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    @SuppressLint("MissingPermission")
    suspend fun playAndRecord(outputFloatData: FloatArray): FloatArray = withContext(Dispatchers.IO) {
        val bytesPerSample = 4
        val minRecordBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
        val recordBufferSize = maxOf(minRecordBufferSize, outputFloatData.size * bytesPerSample)

        // 💡 1. VOICE_COMMUNICATION 대신 VOICE_RECOGNITION 또는 MIC 사용 (블루투스 마이크 자동 전환 방지)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_IN,
            AUDIO_FORMAT,
            recordBufferSize
        )

        // 💡 2. Android 6.0(API 23) 이상: 내장 마이크(TYPE_BUILTIN_MIC) 명시적 고정
        val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val builtinMic = inputDevices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
        }
        builtinMic?.let {
            val isSuccess = audioRecord.setPreferredDevice(it)
            Log.d(TAG, "내장 마이크 고정 설정 결과: $isSuccess")
        } ?: run {
            Log.w(TAG, "내장 마이크 장치를 찾지 못했습니다.")
        }

        val minTrackBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)

        // 💡 3. AudioTrack 속성도 USAGE_MEDIA (음악/측정용)로 맞추어 고음질 출력 유지
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minTrackBufferSize, outputFloatData.size * bytesPerSample))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(outputFloatData, 0, outputFloatData.size, AudioTrack.WRITE_BLOCKING)

        val recordedFloatBuffer = FloatArray(outputFloatData.size)
        var totalReadSamples = 0

        try {
            audioRecord.startRecording()
            audioTrack.play()

            val tempReadBuffer = FloatArray(1024)

            while (totalReadSamples < outputFloatData.size) {
                val readSize = audioRecord.read(
                    tempReadBuffer,
                    0,
                    tempReadBuffer.size,
                    AudioRecord.READ_BLOCKING
                )

                if (readSize > 0) {
                    val remainingSpace = outputFloatData.size - totalReadSamples
                    val copySize = minOf(readSize, remainingSpace)

                    System.arraycopy(tempReadBuffer, 0, recordedFloatBuffer, totalReadSamples, copySize)
                    totalReadSamples += copySize
                } else {
                    Log.e(TAG, "AudioRecord 읽기 오류 발생: $readSize")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "측정 중 오류 발생: ${e.message}")
        } finally {
            try {
                audioTrack.stop()
            } catch (e: Exception) {
                Log.e(TAG, "audioTrack stop error: ${e.message}")
            }
            audioTrack.release()

            try {
                audioRecord.stop()
            } catch (e: Exception) {
                Log.e(TAG, "audioRecord stop error: ${e.message}")
            }
            audioRecord.release()
        }

        Log.d(TAG, "수집 완료된 Float 샘플 개수: $totalReadSamples / ${outputFloatData.size}")
        return@withContext recordedFloatBuffer
    }
}
