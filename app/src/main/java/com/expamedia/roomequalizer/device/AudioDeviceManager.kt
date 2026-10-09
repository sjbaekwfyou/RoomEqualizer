package com.expamedia.roomequalizer.device

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
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
            try {
                if (enable) {
                    val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    speakerDevice?.let {
                        audioManager.setCommunicationDevice(it)
                    }
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set speaker communication device: ${e.message}", e)
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = enable
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set legacy speakerphone state: ${e.message}", e)
            }
        }
    }



    fun releaseAudioDevices() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    audioManager.clearCommunicationDevice()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear communication device: ${e.message}", e)
                }
            } else {
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothScoOn) {
                    audioManager.isBluetoothScoOn = false
                    audioManager.stopBluetoothSco()
                }
            }
            setSpeakerphoneEnabled(false)
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e(TAG, "Error in releaseAudioDevices: ${e.message}", e)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun playAndRecord(outputFloatData: FloatArray): FloatArray =
        withContext(Dispatchers.IO) {
            val bytesPerSample = 4
            val minRecordBufferSize =
                AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
            val recordBufferSize = maxOf(minRecordBufferSize, outputFloatData.size * bytesPerSample)

            // 💡 1. VOICE_COMMUNICATION 대신 VOICE_RECOGNITION 사용 (블루투스 마이크 자동 전환 방지)
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_IN,
                AUDIO_FORMAT,
                recordBufferSize
            )

            // 💡 1. 입력 마이크 선택: USB 마이크가 연결되어 있으면 USB 마이크 사용, 없으면 내장 마이크 사용
            try {
                val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

                val usbMic = inputDevices.firstOrNull { device ->
                    device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                            device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && device.type == AudioDeviceInfo.TYPE_USB_HEADSET)
                }

                if (usbMic != null) {
                    val isSuccess = audioRecord.setPreferredDevice(usbMic)
                    Log.d(TAG, "[입력] USB 마이크 사용: $isSuccess (${usbMic.productName})")
                } else {
                    val builtinMic = inputDevices.firstOrNull { device ->
                        device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
                    }
                    builtinMic?.let {
                        val isSuccess = audioRecord.setPreferredDevice(it)
                        Log.d(TAG, "[입력] 내장 마이크 사용: $isSuccess (${it.productName})")
                    } ?: run {
                        Log.w(TAG, "[입력] 내장 마이크 장치를 찾지 못했습니다.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "마이크 입력 장치 설정 중 오류 발생: ${e.message}", e)
            }

            val minTrackBufferSize =
                AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)

            // AudioTrack 속성도 USAGE_MEDIA (음악/측정용)로 맞추어 고음질 출력 유지
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
                .setBufferSizeInBytes(
                    maxOf(
                        minTrackBufferSize,
                        outputFloatData.size * bytesPerSample
                    )
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            // 💡 2. 출력 스피커 선택: 블루투스 스피커가 연결되어 있으면 블루투스 스피커 사용, 없으면 내장 스피커 사용
            try {
                val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val btSpeaker = outputDevices.firstOrNull { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                                            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                                    ))
                }

                if (btSpeaker != null) {
                    val isSuccess = audioTrack.setPreferredDevice(btSpeaker)
                    Log.d(TAG, "[출력] 블루투스 스피커 사용: $isSuccess (${btSpeaker.productName})")
                } else {
                    val builtinSpeaker = outputDevices.firstOrNull { device ->
                        device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    builtinSpeaker?.let {
                        val isSuccess = audioTrack.setPreferredDevice(it)
                        Log.d(TAG, "[출력] 내장 스피커 사용: $isSuccess (${it.productName})")
                    } ?: run {
                        Log.w(TAG, "[출력] 내장 스피커 장치를 찾지 못했습니다.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "스피커 출력 장치 설정 중 오류 발생: ${e.message}", e)
            }

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

                        System.arraycopy(
                            tempReadBuffer,
                            0,
                            recordedFloatBuffer,
                            totalReadSamples,
                            copySize
                        )
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
