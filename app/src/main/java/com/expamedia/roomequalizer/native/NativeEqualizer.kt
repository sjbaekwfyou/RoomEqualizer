package com.expamedia.roomequalizer.native

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavFileWriter {
    private const val TAG = "WavFileWriter"

    fun saveWavFile(
        filePath: String,
        buffer: FloatArray,
        sampleRate: Int = 48000,
        channels: Short = 1
    ): Boolean {
        val bitsPerSample: Short = 16
        val bytesPerSample = bitsPerSample / 8 // 2 bytes
        val byteRate = sampleRate * channels * bytesPerSample
        val blockAlign = (channels * bytesPerSample).toShort()
        val pcmDataSize = buffer.size * bytesPerSample
        val totalDataSize = pcmDataSize + 36

        return try {
            FileOutputStream(filePath).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    // Little-Endian 데이터 작성을 위한 ByteBuffer 할당 (헤더 크기 44 bytes)
                    val header = ByteBuffer.allocate(44).apply {
                        order(ByteOrder.LITTLE_ENDIAN)

                        // RIFF Header
                        put("RIFF".toByteArray(Charsets.US_ASCII))
                        putInt(totalDataSize)
                        put("WAVE".toByteArray(Charsets.US_ASCII))

                        // fmt Subchunk
                        put("fmt ".toByteArray(Charsets.US_ASCII))
                        putInt(16)                  // SubChunk1Size (16 for PCM)
                        putShort(1.toShort())       // AudioFormat (1 for PCM)
                        putShort(channels)          // NumChannels
                        putInt(sampleRate)          // SampleRate
                        putInt(byteRate)            // ByteRate
                        putShort(blockAlign)        // BlockAlign
                        putShort(bitsPerSample)     // BitsPerSample

                        // data Subchunk
                        put("data".toByteArray(Charsets.US_ASCII))
                        putInt(pcmDataSize)
                    }

                    // 헤더 작성
                    bos.write(header.array())

                    // PCM 16-bit 변환 및 데이터 작성
                    val pcmBuffer = ByteBuffer.allocate(buffer.size * 2).apply {
                        order(ByteOrder.LITTLE_ENDIAN)
                        for (sample in buffer) {
                            // Float(-1.0f ~ 1.0f) -> Int16(-32768 ~ 32767) 클리핑 처리
                            val clampedSample = sample.coerceIn(-1.0f, 1.0f)
                            val pcmValue = (clampedSample * 32767.0f).toInt().toShort()
                            putShort(pcmValue)
                        }
                    }

                    bos.write(pcmBuffer.array())
                    bos.flush()
                }
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write WAV file: $filePath", e)
            false
        }
    }
}

class NativeEqualizer(val context: Context, val chirp: FloatArray) {
    companion object {
        public const val FS: Float = 48000.0f
        public const val ESS_LENGTH: Int = 72000
        public const val N_PEQ: Int = 12
        public const val F_MIN: Float = 50.0f
        public const val F_MAX: Float = 20000.0f
        public const val F_HPF: Float = 40.0f
        public const val G_MAX: Float = 12.0f
    }

    external fun nativeGenerateESS(FS: Float, ESS_LENGTH: Int, chirp: FloatArray)
    external fun nativeAmbientLevel(FS: Float, result: FloatArray): Int
    external fun nativeCalculatePEQ(FS: Float, ESS_LENGTH: Int, N_PEQ: Int, F_MIN: Float, F_MAX: Float, G_MAX: Float, F_HPF: Float, chirp: FloatArray, result: FloatArray, IIRcoef: FloatArray): Int;

    init {
        System.loadLibrary("roomequalizer")
        nativeGenerateESS(FS, ESS_LENGTH, chirp)
    }

    public fun ambientLevel(result: FloatArray): Int {
        val dir = (context as AppCompatActivity).getExternalFilesDir(null)
        if (dir != null) {
            WavFileWriter.saveWavFile(File(dir, "AmbientLevel.wav").absolutePath, result)
        }

        return nativeAmbientLevel(FS, result)
    }

    public fun calculatePEQ(chirp: FloatArray, result: FloatArray, IIRcoef: FloatArray): Int {
        val dir = (context as AppCompatActivity).getExternalFilesDir(null)
        if (dir != null) {
            WavFileWriter.saveWavFile(File(dir, "CalculatePEQ_chirp.wav").absolutePath, chirp)
            WavFileWriter.saveWavFile(File(dir, "CalculatePEQ_result.wav").absolutePath, result)
        }

        return nativeCalculatePEQ(FS, ESS_LENGTH, N_PEQ, F_MIN, F_MAX, G_MAX, F_HPF, chirp, result, IIRcoef)
    }
}