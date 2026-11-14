#include "OboeAudioEngine.h"
#include <android/log.h>
#include <cmath>
#include <cstring>

#define TAG "HearPodOboeEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace hearpod {

OboeAudioEngine::OboeAudioEngine() {
    mInputBuffer.resize(2048, 0.0f);
    mEqFilters.resize(mEqFrequencies.size());
    updateFiltersSampleRate(mSampleRate);

    // Crystal-clear Speech Clarity curve: boosted speech presence, natural warmth
    setBandGain(250, 0.0f);
    setBandGain(500, 1.0f);
    setBandGain(1000, 3.0f);
    setBandGain(2000, 5.0f);
    setBandGain(4000, 5.0f);
    setBandGain(8000, 3.0f);
    mMasterGain.store(1.0f);

    for (int i = 0; i < 32; ++i) {
        mWaveformBuffer[i] = 0.08f;
    }
}

OboeAudioEngine::~OboeAudioEngine() {
    stop();
}

void OboeAudioEngine::updateFiltersSampleRate(float sampleRate) {
    mSampleRate = sampleRate;
    mHighPass180Hz.configure(BiquadFilter::Type::HighPass, 75.0f, sampleRate);
    mLowPass5200Hz.configure(BiquadFilter::Type::LowPass, 14000.0f, sampleRate);

    for (size_t i = 0; i < mEqFrequencies.size(); ++i) {
        float gain = mEqFilters[i].getGain();
        mEqFilters[i].configure(BiquadFilter::Type::PeakingEQ, static_cast<float>(mEqFrequencies[i]), sampleRate, gain);
    }
    mNoiseGate.configure(sampleRate);
}

bool OboeAudioEngine::openStreams() {
    std::lock_guard<std::mutex> lock(mStreamLock);

    // 1. Configure and open Input Stream (Microphone / Earpods Mic)
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
             ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
             ->setSharingMode(oboe::SharingMode::Shared)
             ->setFormat(oboe::AudioFormat::Float)
             ->setChannelCount(oboe::ChannelCount::Mono)
             ->setInputPreset(oboe::InputPreset::VoiceRecognition);

    oboe::Result inResult = inBuilder.openStream(mInputStream);
    if (inResult != oboe::Result::OK) {
        LOGW("Failed with VoiceRecognition preset, falling back to Generic/Default: %s", oboe::convertToText(inResult));
        inBuilder.setInputPreset(oboe::InputPreset::Generic);
        inResult = inBuilder.openStream(mInputStream);
    }
    if (inResult != oboe::Result::OK) {
        LOGW("Failed with Float format, falling back to I16 input: %s", oboe::convertToText(inResult));
        inBuilder.setFormat(oboe::AudioFormat::I16);
        inResult = inBuilder.openStream(mInputStream);
    }
    if (inResult != oboe::Result::OK) {
        LOGW("Failed with LowLatency, falling back to None input: %s", oboe::convertToText(inResult));
        inBuilder.setPerformanceMode(oboe::PerformanceMode::None);
        inResult = inBuilder.openStream(mInputStream);
    }

    if (inResult != oboe::Result::OK || !mInputStream) {
        LOGE("Failed to open input stream: %s", oboe::convertToText(inResult));
        return false;
    }

    int32_t actualSampleRate = mInputStream->getSampleRate();
    LOGI("Opened Oboe input stream: %d Hz, format=%d, bufferSize=%d",
         actualSampleRate, static_cast<int>(mInputStream->getFormat()), mInputStream->getBufferSizeInFrames());

    updateFiltersSampleRate(static_cast<float>(actualSampleRate));

    // 2. Configure and open Output Stream (Earpods Playback)
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
              ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
              ->setSharingMode(oboe::SharingMode::Shared)
              ->setFormat(oboe::AudioFormat::Float)
              ->setChannelCount(oboe::ChannelCount::Stereo)
              ->setUsage(oboe::Usage::Media)
              ->setContentType(oboe::ContentType::Speech)
              ->setSampleRate(actualSampleRate)
              ->setDataCallback(this)
              ->setErrorCallback(this);

    oboe::Result outResult = outBuilder.openStream(mOutputStream);
    if (outResult != oboe::Result::OK) {
        LOGW("Failed to open stereo output stream, falling back to mono: %s", oboe::convertToText(outResult));
        outBuilder.setChannelCount(oboe::ChannelCount::Mono);
        outResult = outBuilder.openStream(mOutputStream);
    }
    if (outResult != oboe::Result::OK) {
        LOGW("Failed with Float output, falling back to I16: %s", oboe::convertToText(outResult));
        outBuilder.setFormat(oboe::AudioFormat::I16);
        outResult = outBuilder.openStream(mOutputStream);
    }

    if (outResult != oboe::Result::OK || !mOutputStream) {
        LOGE("Failed to open output stream: %s", oboe::convertToText(outResult));
        mInputStream->close();
        mInputStream.reset();
        return false;
    }

    LOGI("Opened Oboe output stream: %d Hz, format=%d, bufferSize=%d",
         mOutputStream->getSampleRate(), static_cast<int>(mOutputStream->getFormat()), mOutputStream->getBufferSizeInFrames());

    return true;
}

