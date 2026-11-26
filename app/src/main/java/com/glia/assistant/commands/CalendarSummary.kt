package com.glia.assistant.commands

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class CalendarEvent(
    val title: String,
    val beginMs: Long,
    val allDay: Boolean
)

/** Formata compromissos em fala natural, sem depender de Android. */
object CalendarSummary {
    fun format(
        events: List<CalendarEvent>,
        daysAhead: Int,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val startOfDay = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val upcoming = events
            .filter { event ->
                if (event.allDay) event.beginMs >= startOfDay
                else event.beginMs >= nowMs - 60_000L
            }
            .sortedBy { it.beginMs }

        val period = when {
            daysAhead <= 1 -> "hoje"
            daysAhead == 2 -> "amanh\u00e3"
            else -> "nos pr\u00f3ximos $daysAhead dias"
        }

        if (upcoming.isEmpty()) return "Voc\u00ea n\u00e3o tem compromissos $period."

        val shown = upcoming.take(3)
        val parts = shown.map { event ->
            if (event.allDay) {
                "o dia todo, ${event.title}"
            } else {
                "\u00e0s ${speakTime(event.beginMs, zone)}, ${event.title}"
            }
        }
        val total = if (upcoming.size == 1) "1 compromisso" else "${upcoming.size} compromissos"
        val extra = if (upcoming.size > shown.size) " Os primeiros s\u00e3o: " else " "
        return "Voc\u00ea tem $total $period.$extra" + parts.joinToString("; ") + "."
    }

    fun speakTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = LocalTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
        return if (time.minute == 0) {
            "${time.hour}h"
        } else {
            "${time.hour}h" + time.minute.toString().padStart(2, '0')
        }
    }
}
