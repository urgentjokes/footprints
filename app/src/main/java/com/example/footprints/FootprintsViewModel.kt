package com.example.footprints

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FootprintsViewModel : ViewModel() {
    companion object {
        const val PREFS_NAME = "footprints_settings"
        const val KEY_SHOW_MAP_IMAGE = "show_map_image"
        const val KEY_AUTO_CAPTURE_LOCATION = "auto_capture_location"
        const val KEY_AUTO_CAPTURE_INTERVAL_MINUTES = "auto_capture_interval_minutes"
        const val KEY_AUTO_SEND_LOCATION = "auto_send_location"
        const val KEY_AUTO_SEND_INTERVAL_MINUTES = "auto_send_interval_minutes"
        const val KEY_RUN_IN_BACKGROUND = "run_in_background"
        const val KEY_BLUETOOTH_SHUTTER = "bluetooth_shutter"
    }

    val records get() = FootprintsRepository.records
    var isLoading by mutableStateOf(false)
    var statusMessage by mutableStateOf<String?>(null)
    var showMapImage by mutableStateOf(true)
    var autoCaptureLocation by mutableStateOf(false)
    var runInBackground by mutableStateOf(false)
    var listenForBluetoothShutter by mutableStateOf(true)
    var lastRecordedFromShutter by mutableStateOf(false)
    var autoCaptureIntervalText by mutableStateOf("15")

    var autoSendLocation: Boolean
        get() = autoCaptureLocation
        set(value) { autoCaptureLocation = value }

    var autoSendIntervalText: String
        get() = autoCaptureIntervalText
        set(value) { autoCaptureIntervalText = value }

    private var autoCaptureJob: Job? = null

    fun initSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        showMapImage = prefs.getBoolean(KEY_SHOW_MAP_IMAGE, true)
        autoCaptureLocation = prefs.getBoolean(KEY_AUTO_CAPTURE_LOCATION, prefs.getBoolean(KEY_AUTO_SEND_LOCATION, false))
        runInBackground = prefs.getBoolean(KEY_RUN_IN_BACKGROUND, false)
        listenForBluetoothShutter = prefs.getBoolean(KEY_BLUETOOTH_SHUTTER, true)
        autoCaptureIntervalText = prefs.getString(KEY_AUTO_CAPTURE_INTERVAL_MINUTES, prefs.getString(KEY_AUTO_SEND_INTERVAL_MINUTES, "15")) ?: "15"
        if (autoCaptureLocation) {
            if (runInBackground) {
                LocationTrackingService.start(context.applicationContext, immediateCapture = false)
            } else {
                startInAppAutoCaptureLoop(context.applicationContext, immediateCapture = false)
            }
        }
    }

    fun setListenForBluetoothShutter(context: Context, enabled: Boolean) {
        listenForBluetoothShutter = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BLUETOOTH_SHUTTER, enabled).apply()
        val statusText = if (enabled) "Bluetooth shutter listening enabled" else "Bluetooth shutter listening disabled"
        Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
    }

    fun recordShutterLocation(context: Context) {
        lastRecordedFromShutter = true
        recordCurrentLocation(context)
    }

    fun setShowMapImage(context: Context, enabled: Boolean) {
        showMapImage = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_MAP_IMAGE, enabled).apply()
    }

    fun setRunInBackground(context: Context, enabled: Boolean) {
        runInBackground = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_RUN_IN_BACKGROUND, enabled).apply()
        if (autoCaptureLocation) {
            if (enabled) {
                stopInAppAutoCaptureLoop()
                LocationTrackingService.start(context.applicationContext, immediateCapture = false)
                Toast.makeText(context, "Background tracking enabled (foreground service)", Toast.LENGTH_SHORT).show()
            } else {
                LocationTrackingService.stop(context.applicationContext)
                startInAppAutoCaptureLoop(context.applicationContext, immediateCapture = false)
                Toast.makeText(context, "Background tracking disabled (in-app only)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setAutoCaptureLocation(context: Context, enabled: Boolean) {
        autoCaptureLocation = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AUTO_CAPTURE_LOCATION, enabled)
            .putBoolean(KEY_AUTO_SEND_LOCATION, enabled)
            .apply()
        if (enabled) {
            if (runInBackground) {
                stopInAppAutoCaptureLoop()
                LocationTrackingService.start(context.applicationContext, immediateCapture = true)
            } else {
                LocationTrackingService.stop(context.applicationContext)
                startInAppAutoCaptureLoop(context.applicationContext, immediateCapture = true)
            }
        } else {
            stopInAppAutoCaptureLoop(context)
            LocationTrackingService.stop(context.applicationContext)
        }
    }

    fun setAutoSendLocation(context: Context, enabled: Boolean) = setAutoCaptureLocation(context, enabled)

    val autoCaptureIntervalMinutes: Double
        get() = autoCaptureIntervalText.toDoubleOrNull() ?: 15.0

    val autoSendIntervalMinutes: Double
        get() = autoCaptureIntervalMinutes

    fun setAutoCaptureIntervalText(context: Context, text: String) {
        val normalized = text.replace(',', '.')
        val cleaned = buildString {
            var hasDot = false
            for (ch in normalized) {
                if (ch.isDigit()) {
                    append(ch)
                } else if (ch == '.' && !hasDot) {
                    append(ch)
                    hasDot = true
                }
            }
        }.take(6)
        autoCaptureIntervalText = cleaned
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_AUTO_CAPTURE_INTERVAL_MINUTES, cleaned)
            .putString(KEY_AUTO_SEND_INTERVAL_MINUTES, cleaned)
            .apply()
        if (autoCaptureLocation) {
            if (runInBackground) {
                LocationTrackingService.start(context.applicationContext, immediateCapture = false)
            } else {
                startInAppAutoCaptureLoop(context.applicationContext, immediateCapture = false)
            }
        }
    }

    fun setAutoSendIntervalText(context: Context, text: String) = setAutoCaptureIntervalText(context, text)

    fun startInAppAutoCaptureLoop(context: Context, immediateCapture: Boolean = true) {
        autoCaptureJob?.cancel()
        val appContext = context.applicationContext
        autoCaptureJob = viewModelScope.launch {
            val mins = autoCaptureIntervalMinutes
            val intervalDesc = if (mins < 1.0) {
                val secs = (mins * 60).toInt()
                if (secs == 1) "1 second" else "$secs seconds"
            } else {
                "$mins min"
            }
            Toast.makeText(appContext, "Auto-capture active (every $intervalDesc)", Toast.LENGTH_SHORT).show()

            if (immediateCapture) {
                performAutoCapture(appContext)
            }

            while (isActive && autoCaptureLocation) {
                val currentMillis = (autoCaptureIntervalMinutes * 60.0 * 1000.0).toLong().coerceAtLeast(3000L)
                delay(currentMillis)
                if (isActive && autoCaptureLocation) {
                    performAutoCapture(appContext)
                }
            }
        }
    }

    fun startAutoCaptureLoop(context: Context, immediateCapture: Boolean = true) =
        startInAppAutoCaptureLoop(context, immediateCapture)

    fun stopInAppAutoCaptureLoop(context: Context? = null) {
        autoCaptureJob?.cancel()
        autoCaptureJob = null
        if (context != null) {
            Toast.makeText(context, "Auto-capture turned off", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAutoCaptureLoop(context: Context? = null) = stopInAppAutoCaptureLoop(context)

    private suspend fun performAutoCapture(context: Context) {
        val record = suspendCancellableCoroutine<LocationRecord?> { cont ->
            LocationHelper.fetchCurrentFineLocation(
                context = context,
                onSuccess = { rec ->
                    if (cont.isActive) cont.resume(rec)
                },
                onError = { _ ->
                    if (cont.isActive) cont.resume(null)
                }
            )
        } ?: return

        val finalRecord = if (showMapImage) {
            val savedImagePath = try {
                MapHelper.downloadMapImage(context, record.latitude, record.longitude, record.id)
            } catch (_: Exception) { null }
            if (savedImagePath != null) record.copy(mapImagePath = savedImagePath) else record
        } else {
            record
        }

        records.add(finalRecord)
    }

    override fun onCleared() {
        super.onCleared()
        autoCaptureJob?.cancel()
    }

    fun recordCurrentLocation(context: Context, onComplete: (() -> Unit)? = null) {
        if (isLoading) return
        isLoading = true
        statusMessage = "Acquiring fine location..."
        LocationHelper.fetchCurrentFineLocation(
            context = context,
            onSuccess = { record ->
                if (showMapImage) {
                    statusMessage = "Pulling map picture..."
                    viewModelScope.launch {
                        val savedImagePath = try {
                            MapHelper.downloadMapImage(
                                context = context,
                                lat = record.latitude,
                                lon = record.longitude,
                                recordId = record.id
                            )
                        } catch (_: Exception) {
                            null
                        }

                        val finalRecord = if (savedImagePath != null) {
                            record.copy(mapImagePath = savedImagePath)
                        } else {
                            record
                        }
                        records.add(finalRecord)
                        isLoading = false
                        statusMessage = null
                        onComplete?.invoke()
                    }
                } else {
                    records.add(record)
                    isLoading = false
                    statusMessage = null
                    onComplete?.invoke()
                }
            },
            onError = { error ->
                isLoading = false
                statusMessage = null
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        )
    }

    fun saveAndStartNewJourney(context: Context) {
        val savedPath = LocationHelper.saveCurrentJourney(context)
        records.clear()
        if (savedPath != null) {
            Toast.makeText(context, "Saved to $savedPath\nStarting new journey.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Started new journey.", Toast.LENGTH_SHORT).show()
        }
    }

    fun discardAndStartNewJourney(context: Context) {
        LocationHelper.discardCurrentJourney(context)
        records.clear()
        Toast.makeText(context, "Journey discarded. Starting new journey.", Toast.LENGTH_SHORT).show()
    }

    fun textMostRecentLocation(context: Context, phoneNumber: String = SmsHelper.TARGET_PHONE_NUMBER) {
        val lastRecord = records.lastOrNull()
        if (lastRecord == null) {
            statusMessage = "Acquiring location to text..."
            LocationHelper.fetchCurrentFineLocation(
                context = context,
                onSuccess = { newRecord ->
                    if (showMapImage) {
                        statusMessage = "Downloading map..."
                        viewModelScope.launch {
                            val savedImagePath = try {
                                MapHelper.downloadMapImage(context, newRecord.latitude, newRecord.longitude, newRecord.id)
                            } catch (_: Exception) { null }
                            val finalRecord = if (savedImagePath != null) newRecord.copy(mapImagePath = savedImagePath) else newRecord
                            records.add(finalRecord)
                            statusMessage = null
                            SmsHelper.textLocation(context, finalRecord, phoneNumber)
                        }
                    } else {
                        records.add(newRecord)
                        statusMessage = null
                        SmsHelper.textLocation(context, newRecord, phoneNumber)
                    }
                },
                onError = { err ->
                    statusMessage = null
                    Toast.makeText(context, "Location unavailable: $err", Toast.LENGTH_LONG).show()
                }
            )
        } else {
            SmsHelper.textLocation(context, lastRecord, phoneNumber)
        }
    }
}
