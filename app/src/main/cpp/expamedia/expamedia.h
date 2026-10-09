#pragma once

#include <jni.h>
#include <android/log.h>

#define LOG_TAG "RoomEqualizerJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

void GenerateESS(float FS, int ESS_LENGTH, float* chirp);

int AmbientLevel(float FS, float* result, size_t resultLength);

int CalculatePEQ(float FS, int ESS_LENGTH, int N_PEQ, float F_MIN, float F_MAX, float G_MAX, float F_HPF, float* chirp, float* result, float* IIRcoef);

