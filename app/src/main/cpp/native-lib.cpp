#include <jni.h>
#include <string>
#include "expamedia/expamedia.h"

extern "C"
JNIEXPORT void JNICALL
Java_com_expamedia_roomequalizer_native_NativeEqualizer_GenerateESS(
        JNIEnv *env,
        jobject thiz,
        jdouble FS,
        jint ESS_LENGTH,
        jdoubleArray chirp
) {
    if (chirp == nullptr) return;

    jdouble *buffer = env->GetDoubleArrayElements(chirp, nullptr);
    if (buffer) {
        jsize length = env->GetArrayLength(chirp);

        GenerateESS(FS, ESS_LENGTH, buffer, length);

        env->ReleaseDoubleArrayElements(chirp, buffer, 0);
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_expamedia_roomequalizer_native_NativeEqualizer_AmbientLevel(
        JNIEnv *env,
        jobject thiz,
        jdouble FS,
        jdoubleArray result
) {
    jint ret = 1;   //NG
    jdouble *buffer = env->GetDoubleArrayElements(result, nullptr);

    if (buffer) {
        jsize length = env->GetArrayLength(result);

        ret = AmbientLevel(FS, buffer, length);
        env->ReleaseDoubleArrayElements(result, buffer, 0);
    }

    return ret;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_expamedia_roomequalizer_native_NativeEqualizer_CalculatePEQ(
        JNIEnv *env,
        jobject thiz,
        jdouble FS,
        jint ESS_LENGTH,
        jint N_PEQ,
        jdouble F_MIN,
        jdouble F_MAX,
        jdouble G_MAX,
        jdouble F_HPF,
        jdoubleArray chirp,
        jdoubleArray result,
        jdoubleArray IIRcoef
) {
    jint ret = 1;

    jdouble *chirpBuffer = env->GetDoubleArrayElements(chirp, nullptr);
    jdouble *resultBuffer = env->GetDoubleArrayElements(result, nullptr);
    jdouble *IIRcoefBuffer = env->GetDoubleArrayElements(IIRcoef, nullptr);

    if (chirpBuffer && resultBuffer && IIRcoefBuffer) {
        jsize chirpLength = env->GetArrayLength(chirp);
        jsize resultLength = env->GetArrayLength(result);
        jsize IIRcoefLength = env->GetArrayLength(IIRcoef);

        ret = CalculatePEQ(FS, ESS_LENGTH, N_PEQ, F_MIN, F_MAX, G_MAX, F_HPF, chirpBuffer, chirpLength, resultBuffer, resultLength, IIRcoefBuffer, IIRcoefLength);
    }

    if(chirpBuffer) {
        env->ReleaseDoubleArrayElements(chirp, chirpBuffer, 0);
    }
    if(resultBuffer) {
        env->ReleaseDoubleArrayElements(result, resultBuffer, 0);
    }
    if(IIRcoefBuffer) {
        env->ReleaseDoubleArrayElements(IIRcoef, IIRcoefBuffer, 0);
    }
    return ret;
}