package com.expamedia.roomequalizer.native

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

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

    external fun nativeInitialize(externalFilesDir: String?)
    external fun nativeGenerateESS(FS: Float, ESS_LENGTH: Int, chirp: FloatArray)
    external fun nativeAmbientLevel(FS: Float, result: FloatArray): Int
    external fun nativeCalculatePEQ(FS: Float, ESS_LENGTH: Int, N_PEQ: Int, F_MIN: Float, F_MAX: Float, G_MAX: Float, F_HPF: Float, chirp: FloatArray, result: FloatArray, IIRcoef: FloatArray): Int;

    init {
        System.loadLibrary("roomequalizer")
        nativeInitialize((context as AppCompatActivity).getExternalFilesDir(null)?.absolutePath)
        nativeGenerateESS(FS, ESS_LENGTH, chirp)
    }

    public fun ambientLevel(result: FloatArray): Int {
        return nativeAmbientLevel(FS, result)
    }

    public fun calculatePEQ(chirp: FloatArray, result: FloatArray, IIRcoef: FloatArray): Int {
        return nativeCalculatePEQ(FS, ESS_LENGTH, N_PEQ, F_MIN, F_MAX, G_MAX, F_HPF, chirp, result, IIRcoef)
    }
}