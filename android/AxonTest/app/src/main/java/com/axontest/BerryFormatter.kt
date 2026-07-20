package com.vectr

object BerryFormatter {
    fun format(value: Int): String {
        val short = if (kotlin.math.abs(value) < 1000) value.toString() else {
            val number = value / 1000.0
            if (number % 1.0 == 0.0) number.toInt().toString() else "%.1f".format(java.util.Locale.US, number)
        }
        return "$short${if (kotlin.math.abs(value) >= 1000) "K" else ""} Berries"
    }
}
