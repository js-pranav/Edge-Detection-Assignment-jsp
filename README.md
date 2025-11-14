# EdgeDetectorApp

A native-accelerated Android sample that streams live camera frames, runs OpenCV-based edge detection in C++, and overlays the processed output atop the raw preview.

## Features

- **Android**
  - Live camera preview via Camera2 API
  - JNI bridge to native C++ for OpenCV-powered Canny edge detection
  - UI overlay that toggles between raw and processed frames in real time
  - Performance counters for FPS, resolution, and processing latency
- **Web**
  - Lightweight documentation scaffolding for sharing build steps and feature details
  - (Pluggable) API surface prepared for future web visualizers that could stream processed frames

## Requirements
- Android Studio Giraffe (or newer)
- Android SDK 34 and Android NDK r25c+
- OpenCV Android SDK **4.5.x**
- Physical Android device (camera preview not supported in emulators)

## Project Structure
- `app/src/main/java/com/edgedetector` – Kotlin UI and Camera2 glue
- `app/src/main/cpp` – JNI bridge plus OpenCV edge processor
- `app/src/main/res` – Layout and strings
- `app/src/main/cpp/opencv-android-sdk` – **Place extracted OpenCV SDK here**

## Setup Instructions
1. **Install prerequisites**
   - Install SDK/NDK via Android Studio
   - Download OpenCV Android SDK 4.5.x from [opencv.org](https://opencv.org/releases/)
2. **Configure paths**
   - Copy `local.properties.example` → `local.properties`
   - Update it with your SDK/NDK locations, e.g.:
     ```
     sdk.dir=/Users/you/Library/Android/sdk
     ndk.dir=/Users/you/Library/Android/sdk/ndk/25.2.9519653
     ```
3. **Add OpenCV**
   - Unzip the OpenCV SDK inside `app/src/main/` so the folder `app/src/main/opencv-android-sdk` exists
   - Adjust `OpenCV_DIR` in `app/src/main/cpp/CMakeLists.txt` if you choose a different path

For web documentation or tooling contributions, install Node.js 18+, then run `npm install` and `npm run docs` (if you add a documentation site or companion viewer). This repo currently focuses on Android, but the same README is meant to serve both surfaces.

## Build & Run
```bash
cd /Users/jspranav/Downloads/EgdeDetectorApp
./gradlew assembleDebug
```
Install the generated APK (`app/build/outputs/apk/debug/app-debug.apk`) on a device, grant the camera permission, and tap **Show Processed** to toggle the overlay.

## Architecture Overview
1. **Camera2 frame flow**
   - `TextureView` exposes a `SurfaceTexture`, passed to `CameraManager`
   - `CameraManager` creates a `CaptureSession` with both preview surface and `ImageReader`
   - For every frame, `ImageReader` delivers YUV frames to a background thread
2. **JNI + native pipeline**
   - Kotlin code converts YUV_420_888 into NV21
   - JNI bridge (`NativeProcessor`) sends byte arrays to `native-lib`
   - C++ code invokes OpenCV: YUV→gray, blur, Canny, then writes ARGB output
   - Processed bytes return to Kotlin and populate a `Bitmap` shown in an overlay
3. **Web / TypeScript components (future-facing)**
   - Intended split-view architecture: Android streams frames, web client (React/Next) can subscribe via WebSocket or REST
   - TypeScript layer would manage:
     - Frame metadata rendering (FPS, thresholds)
     - Optional canvas-based visualization
   - Although not yet implemented, the README documents the hooks so a TypeScript companion can be added without changing native code

## Troubleshooting
- **CMake cannot find OpenCV**: verify `OpenCV_DIR` points at `sdk/native/jni`
- **UnsatisfiedLinkError**: confirm `native-lib` is built for your device ABI (check `abiFilters`)
- **Black screen**: ensure camera permission granted and test on a physical device

## Future Ideas
- Use GPU image processing via RenderScript or ML Kit
- Add controls for Canny thresholds
- Save processed frames to disk or share via intent

** Repo link: 


** Working sample images are included in the directory. 
