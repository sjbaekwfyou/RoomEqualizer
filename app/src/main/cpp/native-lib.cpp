#include <jni.h>
#include <string>

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
    if (buffer == nullptr) return;

    jsize length = env->GetArrayLength(chirp);
    {   //test code
        int count = (length < ESS_LENGTH) ? length : ESS_LENGTH;

        double f1 = 20.0;
        double f2 = FS / 2.0;
        double T = static_cast<double>(ESS_LENGTH) / FS;
        double R = std::log(f2 / f1);

        for (int i = 0; i < count; ++i) {
            double t = static_cast<double>(i) / FS;
            double phase = (2.0 * M_PI * f1 * T / R) * (std::exp(t * R / T) - 1.0);
            buffer[i] = std::sin(phase);
        }
    }
    env->ReleaseDoubleArrayElements(chirp, buffer, 0);
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
    jsize length = env->GetArrayLength(result);

    if (buffer) {
        ret = 0;    //OK
    }

    env->ReleaseDoubleArrayElements(result, buffer, 0);
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
    jsize chirpLength = env->GetArrayLength(chirp);

    jdouble *resultBuffer = env->GetDoubleArrayElements(result, nullptr);
    jsize resultLength = env->GetArrayLength(result);

    jdouble *IIRcoefBuffer = env->GetDoubleArrayElements(IIRcoef, nullptr);
    jsize IIRcoefLength = env->GetArrayLength(IIRcoef);

    if (chirpBuffer && resultBuffer && IIRcoefBuffer) {
        ret = 0;    //success
        //ret = 1;    //error1
        //ret = 2;    //error2
        //ret = 3;    //error3
    }

    env->ReleaseDoubleArrayElements(chirp, chirpBuffer, 0);
    env->ReleaseDoubleArrayElements(result, resultBuffer, 0);
    env->ReleaseDoubleArrayElements(IIRcoef, IIRcoefBuffer, 0);
    return ret;
}