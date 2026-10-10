# Footprints

An Android application built from scratch with **Kotlin** and **Jetpack Compose**, targeting **Android 15 (API 35)**.

## Features

- **Minimalist Start Screen**: Begins with a clean screen featuring the blue paw footprint button.
- **Fine Location Capture**: Tapping the paw button queries high-accuracy GPS / Fused Location (`ACCESS_FINE_LOCATION`).
- **Persistent Logging**: Automatically appends each record to `/data/data/com.example.footprints/files/location_log.txt`.
- **Live Screen Output**: Displays timestamps, precise latitude & longitude coordinates, accuracy radius (meters), altitude, and location provider.

## How to Build & Run

### 1. Build Debug APK
```bash
./gradlew assembleDebug
```

### 2. View Live Logs & Logcat
```bash
adb logcat -s Footprints:V AndroidRuntime:E
```
