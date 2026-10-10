package com.example.footprints

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
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocationHelper {

    private const val LOG_FILE_NAME = "location_log.txt"
    private const val CSV_LOG_FILE_NAME = "location_log.csv"
    private const val CSV_HEADER = "timestamp,latitude,longitude\n"
    const val DOCUMENTS_SUBFOLDER = "Footprints log"

    fun getLogFile(context: Context): File = File(context.filesDir, LOG_FILE_NAME)
    fun getCsvLogFile(context: Context): File = File(context.filesDir, CSV_LOG_FILE_NAME)

    fun ensureDocumentsFolderExists(context: Context) {
        try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val targetDir = File(docsDir, DOCUMENTS_SUBFOLDER)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            // Migrate any legacy "Footprints logs" folder to "Footprints log"
            val legacyDir = File(docsDir, "Footprints logs")
            if (legacyDir.exists() && legacyDir.isDirectory) {
                legacyDir.listFiles()?.forEach { file ->
                    val dest = File(targetDir, file.name)
                    if (!dest.exists()) {
                        file.copyTo(dest, overwrite = true)
                    }
                    file.delete()
                }
                legacyDir.delete()
            }

            // Move any loose journey files in root Documents into the subfolder
            docsDir.listFiles { file -> file.isFile && file.name.startsWith("journey_") && file.name.endsWith(".csv") }?.forEach { looseFile ->
                val dest = File(targetDir, looseFile.name)
                if (!dest.exists()) {
                    looseFile.copyTo(dest, overwrite = true)
                }
                looseFile.delete()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val prefs = context.getSharedPreferences("footprints_storage", Context.MODE_PRIVATE)
                val initialized = prefs.getBoolean("folder_initialized_v2", false)
                if (!initialized) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "README.txt")
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$DOCUMENTS_SUBFOLDER/")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write("Footprints journey CSV logs are saved in this folder.\n".toByteArray())
                        }
                    }
                    prefs.edit().putBoolean("folder_initialized_v2", true).apply()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun appendToLogFile(context: Context, record: LocationRecord) {
        try {
            val file = getLogFile(context)
            file.appendText(record.toFormattedLog() + "\n\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun appendToCsvFile(context: Context, record: LocationRecord) {
        try {
            val file = getCsvLogFile(context)
            if (!file.exists() || file.length() == 0L) {
                file.writeText(CSV_HEADER)
            }
            file.appendText(record.toCsvRow() + "\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Saves the current CSV journey log to Android Documents/Footprints log folder.
     * Returns the relative or display path of the saved file, or null if no data to save.
     */
    fun saveCurrentJourney(context: Context): String? {
        val currentFile = getCsvLogFile(context)
        if (!currentFile.exists() || currentFile.length() == 0L) return null

        val csvContent = currentFile.readText()
        val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val stamp = timeFormat.format(Date())
        val fileName = "journey_$stamp.csv"
        var savedPathDisplay: String? = null

        // 1. Save to public Documents/Footprints log via MediaStore (API 29+) with trailing slash
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$DOCUMENTS_SUBFOLDER/")
                }
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(csvContent.toByteArray())
                    }
                    savedPathDisplay = "Documents/$DOCUMENTS_SUBFOLDER/$fileName"
                }
            }

            // Also guarantee directory and file existence via File API
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val targetDir = File(docsDir, DOCUMENTS_SUBFOLDER).apply { if (!exists()) mkdirs() }
            val targetFile = File(targetDir, fileName)
            if (!targetFile.exists() || targetFile.length() == 0L) {
                targetFile.writeText(csvContent)
            }
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf("text/csv"),
                null
            )
            if (savedPathDisplay == null) {
                savedPathDisplay = "Documents/$DOCUMENTS_SUBFOLDER/$fileName"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Also keep internal backup in app-specific files directory
        try {
            val archiveDir = File(context.filesDir, "saved_journeys").apply { if (!exists()) mkdirs() }
            val backupFile = File(archiveDir, fileName)
            backupFile.writeText(csvContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Reset active journey files
        currentFile.delete()
        val textFile = getLogFile(context)
        if (textFile.exists()) textFile.delete()

        return savedPathDisplay ?: "Documents/$DOCUMENTS_SUBFOLDER/$fileName"
    }

    fun discardCurrentJourney(context: Context) {
        val currentFile = getCsvLogFile(context)
        if (currentFile.exists()) currentFile.delete()
        val textFile = getLogFile(context)
        if (textFile.exists()) textFile.delete()
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
            val timestampStr = timeFormat.format(Date())

            val record = LocationRecord(
                timestamp = timestampStr,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                altitude = if (loc.hasAltitude()) loc.altitude else null,
                provider = loc.provider ?: preferredProvider
            )
            appendToLogFile(context, record)
            appendToCsvFile(context, record)
            onSuccess(record)
        }

        val fallbackRunnable = Runnable {
            if (!completed) {
                // Try last known location across all available providers, prioritizing fused
                val lastKnown = try {
                    (if (hasFused) locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER) else null)
                        ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
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
