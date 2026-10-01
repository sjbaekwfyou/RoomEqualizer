#include <cmath>
#include "expamedia.h"

void GenerateESS(double FS, int ESS_LENGTH, double* chirp, size_t chirpSize) {
    //test code
    int count = (chirpSize < ESS_LENGTH) ? chirpSize : ESS_LENGTH;

    double f1 = 20.0;
    double f2 = FS / 2.0;
    double T = static_cast<double>(ESS_LENGTH) / FS;
    double R = std::log(f2 / f1);

    for (int i = 0; i < count; ++i) {
        double t = static_cast<double>(i) / FS;
        double phase = (2.0 * M_PI * f1 * T / R) * (std::exp(t * R / T) - 1.0);
        chirp[i] = std::sin(phase);
    }
}

int AmbientLevel(double FS, double* result, size_t resultLength) {
    return 0;   //OK
    //return 1;   //NG
}

int CalculatePEQ(double FS, int ESS_LENGTH, int N_PEQ, double F_MIN, double F_MAX, double G_MAX, double F_HPF,
                 double* chirp, size_t chirpLength, double* result, size_t resultLength, double* IIRcoef, size_t IIRcoefLength)
{
    return 0;    //success
    //return 1;    //error1
    //return 2;    //error2
    //return 3;    //error3
}

