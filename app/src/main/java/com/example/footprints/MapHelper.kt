package com.example.footprints

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.tan

object MapHelper {
    /**
     * Optional Google Static Maps API key.
     * If set, Google Static Maps API will be used.
     * If null or empty, it uses high-detail OpenStreetMap tile imagery (no key needed).
     */
    var googleMapsApiKey: String? = null

    fun getApiKey(context: Context): String? {
        val prefs = context.getSharedPreferences("footprints_prefs", Context.MODE_PRIVATE)
        return prefs.getString("google_maps_api_key", null) ?: googleMapsApiKey
    }

    fun setApiKey(context: Context, key: String?) {
        googleMapsApiKey = key
        val prefs = context.getSharedPreferences("footprints_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("google_maps_api_key", key).apply()
    }

    fun getGoogleStaticMapUrl(lat: Double, lon: Double, apiKey: String): String {
        // High-contrast dark theme matching Google Maps dark style
        val darkStyle = "&style=element:geometry%7Ccolor:0x242f3e" +
                "&style=element:labels.text.stroke%7Ccolor:0x242f3e" +
                "&style=element:labels.text.fill%7Ccolor:0x746855" +
                "&style=feature:poi%7Celement:labels.text.fill%7Ccolor:0xd59563" +
                "&style=feature:poi.park%7Celement:geometry%7Ccolor:0x263c3f" +
                "&style=feature:road%7Celement:geometry%7Ccolor:0x38414e" +
                "&style=feature:road%7Celement:geometry.stroke%7Ccolor:0x212a37" +
                "&style=feature:road.highway%7Celement:geometry%7Ccolor:0x746855" +
                "&style=feature:water%7Celement:geometry%7Ccolor:0x17263c"
        return "https://maps.googleapis.com/maps/api/staticmap?center=$lat,$lon&zoom=16&size=600x320&scale=2&maptype=roadmap&markers=color:red%7C$lat,$lon$darkStyle&key=$apiKey"
    }

    data class TileFraction(val x: Float, val y: Float)

    fun getTileUrl(lat: Double, lon: Double, zoom: Int = 16): String {
        val n = 1 shl zoom
        val x = (((lon + 180.0) / 360.0 * n).toInt()).coerceIn(0, n - 1)
        val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        val y = (((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n).toInt()).coerceIn(0, n - 1)
        return "https://tile.openstreetmap.org/$zoom/$x/$y.png"
    }

    fun getTileFraction(lat: Double, lon: Double, zoom: Int = 16): TileFraction {
        val n = 1 shl zoom
        val fx = ((lon + 180.0) / 360.0 * n).coerceIn(0.0, (n - 1).toDouble())
        val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        val fy = (((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n)).coerceIn(0.0, (n - 1).toDouble())
        val fracX = (fx - fx.toInt().toDouble()).toFloat().coerceIn(0.08f, 0.92f)
        val fracY = (fy - fy.toInt().toDouble()).toFloat().coerceIn(0.08f, 0.92f)
        return TileFraction(fracX, fracY)
    }

    fun getMapImageUrl(context: Context? = null, lat: Double, lon: Double): String {
        val key = if (context != null) getApiKey(context) else googleMapsApiKey
        return if (!key.isNullOrBlank()) {
            getGoogleStaticMapUrl(lat, lon, key)
        } else {
            getTileUrl(lat, lon, zoom = 16)
        }
    }

    /**
     * Actively downloads the map image for a GPS capture and saves it to local files.
     * Returns the absolute path of the saved image file, or null if network/fetch fails.
     */
    suspend fun downloadMapImage(
        context: Context,
        lat: Double,
        lon: Double,
        recordId: Long
    ): String? = withContext(Dispatchers.IO) {
        try {
            val urlString = getMapImageUrl(context, lat, lon)
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "FootprintsApp/1.0 (Android)")
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
            }

            if (connection.responseCode in 200..299) {
                val mapsDir = File(context.filesDir, "maps").apply { if (!exists()) mkdirs() }
                val targetFile = File(mapsDir, "map_${recordId}.png")
                connection.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.absolutePath
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
