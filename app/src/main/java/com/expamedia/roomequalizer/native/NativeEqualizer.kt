package com.expamedia.roomequalizer.native

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavFileWriter {
    private const val TAG = "WavFileWriter"

    fun saveWavToPublicMusic(
        context: Context,
        fileName: String,
        buffer: FloatArray,
        sampleRate: Int = 48000,
        channels: Short = 1,
        subFolder: String = "RoomEqualizer"
    ): Boolean {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_MUSIC}/$subFolder"

        val existingUri = getExistingAudioUri(context, fileName, relativePath)
        val targetUri: Uri = if (existingUri != null) {
            existingUri
        } else {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: run {
                    return false
                }
        }

        return try {
            resolver.openOutputStream(targetUri, "rwt")?.use { outputStream ->
                writeWavData(outputStream, buffer, sampleRate, channels)
            } ?: throw IOException("Failed to open output stream for URI: $targetUri")

            if (existingUri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                resolver.update(targetUri, updateValues, null, null)
            }

            true
        } catch (e: Exception) {
            if (existingUri == null) {
                resolver.delete(targetUri, null, null)
            }
            false
        }
    }

    private fun getExistingAudioUri(context: Context, fileName: String, relativePath: String): Uri? {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID)

        val selection: String
        val selectionArgs: Array<String>

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val formattedPath = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf(fileName, formattedPath)
        } else {
            selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            selectionArgs = arrayOf(fileName)
        }

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val id = cursor.getLong(idColumn)
                return Uri.withAppendedPath(collection, id.toString())
            }
        }
        return null
    }

    private fun writeWavData(
        outputStream: OutputStream,
        buffer: FloatArray,
        sampleRate: Int,
        channels: Short
    ) {
        val bitsPerSample: Short = 16
        val bytesPerSample = bitsPerSample / 8
        val byteRate = sampleRate * channels * bytesPerSample
        val blockAlign = (channels * bytesPerSample).toShort()
        val pcmDataSize = buffer.size * bytesPerSample
        val totalDataSize = pcmDataSize + 36

        BufferedOutputStream(outputStream).use { bos ->
            // 1. WAV 44바이트 헤더 구성 (Little-Endian)
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

            bos.write(header.array())

            // 2. Float PCM 데이터 -> Int16 데이터 변환 및 기록
            val pcmBuffer = ByteBuffer.allocate(buffer.size * 2).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                for (sample in buffer) {
                    val clampedSample = sample.coerceIn(-1.0f, 1.0f)
                    val pcmValue = (clampedSample * 32767.0f).toInt().toShort()
                    putShort(pcmValue)
                }
            }

            bos.write(pcmBuffer.array())
            bos.flush()
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
        val dir = (context as AppCompatActivity).getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        if (dir != null) {
            WavFileWriter.saveWavToPublicMusic(context, "Expamedia - ambient.wav", result)
        }

        return nativeAmbientLevel(FS, result)
    }

    public fun calculatePEQ(chirp: FloatArray, result: FloatArray, IIRcoef: FloatArray): Int {
        val dir = (context as AppCompatActivity).getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        if (dir != null) {
            WavFileWriter.saveWavToPublicMusic(context, "Expamedia - chirp.wav", chirp)
            WavFileWriter.saveWavToPublicMusic(context, "Expamedia - record.wav", result)
        }

        return nativeCalculatePEQ(FS, ESS_LENGTH, N_PEQ, F_MIN, F_MAX, G_MAX, F_HPF, chirp, result, IIRcoef)
    }
}