package com.example.footprints

data class LocationRecord(
    val id: Long = System.currentTimeMillis(),
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 5.0f,
    val altitude: Double? = null,
    val provider: String = "gps",
    val mapImagePath: String? = null
) {
    fun toFormattedLog(): String = buildString {
        append("[$timestamp]\n")
        append("  Lat: %+.6f, Lon: %+.6f\n".format(latitude, longitude))
        append("  Accuracy: ±%.1fm".format(accuracy))
        if (altitude != null && altitude != 0.0) {
            append(", Alt: %.1fm".format(altitude))
        }
        append(" (provider: $provider)")
    }

    fun toCsvRow(): String {
        return "\"$timestamp\",%.6f,%.6f".format(java.util.Locale.US, latitude, longitude)
    }
}
