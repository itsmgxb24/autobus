package pl.walbrzych.autobus.widget

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import pl.walbrzych.autobus.data.RealTimeDeparture

/** Wording shared by all home-screen widgets; screen-specific labels stay untouched. */
internal fun widgetScheduledDepartureLabel(
    scheduledAt: LocalDateTime,
    now: LocalDateTime,
): String = buildString {
    append("Odjazd: ")
    append(scheduledAt.format(TIME_FORMAT))
    if (scheduledAt.toLocalDate() == now.toLocalDate().plusDays(1)) {
        append(", jutro")
    }
}

/**
 * A live result is still preferred when the server supplies an ETA.  When it
 * only supplies a clock time, use the scheduled date to retain the important
 * next-day context.
 */
internal fun widgetDepartureLabel(
    departure: RealTimeDeparture,
    scheduledAt: LocalDateTime?,
    now: LocalDateTime,
): String = departure.etaMinutes?.let { "Odjazd za $it min" }
    ?: scheduledAt?.let { widgetScheduledDepartureLabel(it, now) }
    ?: "Odjazd: ${departure.displayValue.trim()}"

internal fun widgetDepartureTimeLabel(
    scheduledAt: LocalDateTime,
    now: LocalDateTime,
): String = buildString {
    append(scheduledAt.format(TIME_FORMAT))
    if (scheduledAt.toLocalDate() == now.toLocalDate().plusDays(1)) {
        append(", jutro")
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
