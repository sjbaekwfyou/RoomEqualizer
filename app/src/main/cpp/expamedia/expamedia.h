#pragma once

#include <jni.h>
#include <android/log.h>

#define LOG_TAG "RoomEqualizerJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

void GenerateESS(double FS, int ESS_LENGTH, double* chirp);

int AmbientLevel(double FS, double* result, size_t resultLength);

int CalculatePEQ(double FS, int ESS_LENGTH, int N_PEQ, double F_MIN, double F_MAX, double G_MAX, double F_HPF, double* chirp, double* result, double* IIRcoef);

