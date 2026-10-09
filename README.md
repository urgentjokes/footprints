# LocationLogger

An Android application built from scratch with **Kotlin** and **Jetpack Compose**, targeting **Android 15 (API 35)**.

## Features

- **Minimalist Start Screen**: Begins with a completely blank screen featuring only a filled-circle plus icon (`+`).
- **Fine Location Capture**: Tapping the plus icon queries high-accuracy GPS / Fused Location (`ACCESS_FINE_LOCATION`).
- **Persistent Logging**: Automatically appends each record to `/data/data/com.example.locationlogger/files/location_log.txt`.
- **Live Screen Output**: Displays timestamps, precise latitude & longitude coordinates, accuracy radius (meters), altitude, and location provider.

## How to Build & Run

### 1. Build Debug APK
```bash
cd /root/projects/LocationLogger
./gradlew assembleDebug
```

### 2. Deploy to Tablet
```bash
deploy-app LocationLogger
```

### 3. View Live Logs & Logcat
```bash
adb logcat -s LocationLogger:V AndroidRuntime:E
```
