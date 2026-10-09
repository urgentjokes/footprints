package com.example.locationlogger

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocationHelper {

    private const val LOG_FILE_NAME = "location_log.txt"

    fun getLogFile(context: Context): File = File(context.filesDir, LOG_FILE_NAME)

    fun appendToLogFile(context: Context, record: LocationRecord) {
        try {
            val file = getLogFile(context)
            file.appendText(record.toFormattedLog() + "\n\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun readLogsFromFile(context: Context): List<String> {
        val file = getLogFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText().trim()
            if (text.isEmpty()) emptyList() else text.split("\n\n").filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun fetchCurrentFineLocation(
        context: Context,
        onSuccess: (LocationRecord) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!hasLocationPermission(context)) {
            onError("Fine location permission not granted")
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            onError("LocationManager service not available")
            return
        }

        val allProviders = locationManager.allProviders
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val hasFused = allProviders.contains(LocationManager.FUSED_PROVIDER)

        val preferredProvider = when {
            hasFused -> LocationManager.FUSED_PROVIDER
            isGpsEnabled -> LocationManager.GPS_PROVIDER
            isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.PASSIVE_PROVIDER
        }

        var completed = false
        val handler = Handler(Looper.getMainLooper())

        fun handleLocation(loc: Location) {
            if (completed) return
            completed = true

            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val timestampStr = timeFormat.format(Date(loc.time.takeIf { it > 0 } ?: System.currentTimeMillis()))

            val record = LocationRecord(
                timestamp = timestampStr,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                altitude = if (loc.hasAltitude()) loc.altitude else null,
                provider = loc.provider ?: preferredProvider
            )
            appendToLogFile(context, record)
            onSuccess(record)
        }

        val fallbackRunnable = Runnable {
            if (!completed) {
                // Try last known location
                val lastKnown = try {
                    (if (hasFused) locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER) else null)
                        ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } catch (e: SecurityException) {
                    null
                }

                if (lastKnown != null) {
                    handleLocation(lastKnown)
                } else {
                    completed = true
                    onError("Location timeout: unable to obtain a location fix")
                }
            }
        }

        // Set 4-second timeout to fall back to last known location if hardware takes too long
        handler.postDelayed(fallbackRunnable, 4000)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellationSignal = CancellationSignal()
                locationManager.getCurrentLocation(
                    preferredProvider,
                    cancellationSignal,
                    context.mainExecutor
                ) { loc ->
                    handler.removeCallbacks(fallbackRunnable)
                    if (loc != null) {
                        handleLocation(loc)
                    } else {
                        fallbackRunnable.run()
                    }
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        handler.removeCallbacks(fallbackRunnable)
                        locationManager.removeUpdates(this)
                        handleLocation(loc)
                    }
                }
                locationManager.requestLocationUpdates(
                    preferredProvider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        } catch (e: SecurityException) {
            handler.removeCallbacks(fallbackRunnable)
            onError("Security exception: ${e.message}")
        } catch (e: Exception) {
            handler.removeCallbacks(fallbackRunnable)
            fallbackRunnable.run()
        }
    }
}