void OboeAudioEngine::closeStreams() {
    std::lock_guard<std::mutex> lock(mStreamLock);

    if (mOutputStream) {
        mOutputStream->stop();
        mOutputStream->close();
        mOutputStream.reset();
    }

    if (mInputStream) {
        mInputStream->stop();
        mInputStream->close();
        mInputStream.reset();
    }
}

bool OboeAudioEngine::start() {
    if (mIsRunning.load() && mOutputStream && mInputStream) return true;

    // Reset running state before reopening
    mIsRunning.store(false);
    closeStreams();

    if (!openStreams()) {
        return false;
    }

    // Flush any stale startup audio from the microphone
    if (mInputStream) {
        oboe::Result result = mInputStream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Failed to start input stream: %s", oboe::convertToText(result));
            closeStreams();
            return false;
        }

        // Drain up to 1024 frames of startup silence / historical noise safely
        float drainBuffer[256];
        int drainCount = 0;
        while (drainCount < 4) {
            auto res = mInputStream->read(drainBuffer, 256, 0);
            if (res == oboe::Result::OK && res.value() > 0) {
                drainCount++;
            } else {
                break;
            }
        }
    }

    if (mOutputStream) {
        oboe::Result result = mOutputStream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Failed to start output stream: %s", oboe::convertToText(result));
            closeStreams();
            return false;
        }
    }

    mIsRunning.store(true);
    LOGI("OboeAudioEngine started successfully with low latency");
    return true;
}

void OboeAudioEngine::stop() {
    mIsRunning.store(false);
    closeStreams();
    mNoiseGate.reset();
    mLimiter.reset();
    mHighPass180Hz.reset();
    mLowPass5200Hz.reset();
    for (auto& f : mEqFilters) {
        f.reset();
    }
    LOGI("OboeAudioEngine stopped");
}

