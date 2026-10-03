#include <cmath>
#include "expamedia.h"
#include <thread>
#include <chrono>

void GenerateESS(double FS, int ESS_LENGTH, double* chirp) {
    //test code
    double f1 = 20.0;
    double f2 = FS / 2.0;
    double T = static_cast<double>(ESS_LENGTH) / FS;
    double R = std::log(f2 / f1);

    for (int i = 0; i < ESS_LENGTH; ++i) {
        double t = static_cast<double>(i) / FS;
        double phase = (2.0 * M_PI * f1 * T / R) * (std::exp(t * R / T) - 1.0);
        chirp[i] = std::sin(phase);
    }
}

int AmbientLevel(double FS, double* result, size_t resultLength) {
    static int l_ret = 0;
    int ret = l_ret++;
    return ret % 2;
    //return 0;   //OK
    //return 1;   //NG
}

int CalculatePEQ(double FS, int ESS_LENGTH, int N_PEQ, double F_MIN, double F_MAX, double G_MAX, double F_HPF, double* chirp, double* result, double* IIRcoef) {
    std::this_thread::sleep_for(std::chrono::seconds(1));
    static int l_ret = 0;
    int ret = l_ret++;
    return ret % 4;
    //return 0;    //success
    //return 1;    //error1
    //return 2;    //error2
    //return 3;    //error3
}

