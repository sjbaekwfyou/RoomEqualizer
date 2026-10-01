package com.expamedia.roomequalizer.native

class NativeEqualizer {
    public val FS: Double = 48000.0
    public val ESS_LENGTH: Int = 72000
    public val N_PEQ: Int = 12
    public val F_MIN: Double = 50.0
    public val F_MAX: Double = 20000.0
    public val F_HPF: Double = 40.0
    public val G_MAX: Double = 12.0

    /**
     * A native method that is implemented by the 'roomequalizer' native library,
     * which is packaged with this application.
     */
    external fun GenerateESS(FS: Double, ESS_LENGTH: Int, chirp: DoubleArray)
    external fun AmbientLevel(FS: Double, result: DoubleArray): Int
    external fun CalculatePEQ(FS: Double, ESS_LENGTH: Int, N_PEQ: Int, F_MIN: Double, F_MAX: Double, G_MAX: Double, F_HPF: Double, chirp: DoubleArray, result: DoubleArray, IIRcoef: DoubleArray): Int;

    companion object {
        // Used to load the 'roomequalizer' library on application startup.
        init {
            System.loadLibrary("roomequalizer")
        }
    }

    fun GenerateESS(chirp: DoubleArray) {
        GenerateESS(FS, ESS_LENGTH, chirp)
    }

    fun AmbientLevel(result: DoubleArray): Int {
        return AmbientLevel(FS, result)
    }

    fun CalculatePEQ(chirp: DoubleArray, result: DoubleArray, IIRcoef: DoubleArray): Int {
        return CalculatePEQ(FS, ESS_LENGTH, N_PEQ, F_MIN, F_MAX, G_MAX, F_HPF, chirp, result, IIRcoef)
    }
}