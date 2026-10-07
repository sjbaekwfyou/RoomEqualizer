#include <cmath>
#include "expamedia.h"
#include <thread>
#include <chrono>

#include <string>
#include <vector>
#include <fstream>
#include <cstdint>
#include <algorithm>

static std::string lExternalDir;
void SetExternalFilesDir(const char* externalFilesDir) {
    lExternalDir = externalFilesDir ? externalFilesDir : "";
}

static void WriteLittleEndian(std::ofstream& file, uint32_t value, size_t size) {
    for (size_t i = 0; i < size; ++i) {
        char byte = static_cast<char>((value >> (i * 8)) & 0xFF);
        file.write(&byte, 1);
    }
}

static bool SaveWavFile(const std::string& filePath,
                        const std::vector<float>& buffer,
                        uint32_t sampleRate = 48000,
                        uint16_t channels = 1)
{
    std::ofstream file(filePath, std::ios::binary);
    if (!file.is_open()) {
        LOGE("Failed to open file: %s", filePath.c_str());
        return false;
    }

    uint16_t bitsPerSample = 16;
    uint32_t byteRate = sampleRate * channels * (bitsPerSample / 8);
    uint16_t blockAlign = channels * (bitsPerSample / 8);
    uint32_t pcmDataSize = static_cast<uint32_t>(buffer.size() * (bitsPerSample / 8));
    uint32_t totalDataSize = pcmDataSize + 36;

    file.write("RIFF", 4);
    WriteLittleEndian(file, totalDataSize, 4);
    file.write("WAVE", 4);
    file.write("fmt ", 4);
    WriteLittleEndian(file, 16, 4);          // SubChunk1Size (16 for PCM)
    WriteLittleEndian(file, 1, 2);           // AudioFormat (1 for PCM)
    WriteLittleEndian(file, channels, 2);    // NumChannels
    WriteLittleEndian(file, sampleRate, 4);  // SampleRate
    WriteLittleEndian(file, byteRate, 4);    // ByteRate
    WriteLittleEndian(file, blockAlign, 2);  // BlockAlign
    WriteLittleEndian(file, bitsPerSample, 2);// BitsPerSample
    file.write("data", 4);
    WriteLittleEndian(file, pcmDataSize, 4);

    for (float sample : buffer) {
        int16_t pcmValue = static_cast<int16_t>(sample * 32767.0);
        WriteLittleEndian(file, static_cast<uint16_t>(pcmValue), 2);
    }

    file.close();
    return true;
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
    const std::string filePath = lExternalDir + "/" + "AmbientLevel.wav";
    SaveWavFile(filePath, std::vector<float>(result, result + resultLength));

    static int l_ret = 0;
    int ret = l_ret++;
    return ret % 2;
    //return 0;   //OK
    //return 1;   //NG
}

int CalculatePEQ(float FS, int ESS_LENGTH, int N_PEQ, float F_MIN, float F_MAX, float G_MAX, float F_HPF, float* chirp, float* result, float* IIRcoef) {
    {
        const std::string filePath = lExternalDir + "/" + "CalculatePEQ_chirp.wav";
        SaveWavFile(filePath, std::vector<float>(chirp, chirp + ESS_LENGTH));
    }
    {
        const std::string filePath = lExternalDir + "/" + "CalculatePEQ_result.wav";
        SaveWavFile(filePath, std::vector<float>(result, result + ESS_LENGTH));
    }

    std::this_thread::sleep_for(std::chrono::seconds(1));
    static int l_ret = 0;
    int ret = l_ret++;
    return ret % 4;
    //return 0;    //success
    //return 1;    //error1
    //return 2;    //error2
    //return 3;    //error3
}

