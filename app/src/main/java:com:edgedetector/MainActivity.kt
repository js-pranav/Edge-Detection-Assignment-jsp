package com.edgedetector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.edgedetector.camera.CameraManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EdgeDetector"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var textureView: TextureView
    private lateinit var cameraManager: CameraManager
    private lateinit var fpsTextView: TextView
    private lateinit var resolutionTextView: TextView
    private lateinit var processingTimeTextView: TextView
    private lateinit var toggleButton: Button
    private lateinit var processedImageView: ImageView

    private var showProcessed = false
    private var isPermissionGranted = false
    private var frameCount = 0
    private var lastTimeMs = System.currentTimeMillis()
    private var currentFPS = 0
    private var processedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        initializePermissions()
    }

    private fun initializeViews() {
        textureView = findViewById(R.id.camera_preview)
        fpsTextView = findViewById(R.id.fps_counter)
        resolutionTextView = findViewById(R.id.resolution_info)
        processingTimeTextView = findViewById(R.id.processing_time)
        toggleButton = findViewById(R.id.toggle_processed_btn)
        processedImageView = findViewById(R.id.processed_overlay)
        processedImageView.visibility = View.GONE

        toggleButton.setOnClickListener {
            toggleProcessedView()
        }
    }

    private fun initializePermissions() {
        isPermissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!isPermissionGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                PERMISSION_REQUEST_CODE
            )
        } else {
            startCamera()
        }
    }

    private fun startCamera() {
        cameraManager = CameraManager(this)
        cameraManager.setProcessingMode(showProcessed)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                Log.d(TAG, "TextureView available: $width x $height")
                lifecycleScope.launch {
                    try {
                        cameraManager.openCamera(surface, width, height) { processingTimeMs, processedData, frameWidth, frameHeight ->
                            updateFPS()
                            updateUI(processingTimeMs, frameWidth, frameHeight)
                            updateProcessedFrame(processedData, frameWidth, frameHeight)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening camera: ${e.message}", e)
                    }
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                cameraManager.closeCamera()
                return true
            }

            override fun onSurfaceTextureFrameAvailable(surface: android.graphics.SurfaceTexture) {}
        }
    }

    private fun updateFPS() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastTimeMs

        if (elapsed >= 1000) {
            currentFPS = frameCount
            frameCount = 0
            lastTimeMs = currentTime
        }
    }

    private fun updateUI(processingTimeMs: Long, frameWidth: Int, frameHeight: Int) {
        runOnUiThread {
            fpsTextView.text = "FPS: $currentFPS"
            resolutionTextView.text = "$frameWidth × $frameHeight"
            processingTimeTextView.text = "Processing: ${processingTimeMs}ms"
        }
    }

    private fun updateProcessedFrame(processedData: ByteArray?, width: Int, height: Int) {
        if (!showProcessed || processedData == null) {
            return
        }

        if (processedBitmap == null || processedBitmap?.width != width || processedBitmap?.height != height) {
            processedBitmap?.recycle()
            processedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

        processedBitmap?.let { bitmap ->
            val buffer = ByteBuffer.wrap(processedData)
            bitmap.copyPixelsFromBuffer(buffer)
            runOnUiThread {
                processedImageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun toggleProcessedView() {
        showProcessed = !showProcessed
        toggleButton.text = if (showProcessed) "Show Raw" else "Show Processed"
        processedImageView.visibility = if (showProcessed) View.VISIBLE else View.GONE
        if (!showProcessed) {
            processedImageView.setImageDrawable(null)
        }
        if (::cameraManager.isInitialized) {
            cameraManager.setProcessingMode(showProcessed)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                isPermissionGranted = true
                startCamera()
            }
        }
    }

    override fun onDestroy() {
        if (::cameraManager.isInitialized) {
            cameraManager.closeCamera()
        }
        processedBitmap?.recycle()
        processedBitmap = null
        super.onDestroy()
    }
}
