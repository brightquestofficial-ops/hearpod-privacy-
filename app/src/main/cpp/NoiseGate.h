#pragma once
#include <cmath>
#include <algorithm>

namespace hearpod {

enum class AiNoiseMode {
    STUDIO_QUIET = 0,
    BALANCED = 1,
    CONVERSATION_FOCUS = 2,
    OFF = 3
};

class NoiseGate {
public:
    NoiseGate() = default;

    void configure(float sampleRate) {
        mSampleRate = sampleRate;
        reset();
    }

    void setMode(AiNoiseMode mode) {
        mMode = mode;
    }

    AiNoiseMode getMode() const {
        return mMode;
    }

    void setTransparencyLevel(float level) {
        mTransparencyLevel = std::clamp(level, 0.0f, 1.0f);
    }

    float getTransparencyLevel() const {
        return mTransparencyLevel;
    }

    void process(float* samples, int32_t count) {
        if (count <= 0) return;

        // 1. Calculate frame RMS energy
        double sumSquares = 0.0;
        for (int32_t i = 0; i < count; ++i) {
            float s = samples[i];
            sumSquares += s * s;
        }
        float frameRms = static_cast<float>(std::sqrt(sumSquares / count));

        // 2. Track ambient stationary noise floor
        if (frameRms < mNoiseFloor * 1.4f) {
            mNoiseFloor = mNoiseFloor * 0.96f + frameRms * 0.04f;
        } else if (frameRms < mNoiseFloor) {
            mNoiseFloor = frameRms;
        }
        mNoiseFloor = std::clamp(mNoiseFloor, 0.0005f, 0.035f);

        // 3. Voice Activity Detection with Schmitt Trigger (Hysteresis)
        float openThreshold = std::max(0.0030f, mNoiseFloor * 2.2f);
        float closeThreshold = openThreshold * 0.75f;

        if (frameRms >= openThreshold) {
            mSpeechHoldFrames = 40; // ~160ms hangover hold
            mIsGateOpen = true;
        } else if (mSpeechHoldFrames > 0) {
            mSpeechHoldFrames--;
            mIsGateOpen = true;
        } else if (frameRms < closeThreshold) {
            mIsGateOpen = false;
        }

        float targetGain = 1.0f;
        if (mIsGateOpen) {
            if (mMode == AiNoiseMode::CONVERSATION_FOCUS) {
                targetGain = 1.35f;
            } else if (mMode == AiNoiseMode::BALANCED) {
                targetGain = 1.15f;
            } else if (mMode == AiNoiseMode::STUDIO_QUIET) {
                targetGain = 1.05f;
            } else {
                targetGain = 1.00f;
            }
        } else {
            // Ambient pause: attenuate stationary background noise/hiss to maintain a quiet, clean backdrop
            switch (mMode) {
                case AiNoiseMode::STUDIO_QUIET:
                    targetGain = 0.20f; // -14 dB
                    break;
                case AiNoiseMode::BALANCED:
                    targetGain = std::clamp(0.40f + 0.20f * mTransparencyLevel, 0.25f, 0.60f); // -18 dB
                    break;
                case AiNoiseMode::CONVERSATION_FOCUS:
                    targetGain = 0.30f; // -10 dB
                    break;
                case AiNoiseMode::OFF:
                    targetGain = 1.00f;
                    break;
            }
        }

        // 4. Smooth sample-by-sample linear gain ramp (eliminates clicks, vinyl pops, or flutter)
        float gainStep = (targetGain - mCurrentGain) / static_cast<float>(count);
        for (int32_t i = 0; i < count; ++i) {
            mCurrentGain += gainStep;
            samples[i] *= mCurrentGain;
        }
        mCurrentGain = targetGain;
    }

    bool isGateOpen() const {
        return mIsGateOpen;
    }

    float getCurrentGainReductionDb() const {
        float g = std::max(mCurrentGain, 0.0001f);
        return 20.0f * std::log10(g);
    }

    void reset() {
        mNoiseFloor = 0.002f;
        mCurrentGain = 1.0f;
        mSpeechHoldFrames = 0;
        mIsGateOpen = false;
    }

private:
    AiNoiseMode mMode = AiNoiseMode::BALANCED;
    float mSampleRate = 48000.0f;
    float mNoiseFloor = 0.002f;
    float mCurrentGain = 1.0f;
    float mTransparencyLevel = 0.85f;
    int32_t mSpeechHoldFrames = 0;
    bool mIsGateOpen = false;
};

} // namespace hearpod