oboe::DataCallbackResult OboeAudioEngine::onAudioReady(oboe::AudioStream *oboeStream,
                                                      void *audioData,
                                                      int32_t numFrames) {
    if (!mIsRunning.load() || numFrames <= 0) {
        return oboe::DataCallbackResult::Continue;
    }

    if (mInputBuffer.size() < static_cast<size_t>(numFrames)) {
        mInputBuffer.resize(numFrames * 2, 0.0f);
    }

    int32_t framesRead = 0;
    if (mInputStream) {
        if (mInputStream->getFormat() == oboe::AudioFormat::I16) {
            if (mInputShortBuffer.size() < static_cast<size_t>(numFrames)) {
                mInputShortBuffer.resize(numFrames * 2, 0);
            }
            auto result = mInputStream->read(mInputShortBuffer.data(), numFrames, 2500000ULL);
            if (result == oboe::Result::OK && result.value() > 0) {
                framesRead = result.value();
                for (int32_t i = 0; i < framesRead; ++i) {
                    mInputBuffer[i] = mInputShortBuffer[i] / 32768.0f;
                }
            }
        } else {
            auto result = mInputStream->read(mInputBuffer.data(), numFrames, 2500000ULL);
            if (result == oboe::Result::OK && result.value() > 0) {
                framesRead = result.value();
            }
        }
    }

    // Smoothly pad if input produced fewer frames than output requested
    if (framesRead < numFrames) {
        if (framesRead > 0) {
            float lastVal = mInputBuffer[framesRead - 1];
            for (int32_t i = framesRead; i < numFrames; ++i) {
                mInputBuffer[i] = lastVal * 0.95f;
            }
        } else {
            std::fill(mInputBuffer.begin(), mInputBuffer.begin() + numFrames, 0.0f);
        }
    }

    float micSensitivity = mMicSensitivity.load();
    if (std::abs(micSensitivity - 1.0f) > 0.01f) {
        for (int32_t i = 0; i < numFrames; ++i) {
            mInputBuffer[i] *= micSensitivity;
        }
    }

    // 1. High-Pass (180 Hz) to eliminate AC hum and mechanical bumps
    for (int32_t i = 0; i < numFrames; ++i) {
        mInputBuffer[i] = mHighPass180Hz.process(mInputBuffer[i]);
    }

    // 2. Low-Pass (5200 Hz) to eliminate microphone thermal white noise and hiss
    for (int32_t i = 0; i < numFrames; ++i) {
        mInputBuffer[i] = mLowPass5200Hz.process(mInputBuffer[i]);
    }

    // 3. AI Downward Expander Noise Gate
    mNoiseGate.process(mInputBuffer.data(), numFrames);

    // 4. Multi-band Equalizer
    for (auto& filter : mEqFilters) {
        for (int32_t i = 0; i < numFrames; ++i) {
            mInputBuffer[i] = filter.process(mInputBuffer[i]);
        }
    }

    // 5. Speech Clarifier & Linear Volume Control
    float masterGain = mMasterGain.load();
    float vocalGain = masterGain * 1.35f;

    for (int32_t i = 0; i < numFrames; ++i) {
        float sample = mInputBuffer[i];
        float absSample = std::abs(sample);

        if (absSample > 0.0020f) {
            float sign = (sample >= 0.0f) ? 1.0f : -1.0f;
            float vocalLift = 1.0f + 0.65f * std::clamp(1.0f - (absSample / 0.20f), 0.0f, 1.0f);
            float amplified = sign * absSample * vocalGain * vocalLift;

            // Soft-knee analog-style saturation prevents harsh digital clipping
            if (amplified > 0.88f) {
                amplified = 0.88f + 0.12f * ((amplified - 0.88f) / (1.0f + (amplified - 0.88f)));
            } else if (amplified < -0.88f) {
                amplified = -0.88f - 0.12f * ((-amplified - 0.88f) / (1.0f + (-amplified - 0.88f)));
            }

            sample = std::clamp(amplified, -0.98f, 0.98f);
        } else {
            // Ambient pause: attenuate electronic mic hiss by -12dB
            sample *= 0.25f;
        }

        mInputBuffer[i] = sample;
    }

    // 6. Lookahead Soft Limiter
    mLimiter.process(mInputBuffer.data(), numFrames);

    double sumSquares = 0.0;
    float peakAmp = 0.0f;

    int32_t channelCount = mOutputStream ? mOutputStream->getChannelCount() : 1;
    float earBalance = mEarBalance.load();
    float balanceLeft = (earBalance <= 0.0f) ? 1.0f : (1.0f - earBalance);
    float balanceRight = (earBalance >= 0.0f) ? 1.0f : (1.0f + earBalance);
    float leftGain = balanceLeft * mLeftGain.load();
    float rightGain = balanceRight * mRightGain.load();

    bool isOutputI16 = (mOutputStream && mOutputStream->getFormat() == oboe::AudioFormat::I16);
    int16_t* out16 = isOutputI16 ? static_cast<int16_t*>(audioData) : nullptr;
    float* outFloat = !isOutputI16 ? static_cast<float*>(audioData) : nullptr;

    for (int32_t i = 0; i < numFrames; ++i) {
        float sample = mInputBuffer[i];
        sample = std::clamp(sample, -1.0f, 1.0f);

        if (isOutputI16) {
            if (channelCount == 2) {
                out16[i * 2] = static_cast<int16_t>(std::clamp(sample * leftGain * 32767.0f, -32768.0f, 32767.0f));
                out16[i * 2 + 1] = static_cast<int16_t>(std::clamp(sample * rightGain * 32767.0f, -32768.0f, 32767.0f));
            } else {
                out16[i] = static_cast<int16_t>(std::clamp(sample * 32767.0f, -32768.0f, 32767.0f));
            }
        } else {
            if (channelCount == 2) {
                outFloat[i * 2] = std::clamp(sample * leftGain, -1.0f, 1.0f);
                outFloat[i * 2 + 1] = std::clamp(sample * rightGain, -1.0f, 1.0f);
            } else {
                outFloat[i] = sample;
            }
        }

        sumSquares += sample * sample;
        float absS = std::abs(sample);
        if (absS > peakAmp) peakAmp = absS;
    }

    // Update real-time metering at ~30 FPS
    mMeteringFrameCount++;
    if (mMeteringFrameCount % 6 == 0) {
        float rms = static_cast<float>(std::sqrt(sumSquares / numFrames));
        float db = std::clamp(30.0f + 20.0f * std::log10(std::max(rms, 0.0001f) * 10.0f + 1.0f), 25.0f, 98.0f);
        float currentPeak = std::clamp(30.0f + 20.0f * std::log10(std::max(peakAmp, 0.0001f) * 10.0f + 1.0f), 25.0f, 98.0f);

        mCurrentDb.store(db);
        mPeakDb.store(currentPeak);

        // Estimate end-to-end hardware latency
        if (mOutputStream) {
            auto latencyResult = mOutputStream->calculateLatencyMillis();
            if (latencyResult == oboe::Result::OK) {
                mLatencyMs.store(static_cast<float>(latencyResult.value()));
            } else {
                float framesDelay = static_cast<float>(numFrames) / mSampleRate * 1000.0f;
                mLatencyMs.store(framesDelay);
            }
        }

        {
            std::lock_guard<std::mutex> lock(mWaveformMutex);
            mWaveformBuffer[mWaveformIndex] = std::clamp(rms * 4.5f, 0.06f, 1.0f);
            mWaveformIndex = (mWaveformIndex + 1) % 32;
        }
    }

    return oboe::DataCallbackResult::Continue;
}

