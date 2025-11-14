package com.edgedetector.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager as AndroidCameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.edgedetector.jni.NativeProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraManager"
    }

    private val androidCameraManager: AndroidCameraManager = context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null
    private var lastProcessingTimeMs = 0L
    private var useProcessing = false
    private var processingCallback: ((Long, ByteArray?, Int, Int) -> Unit)? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    suspend fun openCamera(
        surfaceTexture: SurfaceTexture,
        previewWidth: Int,
        previewHeight: Int,
        callback: (Long, ByteArray?, Int, Int) -> Unit
    ) = withContext(Dispatchers.Main) {
        processingCallback = callback
        startBackgroundThread()
        try {
            val cameraId = withContext(Dispatchers.Default) { getBackCameraId() }
            if (cameraId != null) {
                configureAndOpenCamera(cameraId, surfaceTexture, previewWidth, previewHeight)
            } else {
                Log.e(TAG, "No back-facing camera found.")
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access exception: ${e.message}", e)
        }
    }

    private fun configureAndOpenCamera(
        cameraId: String,
        surfaceTexture: SurfaceTexture,
        previewWidth: Int,
        previewHeight: Int
    ) {
        try {
            val characteristics = androidCameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return

            val supportedSizes = map.getOutputSizes(ImageFormat.YUV_420_888)
            val selectedSize = supportedSizes.minByOrNull {
                kotlin.math.abs(it.width - previewWidth) + kotlin.math.abs(it.height - previewHeight)
            } ?: supportedSizes[0]

            surfaceTexture.setDefaultBufferSize(selectedSize.width, selectedSize.height)

            imageReader = ImageReader.newInstance(
                selectedSize.width,
                selectedSize.height,
                ImageFormat.YUV_420_888,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val startTime = System.currentTimeMillis()
                    val result = processFrame(image)
                    lastProcessingTimeMs = System.currentTimeMillis() - startTime
                    processingCallback?.invoke(
                        lastProcessingTimeMs,
                        result.processedData,
                        result.width,
                        result.height
                    )
                }, backgroundHandler)
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                androidCameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        cameraDevice = device
                        createCaptureSession(device, surfaceTexture, selectedSize.width, selectedSize.height)
                    }

                    override fun onDisconnected(device: CameraDevice) {
                        device.close()
                        cameraDevice = null
                    }

                    override fun onError(device: CameraDevice, error: Int) {
                        device.close()
                        cameraDevice = null
                        Log.e(TAG, "Camera error: $error")
                    }
                }, Handler(Looper.getMainLooper()))
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access error: ${e.message}", e)
        }
    }

    private fun createCaptureSession(device: CameraDevice, surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        try {
            val previewSurface = android.view.Surface(surfaceTexture)
            val readerSurface = imageReader?.surface ?: return

            val targets = listOf(previewSurface, readerSurface)
            val captureRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            targets.forEach { captureRequestBuilder.addTarget(it) }

            device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        session.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Error setting capture request: ${e.message}", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Capture session configuration failed")
                }
            }, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error creating capture session: ${e.message}", e)
        }
    }

    private data class FrameProcessingResult(
        val processedData: ByteArray?,
        val width: Int,
        val height: Int
    )

    private fun processFrame(image: Image): FrameProcessingResult {
        val width = image.width
        val height = image.height
        var processedData: ByteArray? = null
        try {
            if (useProcessing) {
                val nv21Data = convertYuv420ToNV21(image)
                processedData = NativeProcessor.processEdges(nv21Data, width, height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame: ${e.message}", e)
        } finally {
            image.close()
        }
        return FrameProcessingResult(processedData, width, height)
    }

    private fun convertYuv420ToNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        copyLumaPlane(yPlane, width, height, nv21)
        copyChromaPlanes(uPlane, vPlane, width, height, nv21, ySize)

        return nv21
    }

    private fun copyLumaPlane(plane: Image.Plane, width: Int, height: Int, out: ByteArray) {
        val buffer = plane.buffer
        buffer.rewind()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        if (pixelStride == 1 && rowStride == width) {
            buffer.get(out, 0, width * height)
            return
        }

        var outputOffset = 0
        for (row in 0 until height) {
            var inputOffset = row * rowStride
            for (col in 0 until width) {
                out[outputOffset++] = buffer.get(inputOffset)
                inputOffset += pixelStride
            }
        }
    }

    private fun copyChromaPlanes(
        uPlane: Image.Plane,
        vPlane: Image.Plane,
        width: Int,
        height: Int,
        out: ByteArray,
        offset: Int
    ) {
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        uBuffer.rewind()
        vBuffer.rewind()

        val rowStrideU = uPlane.rowStride
        val rowStrideV = vPlane.rowStride
        val pixelStrideU = uPlane.pixelStride
        val pixelStrideV = vPlane.pixelStride

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        var outputOffset = offset

        for (row in 0 until chromaHeight) {
            var uInputOffset = row * rowStrideU
            var vInputOffset = row * rowStrideV
            for (col in 0 until chromaWidth) {
                out[outputOffset++] = vBuffer.get(vInputOffset)
                out[outputOffset++] = uBuffer.get(uInputOffset)
                vInputOffset += pixelStrideV
                uInputOffset += pixelStrideU
            }
        }
    }

    private fun getBackCameraId(): String? {
        return try {
            androidCameraManager.cameraIdList.firstOrNull { cameraId ->
                val characteristics = androidCameraManager.getCameraCharacteristics(cameraId)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error getting camera IDs: ${e.message}", e)
            null
        }
    }

    fun setProcessingMode(enabled: Boolean) {
        useProcessing = enabled
    }

    fun closeCamera() {
        try {
            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera: ${e.message}", e)
        } finally {
            stopBackgroundThread()
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread != null) return
        backgroundThread = HandlerThread("EdgeDetectorCamera").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread: ${e.message}", e)
        }
        backgroundThread = null
        backgroundHandler = null
    }
}
