#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <vector>
#include <memory>
#include <mutex>
#include "BiquadFilter.h"
#include "NoiseGate.h"
#include "LookaheadLimiter.h"

namespace hearpod {

class OboeAudioEngine : public oboe::AudioStreamDataCallback,
                        public oboe::AudioStreamErrorCallback {
public:
    OboeAudioEngine();
    virtual ~OboeAudioEngine();

    bool start();
    void stop();
    bool isRunning() const { return mIsRunning.load(); }

    void setMasterGain(float gain);
    float getMasterGain() const { return mMasterGain.load(); }

    void setBandGain(int frequencyHz, float gainDb);
    float getBandGain(int frequencyHz) const;

    void setAiNoiseMode(int mode);
    int getAiNoiseMode() const;

    void setFeedbackPrevention(bool enabled);
    bool isFeedbackPreventionEnabled() const;

    float getCurrentDb() const { return mCurrentDb.load(); }
    float getPeakDb() const { return mPeakDb.load(); }
    float getLatencyMs() const { return mLatencyMs.load(); }
    void getWaveformSamples(float* out32Samples) const;

    void setEarBalance(float balance) { mEarBalance.store(std::clamp(balance, -1.0f, 1.0f)); }
    float getEarBalance() const { return mEarBalance.load(); }

    void setLeftRightGains(float left, float right) {
        mLeftGain.store(std::clamp(left, 0.0f, 2.0f));
        mRightGain.store(std::clamp(right, 0.0f, 2.0f));
    }
    float getLeftGain() const { return mLeftGain.load(); }
    float getRightGain() const { return mRightGain.load(); }

    void setTransparencyLevel(float level) {
        float clamped = std::clamp(level, 0.0f, 1.0f);
        mTransparencyLevel.store(clamped);
        mNoiseGate.setTransparencyLevel(clamped);
    }
    float getTransparencyLevel() const { return mTransparencyLevel.load(); }

    void setMicSensitivity(float sensitivity) { mMicSensitivity.store(std::clamp(sensitivity, 0.5f, 2.5f)); }
    float getMicSensitivity() const { return mMicSensitivity.load(); }

    void setPodMicRouting(int routing);

    int32_t getSampleRate() const;
    int32_t getBufferSizeFrames() const;
    bool isAAudio() const;

    // oboe::AudioStreamDataCallback interface
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream,
                                          void *audioData,
                                          int32_t numFrames) override;

    // oboe::AudioStreamErrorCallback interface
    void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    bool openStreams();
    void closeStreams();
    void updateFiltersSampleRate(float sampleRate);

    std::shared_ptr<oboe::AudioStream> mInputStream;
    std::shared_ptr<oboe::AudioStream> mOutputStream;

    std::atomic<bool> mIsRunning{false};
    std::atomic<float> mMasterGain{1.0f};
    std::atomic<float> mCurrentDb{35.0f};
    std::atomic<float> mPeakDb{35.0f};
    std::atomic<float> mLatencyMs{4.0f};
    std::atomic<float> mEarBalance{0.0f};
    std::atomic<float> mLeftGain{1.0f};
    std::atomic<float> mRightGain{1.0f};
    std::atomic<float> mTransparencyLevel{0.85f};
    std::atomic<float> mMicSensitivity{1.0f};

    // Equalizer & Filter Chain
    BiquadFilter mHighPass180Hz;
    BiquadFilter mLowPass5200Hz;
    std::vector<int> mEqFrequencies{250, 500, 1000, 2000, 4000, 8000};
    std::vector<BiquadFilter> mEqFilters;
    NoiseGate mNoiseGate;
    LookaheadLimiter mLimiter;

    std::vector<float> mInputBuffer;
    std::vector<int16_t> mInputShortBuffer;

    mutable std::mutex mWaveformMutex;
    float mWaveformBuffer[32] = {0.08f};
    int mWaveformIndex = 0;
    int mMeteringFrameCount = 0;

    float mSampleRate = 48000.0f;
    std::mutex mStreamLock;
};

} // namespace hearpod
