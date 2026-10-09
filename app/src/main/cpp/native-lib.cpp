#include <jni.h>
#include <string>
#include "expamedia/expamedia.h"

extern "C"
JNIEXPORT void JNICALL
Java_com_expamedia_roomequalizer_native_NativeEqualizer_nativeGenerateESS(
        JNIEnv *env,
        jobject thiz,
        jfloat FS,
        jint ESS_LENGTH,
        jfloatArray chirp
) {
    if (chirp == nullptr) return;

    jfloat *buffer = env->GetFloatArrayElements(chirp, nullptr);
    if (!buffer) {
        LOGE("GenerateESS. buffer is null.");
    }
    else {
        jsize length = env->GetArrayLength(chirp);
        if(length == ESS_LENGTH) {
            GenerateESS(FS, ESS_LENGTH, buffer);
            LOGD("GenerateESS");
        }
        else {
            LOGE("GenerateESS. chirp size is not equal to ESS_LENGTH. [%d vs %d]", ESS_LENGTH, length);
        }
        env->ReleaseFloatArrayElements(chirp, buffer, 0);
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_expamedia_roomequalizer_native_NativeEqualizer_nativeAmbientLevel(
        JNIEnv *env,
        jobject thiz,
        jfloat FS,
        jfloatArray result
) {
    jint ret = 1;   //NG
    jfloat *buffer = env->GetFloatArrayElements(result, nullptr);

    if (!buffer) {
        LOGE("AmbientLevel. buffer is null.");
    }
    else {
        jsize length = env->GetArrayLength(result);
        ret = AmbientLevel(FS, buffer, length);
        env->ReleaseFloatArrayElements(result, buffer, 0);

        LOGD("AmbientLevel. result size:%d ret:%d", length, ret);
    }

    return ret;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_expamedia_roomequalizer_native_NativeEqualizer_nativeCalculatePEQ(
        JNIEnv *env,
        jobject thiz,
        jfloat FS,
        jint ESS_LENGTH,
        jint N_PEQ,
        jfloat F_MIN,
        jfloat F_MAX,
        jfloat G_MAX,
        jfloat F_HPF,
        jfloatArray chirp,
        jfloatArray result,
        jfloatArray IIRcoef
) {
    jint ret = 1;

    jfloat *chirpBuffer = env->GetFloatArrayElements(chirp, nullptr);
    jfloat *resultBuffer = env->GetFloatArrayElements(result, nullptr);
    jfloat *IIRcoefBuffer = env->GetFloatArrayElements(IIRcoef, nullptr);

    if (!chirpBuffer) {
        LOGE("CalculatePEQ. chirpBuffer is null.");
    }
    else if (!resultBuffer) {
        LOGE("CalculatePEQ. resultBuffer is null.");
    }
    else if (!IIRcoefBuffer) {
        LOGE("CalculatePEQ. IIRcoefBuffer is null.");
    }
    else {
        jsize chirpLength = env->GetArrayLength(chirp);
        jsize resultLength = env->GetArrayLength(result);
        jsize IIRcoefLength = env->GetArrayLength(IIRcoef);

        if(chirpLength != ESS_LENGTH) {
            LOGE("CalculatePEQ. chirp size is not equal to ESS_LENGTH. [%d vs %d]", ESS_LENGTH, chirpLength);
        }
        else if(resultLength != ESS_LENGTH) {
            LOGE("CalculatePEQ. result size is not equal to ESS_LENGTH. [%d vs %d]", ESS_LENGTH, resultLength);
        }
        else if(IIRcoefLength != ESS_LENGTH) {
            LOGE("CalculatePEQ. IIRcoef size is not equal to ESS_LENGTH. [%d vs %d]", ESS_LENGTH, IIRcoefLength);
        }
        else {
            ret = CalculatePEQ(FS, ESS_LENGTH, N_PEQ, F_MIN, F_MAX, G_MAX, F_HPF, chirpBuffer, resultBuffer, IIRcoefBuffer);
            LOGD("CalculatePEQ. ret:%d", ret);
        }
    }

    if(chirpBuffer) {
        env->ReleaseFloatArrayElements(chirp, chirpBuffer, 0);
    }
    if(resultBuffer) {
        env->ReleaseFloatArrayElements(result, resultBuffer, 0);
    }
    if(IIRcoefBuffer) {
        env->ReleaseFloatArrayElements(IIRcoef, IIRcoefBuffer, 0);
    }
    return ret;
}