void OboeAudioEngine::onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGW("Oboe stream error before close: %s", oboe::convertToText(error));
}

void OboeAudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGW("Oboe stream error after close: %s. Releasing stream state.", oboe::convertToText(error));
    mIsRunning.store(false);
    closeStreams();
}

void OboeAudioEngine::setMasterGain(float gain) {
    mMasterGain.store(std::clamp(gain, 0.5f, 8.0f));
}

void OboeAudioEngine::setBandGain(int frequencyHz, float gainDb) {
    for (size_t i = 0; i < mEqFrequencies.size(); ++i) {
        if (mEqFrequencies[i] == frequencyHz) {
            mEqFilters[i].setGain(std::clamp(gainDb, -12.0f, 18.0f));
            break;
        }
    }
}

float OboeAudioEngine::getBandGain(int frequencyHz) const {
    for (size_t i = 0; i < mEqFrequencies.size(); ++i) {
        if (mEqFrequencies[i] == frequencyHz) {
            return mEqFilters[i].getGain();
        }
    }
    return 0.0f;
}

void OboeAudioEngine::setAiNoiseMode(int mode) {
    mNoiseGate.setMode(static_cast<AiNoiseMode>(mode));
}

int OboeAudioEngine::getAiNoiseMode() const {
    return static_cast<int>(mNoiseGate.getMode());
}

void OboeAudioEngine::setFeedbackPrevention(bool enabled) {
    mLimiter.setFeedbackPrevention(enabled);
}

bool OboeAudioEngine::isFeedbackPreventionEnabled() const {
    return mLimiter.isFeedbackPreventionEnabled();
}

void OboeAudioEngine::getWaveformSamples(float* out32Samples) const {
    if (!out32Samples) return;
    std::lock_guard<std::mutex> lock(mWaveformMutex);
    for (int i = 0; i < 32; ++i) {
        out32Samples[i] = mWaveformBuffer[i];
    }
}

int32_t OboeAudioEngine::getSampleRate() const {
    return static_cast<int32_t>(mSampleRate);
}

int32_t OboeAudioEngine::getBufferSizeFrames() const {
    if (mOutputStream) {
        return mOutputStream->getBufferSizeInFrames();
    }
    return 0;
}

bool OboeAudioEngine::isAAudio() const {
    if (mOutputStream) {
        return mOutputStream->getAudioApi() == oboe::AudioApi::AAudio;
    }
    return false;
}

void OboeAudioEngine::setPodMicRouting(int routing) {
    // 0: DUAL_POD_BEAMFORMING, 1: LEFT_POD_ONLY, 2: RIGHT_POD_ONLY, 3: PHONE_DIRECTIONAL
    switch (routing) {
        case 1: // LEFT_POD_ONLY
            mLeftGain.store(1.35f);
            mRightGain.store(0.40f);
            mTransparencyLevel.store(0.90f);
            break;
        case 2: // RIGHT_POD_ONLY
            mLeftGain.store(0.40f);
            mRightGain.store(1.35f);
            mTransparencyLevel.store(0.90f);
            break;
        case 0: // DUAL_POD_BEAMFORMING
            mLeftGain.store(1.15f);
            mRightGain.store(1.15f);
            mTransparencyLevel.store(0.95f);
            break;
        case 3: // PHONE_DIRECTIONAL
        default:
            mLeftGain.store(1.0f);
            mRightGain.store(1.0f);
            mTransparencyLevel.store(0.85f);
            break;
    }
}

} // namespace hearpod
