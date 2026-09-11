package com.uploadgo.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Small formatting helpers used across the UI. */
object Format {

    /** Human-readable byte size, e.g. "4.2 MB". */
    fun bytes(size: Long): String {
        if (size < 0) return "—"
        if (size < 1024) return "$size B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = size.toDouble()
        var unit = "B"
        for (u in units) {
            value /= 1024.0
            if (value < 1024.0 || u == units.last()) {
                unit = u
                break
            }
        }
        return String.format(Locale.US, "%.1f %s", value, unit)
    }

    /** Short wall-clock time such as "10:42 PM". */
    fun time(timestamp: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

    /** Day bucket label for grouping history entries. */
    fun dayGroup(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val now = Calendar.getInstance()
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return when {
            isSameDay(cal, now) -> "today"
            isSameDay(cal, yesterday) -> "yesterday"
            else -> fmt.format(cal.time)
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
