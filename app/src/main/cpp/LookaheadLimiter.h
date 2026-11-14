#pragma once
#include <cmath>
#include <algorithm>

namespace hearpod {

class LookaheadLimiter {
public:
    LookaheadLimiter() = default;

    void setFeedbackPrevention(bool enabled) {
        mFeedbackPreventionEnabled = enabled;
    }

    bool isFeedbackPreventionEnabled() const {
        return mFeedbackPreventionEnabled;
    }

    void process(float* samples, int32_t count) {
        if (count <= 0) return;

        // Measure frame peak
        float maxPeak = 0.0f;
        for (int32_t i = 0; i < count; ++i) {
            float p = std::abs(samples[i]);
            if (p > maxPeak) maxPeak = p;
        }

        float targetGain = 1.0f;
        if (mFeedbackPreventionEnabled && maxPeak > 0.85f) {
            targetGain = 0.85f / maxPeak;
        }

        float limiterStep = (targetGain - mCurrentGain) / static_cast<float>(count);

        for (int32_t i = 0; i < count; ++i) {
            mCurrentGain += limiterStep;
            float s = samples[i] * mCurrentGain;

            // Soft-knee analog-style saturation prevents harsh digital clipping
            if (s > 0.90f) {
                s = 0.90f + 0.10f * ((s - 0.90f) / (1.0f + (s - 0.90f)));
            } else if (s < -0.90f) {
                s = -0.90f - 0.10f * ((-s - 0.90f) / (1.0f + (-s - 0.90f)));
            }

            samples[i] = std::clamp(s, -1.0f, 1.0f);
        }
    }

    void reset() {
        mCurrentGain = 1.0f;
    }

private:
    bool mFeedbackPreventionEnabled = true;
    float mCurrentGain = 1.0f;
};

} // namespace hearpod
