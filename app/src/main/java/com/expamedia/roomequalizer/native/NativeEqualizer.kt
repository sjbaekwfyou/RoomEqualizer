package com.expamedia.roomequalizer.native

class NativeEqualizer {
    private final val FS: Double = 48000.0
    public final val ESS_LENGTH: Int = 72000
    private final val N_PEQ: Int = 12
    private final val F_MIN: Double = 50.0
    private final val F_MAX: Double = 20000.0
    private final val F_HPF: Double = 40.0
    private final val G_MAX: Double = 12.0

    /**
     * A native method that is implemented by the 'roomequalizer' native library,
     * which is packaged with this application.
     */
    external fun nativeGenerateESS(FS: Double, ESS_LENGTH: Int, chirp: DoubleArray)
    external fun nativeAmbientLevel(FS: Double, result: DoubleArray): Int
    external fun nativeCalculatePEQ(FS: Double, ESS_LENGTH: Int, N_PEQ: Int, F_MIN: Double, F_MAX: Double, G_MAX: Double, F_HPF: Double, chirp: DoubleArray, result: DoubleArray, IIRcoef: DoubleArray): Int;

    companion object {
        // Used to load the 'roomequalizer' library on application startup.
        init {
            System.loadLibrary("roomequalizer")
        }
    }

    fun generateESS(chirp: DoubleArray) {
        nativeGenerateESS(FS, ESS_LENGTH, chirp)
    }

    fun ambientLevel(result: DoubleArray): Int {
        return nativeAmbientLevel(FS, result)
    }

    fun calculatePEQ(chirp: DoubleArray, result: DoubleArray, IIRcoef: DoubleArray): Int {
        return nativeCalculatePEQ(FS, ESS_LENGTH, N_PEQ, F_MIN, F_MAX, G_MAX, F_HPF, chirp, result, IIRcoef)
    }
}