#pragma once
#include <cmath>
#include <algorithm>
#include <atomic>

namespace hearpod {

class BiquadFilter {
public:
    enum class Type {
        HighPass,
        LowPass,
        PeakingEQ
    };

    struct Coeffs {
        float b0{1.0f}, b1{0.0f}, b2{0.0f};
        float a1{0.0f}, a2{0.0f};
    };

    BiquadFilter() {
        mCoeffs[0] = {1.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        mCoeffs[1] = {1.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        mActiveIdx.store(0, std::memory_order_relaxed);
    }

    BiquadFilter(const BiquadFilter& other) {
        mType = other.mType;
        mFreq = other.mFreq;
        mSampleRate = other.mSampleRate;
        mGainDb = other.mGainDb;
        mQ = other.mQ;
        int idx = other.mActiveIdx.load(std::memory_order_relaxed);
        mCoeffs[0] = other.mCoeffs[0];
        mCoeffs[1] = other.mCoeffs[1];
        mActiveIdx.store(idx, std::memory_order_relaxed);
        s1 = other.s1;
        s2 = other.s2;
    }

    BiquadFilter& operator=(const BiquadFilter& other) {
        if (this != &other) {
            mType = other.mType;
            mFreq = other.mFreq;
            mSampleRate = other.mSampleRate;
            mGainDb = other.mGainDb;
            mQ = other.mQ;
            int idx = other.mActiveIdx.load(std::memory_order_relaxed);
            mCoeffs[0] = other.mCoeffs[0];
            mCoeffs[1] = other.mCoeffs[1];
            mActiveIdx.store(idx, std::memory_order_relaxed);
            s1 = other.s1;
            s2 = other.s2;
        }
        return *this;
    }

    BiquadFilter(BiquadFilter&& other) noexcept {
        mType = other.mType;
        mFreq = other.mFreq;
        mSampleRate = other.mSampleRate;
        mGainDb = other.mGainDb;
        mQ = other.mQ;
        int idx = other.mActiveIdx.load(std::memory_order_relaxed);
        mCoeffs[0] = other.mCoeffs[0];
        mCoeffs[1] = other.mCoeffs[1];
        mActiveIdx.store(idx, std::memory_order_relaxed);
        s1 = other.s1;
        s2 = other.s2;
    }

    BiquadFilter& operator=(BiquadFilter&& other) noexcept {
        if (this != &other) {
            mType = other.mType;
            mFreq = other.mFreq;
            mSampleRate = other.mSampleRate;
            mGainDb = other.mGainDb;
            mQ = other.mQ;
            int idx = other.mActiveIdx.load(std::memory_order_relaxed);
            mCoeffs[0] = other.mCoeffs[0];
            mCoeffs[1] = other.mCoeffs[1];
            mActiveIdx.store(idx, std::memory_order_relaxed);
            s1 = other.s1;
            s2 = other.s2;
        }
        return *this;
    }

    void configure(Type type, float frequencyHz, float sampleRate, float gainDb = 0.0f, float q = 0.7071f) {
        mType = type;
        mFreq = frequencyHz;
        mSampleRate = sampleRate;
        mGainDb = gainDb;
        mQ = q;
        recalculate();
    }

    void setGain(float gainDb) {
        if (std::abs(mGainDb - gainDb) > 0.01f) {
            mGainDb = gainDb;
            recalculate();
        }
    }

    float getGain() const { return mGainDb; }
    float getFrequency() const { return mFreq; }

    inline float process(float in) {
        int idx = mActiveIdx.load(std::memory_order_acquire);
        const auto& c = mCoeffs[idx];
        float out = c.b0 * in + s1;
        s1 = c.b1 * in - c.a1 * out + s2;
        s2 = c.b2 * in - c.a2 * out;
        if (std::isnan(out) || std::isinf(out)) {
            reset();
            return in;
        }
        return out;
    }

    void reset() {
        s1 = 0.0f;
        s2 = 0.0f;
    }

private:
    void recalculate() {
        constexpr float kPi = 3.14159265358979323846f;
        float omega = 2.0f * kPi * mFreq / mSampleRate;
        float sn = std::sin(omega);
        float cs = std::cos(omega);
        float alpha = sn / (2.0f * mQ);

        Coeffs nextCoeffs;
        switch (mType) {
            case Type::HighPass: {
                float a0 = 1.0f + alpha;
                nextCoeffs.b0 = ((1.0f + cs) / 2.0f) / a0;
                nextCoeffs.b1 = (-(1.0f + cs)) / a0;
                nextCoeffs.b2 = ((1.0f + cs) / 2.0f) / a0;
                nextCoeffs.a1 = (-2.0f * cs) / a0;
                nextCoeffs.a2 = (1.0f - alpha) / a0;
                break;
            }
            case Type::LowPass: {
                float a0 = 1.0f + alpha;
                nextCoeffs.b0 = ((1.0f - cs) / 2.0f) / a0;
                nextCoeffs.b1 = (1.0f - cs) / a0;
                nextCoeffs.b2 = ((1.0f - cs) / 2.0f) / a0;
                nextCoeffs.a1 = (-2.0f * cs) / a0;
                nextCoeffs.a2 = (1.0f - alpha) / a0;
                break;
            }
            case Type::PeakingEQ: {
                float A = std::pow(10.0f, mGainDb / 40.0f);
                float a0 = 1.0f + alpha / A;
                nextCoeffs.b0 = (1.0f + alpha * A) / a0;
                nextCoeffs.b1 = (-2.0f * cs) / a0;
                nextCoeffs.b2 = (1.0f - alpha * A) / a0;
                nextCoeffs.a1 = (-2.0f * cs) / a0;
                nextCoeffs.a2 = (1.0f - alpha / A) / a0;
                break;
            }
        }

        int nextIdx = 1 - mActiveIdx.load(std::memory_order_relaxed);
        mCoeffs[nextIdx] = nextCoeffs;
        mActiveIdx.store(nextIdx, std::memory_order_release);
    }

    Type mType = Type::PeakingEQ;
    float mFreq = 1000.0f;
    float mSampleRate = 48000.0f;
    float mGainDb = 0.0f;
    float mQ = 0.7071f;

    Coeffs mCoeffs[2];
    std::atomic<int> mActiveIdx{0};

    float s1 = 0.0f, s2 = 0.0f;
};

} // namespace hearpod
