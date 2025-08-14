package com.example.buzztimer.model

/**
 * Represents a time interval for the timer
 * @param minutes The number of minutes
 * @param seconds The number of seconds
 * @param name Optional name for the interval
 */
data class TimerInterval(
    val minutes: Int,
    val seconds: Int,
    val name: String? = null
) {
    /**
     * Returns the total time in milliseconds
     */
    fun getTotalTimeMillis(): Long = ((minutes * 60) + seconds) * 1000L

    /**
     * Returns a formatted string representation of the interval
     * If a name is provided, it includes the name in the display string
     */
    fun getDisplayString(): String {
        val timeString = "${minutes}m ${seconds}s"
        return if (name.isNullOrBlank()) {
            timeString
        } else {
            "$name ($timeString)"
        }
    }
}
