package com.example.locationlogger

data class LocationRecord(
    val id: Long = System.currentTimeMillis(),
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double?,
    val provider: String
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
}
