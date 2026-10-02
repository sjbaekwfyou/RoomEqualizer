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
                speakerDevice?.let { audioManager.setCommunicationDevice(it) }
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enable
        }
    }

    fun initAudioDevices() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val availableDevices = audioManager.availableCommunicationDevices
            val btDevice = availableDevices.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            if (btDevice != null) {
                audioManager.setCommunicationDevice(btDevice)
            }
        } else {
            @Suppress("DEPRECATION")
            if (!audioManager.isBluetoothScoOn) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
        }
        // 출력을 내장 스피커로 설정
        setSpeakerphoneEnabled(true)
    }

    @SuppressLint("MissingPermission")
    suspend fun playAndRecord(outputFloatData: FloatArray): FloatArray = withContext(Dispatchers.IO) {
        val bytesPerSample = 4
        val minRecordBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
        val recordBufferSize = maxOf(minRecordBufferSize, outputFloatData.size * bytesPerSample)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_IN,
            AUDIO_FORMAT,
            recordBufferSize
        )

        val minTrackBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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
            audioTrack.stop()
            audioTrack.release()
            audioRecord.stop()
            audioRecord.release()
        }

        Log.d(TAG, "수집 완료된 Float 샘플 개수: $totalReadSamples / ${outputFloatData.size}")
        return@withContext recordedFloatBuffer
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
}
