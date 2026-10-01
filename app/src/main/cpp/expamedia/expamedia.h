#pragma once

#include <cstddef>

void GenerateESS(double FS, int ESS_LENGTH, double* chirp, size_t chirpSize);

int AmbientLevel(double FS, double* result, size_t resultLength);

int CalculatePEQ(double FS, int ESS_LENGTH, int N_PEQ, double F_MIN, double F_MAX, double G_MAX, double F_HPF,
                 double* chirp, size_t chirpLength, double* result, size_t resultLength, double* IIRcoef, size_t IIRcoefLength);

