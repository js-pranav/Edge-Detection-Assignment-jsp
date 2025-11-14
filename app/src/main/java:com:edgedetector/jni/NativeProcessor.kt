package com.edgedetector.jni

object NativeProcessor {
    init {
        System.loadLibrary("native-lib")
    }

    external fun processEdges(imageData: ByteArray, width: Int, height: Int): ByteArray?
}
