#include <jni.h>
#include <android/log.h>
#include "opencv-processor.h"

#define LOG_TAG "EdgeDetector"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jbyteArray JNICALL Java_com_edgedetector_jni_NativeProcessor_processEdges(
    JNIEnv *env, jclass clazz, jbyteArray imageData, jint width, jint height) {

    try {
        jbyte *data = env->GetByteArrayElements(imageData, nullptr);
        int dataSize = env->GetArrayLength(imageData);

        if (!data || width <= 0 || height <= 0) {
            LOGE("Invalid parameters");
            env->ReleaseByteArrayElements(imageData, data, JNI_ABORT);
            return nullptr;
        }

        std::vector<uint8_t> processedData = detectEdges(
            reinterpret_cast<uint8_t *>(data), width, height);

        jbyteArray result = env->NewByteArray(processedData.size());
        env->SetByteArrayRegion(result, 0, processedData.size(),
                               reinterpret_cast<const jbyte *>(processedData.data()));

        env->ReleaseByteArrayElements(imageData, data, JNI_ABORT);
        return result;

    } catch (const std::exception &e) {
        LOGE("Exception in processEdges: %s", e.what());
        return nullptr;
    }
}

}