#include <jni.h>
#include "OboeAudioEngine.h"

using namespace hearpod;

extern "C" {

// ============================================================================
// com.example.audio.OboeAudioEngineJni dedicated JNI interface layer
// ============================================================================

JNIEXPORT jlong JNICALL
Java_com_example_audio_OboeAudioEngineJni_createEngine(JNIEnv *env, jobject thiz) {
    auto *engine = new(std::nothrow) OboeAudioEngine();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngineJni_destroyEngine(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    delete engine;
}

JNIEXPORT jboolean JNICALL
Java_com_example_audio_OboeAudioEngineJni_startEngine(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return JNI_FALSE;
    return engine->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngineJni_stopEngine(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) {
        engine->stop();
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_audio_OboeAudioEngineJni_isRunning(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return JNI_FALSE;
    return engine->isRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngineJni_setMasterGain(JNIEnv *env, jobject thiz, jlong engineHandle, jfloat gain) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) {
        engine->setMasterGain(gain);
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngineJni_setBandGain(JNIEnv *env, jobject thiz, jlong engineHandle, jint freq, jfloat gainDb) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) {
        engine->setBandGain(freq, gainDb);
    }
}

JNIEXPORT jfloat JNICALL
Java_com_example_audio_OboeAudioEngineJni_getBandGain(JNIEnv *env, jobject thiz, jlong engineHandle, jint freq) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return 0.0f;
    return engine->getBandGain(freq);
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngineJni_setAiNoiseMode(JNIEnv *env, jobject thiz, jlong engineHandle, jint mode) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) {
        engine->setAiNoiseMode(mode);
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngineJni_setFeedbackPrevention(JNIEnv *env, jobject thiz, jlong engineHandle, jboolean enabled) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) {
        engine->setFeedbackPrevention(enabled == JNI_TRUE);
    }
}

JNIEXPORT jfloat JNICALL
Java_com_example_audio_OboeAudioEngineJni_getCurrentDb(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return 30.0f;
    return engine->getCurrentDb();
}

JNIEXPORT jfloat JNICALL
Java_com_example_audio_OboeAudioEngineJni_getPeakDb(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return 30.0f;
    return engine->getPeakDb();
}

JNIEXPORT jfloat JNICALL
Java_com_example_audio_OboeAudioEngineJni_getLatencyMs(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return 4.0f;
    return engine->getLatencyMs();
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngineJni_getWaveform(JNIEnv *env, jobject thiz, jlong engineHandle, jfloatArray outArray) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine || !outArray) return;

    jsize len = env->GetArrayLength(outArray);
    if (len < 32) return;

    float buffer[32];
    engine->getWaveformSamples(buffer);
    env->SetFloatArrayRegion(outArray, 0, 32, buffer);
}

JNIEXPORT jint JNICALL
Java_com_example_audio_OboeAudioEngineJni_getSampleRate(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return 48000;
    return engine->getSampleRate();
}

JNIEXPORT jint JNICALL
Java_com_example_audio_OboeAudioEngineJni_getBufferSizeFrames(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return 0;
    return engine->getBufferSizeFrames();
}

JNIEXPORT jboolean JNICALL
Java_com_example_audio_OboeAudioEngineJni_isAAudio(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return JNI_FALSE;
    return engine->isAAudio() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngineJni_setEarBalance(JNIEnv *env, jobject thiz, jlong engineHandle, jfloat balance) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) engine->setEarBalance(balance);
}

JNIEXPORT jfloat JNICALL
Java_com_example_audio_OboeAudioEngineJni_getEarBalance(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return 0.0f;
    return engine->getEarBalance();
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngineJni_setLeftRightGains(JNIEnv *env, jobject thiz, jlong engineHandle, jfloat left, jfloat right) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) engine->setLeftRightGains(left, right);
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngineJni_setMicSensitivity(JNIEnv *env, jobject thiz, jlong engineHandle, jfloat sensitivity) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) engine->setMicSensitivity(sensitivity);
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngineJni_setTransparencyLevel(JNIEnv *env, jobject thiz, jlong engineHandle, jfloat level) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) engine->setTransparencyLevel(level);
}

JNIEXPORT jfloat JNICALL
Java_com_example_audio_OboeAudioEngineJni_getMicSensitivity(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return 1.0f;
    return engine->getMicSensitivity();
}

// ============================================================================
// Direct com.example.audio.OboeAudioEngine bindings (Backward compatibility)
// ============================================================================

JNIEXPORT jlong JNICALL
Java_com_example_audio_OboeAudioEngine_createEngine(JNIEnv *env, jobject thiz) {
    auto *engine = new(std::nothrow) OboeAudioEngine();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngine_destroyEngine(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    delete engine;
}

JNIEXPORT jboolean JNICALL
Java_com_example_audio_OboeAudioEngine_startEngine(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return JNI_FALSE;
    return engine->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngine_stopEngine(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) {
        engine->stop();
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_audio_OboeAudioEngine_isRunning(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return JNI_FALSE;
    return engine->isRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngine_setMasterGain(JNIEnv *env, jobject thiz, jlong engineHandle, jfloat gain) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) {
        engine->setMasterGain(gain);
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngine_setBandGain(JNIEnv *env, jobject thiz, jlong engineHandle, jint freq, jfloat gainDb) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) {
        engine->setBandGain(freq, gainDb);
    }
}

JNIEXPORT jfloat JNICALL
Java_com_example_audio_OboeAudioEngine_getBandGain(JNIEnv *env, jobject thiz, jlong engineHandle, jint freq) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return 0.0f;
    return engine->getBandGain(freq);
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngine_setAiNoiseMode(JNIEnv *env, jobject thiz, jlong engineHandle, jint mode) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) {
        engine->setAiNoiseMode(mode);
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngine_setFeedbackPrevention(JNIEnv *env, jobject thiz, jlong engineHandle, jboolean enabled) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) {
        engine->setFeedbackPrevention(enabled == JNI_TRUE);
    }
}

JNIEXPORT jfloat JNICALL
Java_com_example_audio_OboeAudioEngine_getCurrentDb(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return 30.0f;
    return engine->getCurrentDb();
}

JNIEXPORT jfloat JNICALL
Java_com_example_audio_OboeAudioEngine_getPeakDb(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return 30.0f;
    return engine->getPeakDb();
}

JNIEXPORT jfloat JNICALL
Java_com_example_audio_OboeAudioEngine_getLatencyMs(JNIEnv *env, jobject thiz, jlong engineHandle) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine) return 4.0f;
    return engine->getLatencyMs();
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngine_getWaveform(JNIEnv *env, jobject thiz, jlong engineHandle, jfloatArray outArray) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (!engine || !outArray) return;

    jsize len = env->GetArrayLength(outArray);
    if (len < 32) return;

    float buffer[32];
    engine->getWaveformSamples(buffer);
    env->SetFloatArrayRegion(outArray, 0, 32, buffer);
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngine_setLeftRightGains(JNIEnv *env, jobject thiz, jlong engineHandle, jfloat left, jfloat right) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) engine->setLeftRightGains(left, right);
}

JNIEXPORT void JNICALL
Java_com_example_audio_OboeAudioEngine_setTransparencyLevel(JNIEnv *env, jobject thiz, jlong engineHandle, jfloat level) {
    auto *engine = reinterpret_cast<OboeAudioEngine *>(engineHandle);
    if (engine) engine->setTransparencyLevel(level);
}

} // extern "C"
