package com.glia.assistant.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/** Le os proximos compromissos do calendario do usuario (precisa de READ_CALENDAR). */
class CalendarReader(context: Context) {
    private val appContext = context.applicationContext

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun upcoming(daysAhead: Int): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val now = System.currentTimeMillis()
        val end = now + daysAhead.coerceIn(1, 31) * DAY_MS
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(end.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
        )
        val events = ArrayList<CalendarEvent>()
        runCatching {
            appContext.contentResolver.query(
                uri,
                projection,
                CalendarContract.Instances.VISIBLE + " = 1",
                null,
                CalendarContract.Instances.BEGIN + " ASC",
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                while (cursor.moveToNext() && events.size < MAX_EVENTS) {
                    val title = cursor.getString(titleIdx)?.trim().orEmpty()
                    if (title.isEmpty()) continue
                    events.add(
                        CalendarEvent(
                            title = title,
                            beginMs = cursor.getLong(beginIdx),
                            allDay = cursor.getInt(allDayIdx) == 1,
                        )
                    )
                }
            }
        }
        return events
    }

    fun summary(daysAhead: Int): CalendarAnswer {
        if (!hasPermission()) {
            return CalendarAnswer(
                spoken = "Preciso da permissao de calendario para consultar seus compromissos. " +
                    "Abra o Glia e permita o acesso ao calendario.",
                hasPermission = false,
            )
        }
        val events = upcoming(daysAhead)
        return CalendarAnswer(
            spoken = CalendarSummary.format(
                events = events,
                daysAhead = daysAhead,
                nowMs = System.currentTimeMillis(),
            ),
            hasPermission = true,
        )
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val MAX_EVENTS = 20
    }
}

data class CalendarAnswer(val spoken: String, val hasPermission: Boolean)
