#include <cmath>
#include "expamedia.h"
#include <thread>
#include <chrono>

#include <string>
#include <vector>
#include <fstream>
#include <cstdint>
#include <algorithm>

static void WriteLittleEndian(std::ofstream& file, uint32_t value, size_t size) {
    for (size_t i = 0; i < size; ++i) {
        char byte = static_cast<char>((value >> (i * 8)) & 0xFF);
        file.write(&byte, 1);
    }
}

void GenerateESS(float FS, int ESS_LENGTH, float* chirp) {
    //test code
    float f1 = 20.0;
    float f2 = FS / 2.0;
    float T = static_cast<float>(ESS_LENGTH) / FS;
    float R = std::log(f2 / f1);

    for (int i = 0; i < ESS_LENGTH; ++i) {
        float t = static_cast<float>(i) / FS;
        float phase = (2.0 * M_PI * f1 * T / R) * (std::exp(t * R / T) - 1.0);
        chirp[i] = std::sin(phase);
    }
}

int AmbientLevel(float FS, float* result, size_t resultLength) {
    static int l_ret = 0;
    int ret = l_ret++;
    return ret % 2;
    //return 0;   //OK
    //return 1;   //NG
}

int CalculatePEQ(float FS, int ESS_LENGTH, int N_PEQ, float F_MIN, float F_MAX, float G_MAX, float F_HPF, float* chirp, float* result, float* IIRcoef) {
    std::this_thread::sleep_for(std::chrono::seconds(1));
    static int l_ret = 0;
    int ret = l_ret++;
    return ret % 4;
    //return 0;    //success
    //return 1;    //error1
    //return 2;    //error2
    //return 3;    //error3
}

