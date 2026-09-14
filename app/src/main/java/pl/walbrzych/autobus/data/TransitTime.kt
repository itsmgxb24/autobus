package pl.walbrzych.autobus.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The timetable service and every displayed transit time use the operator's Polish time zone. */
object TransitTime {
    val zone: ZoneId = ZoneId.of("Europe/Warsaw")

    fun now(): LocalDateTime = LocalDateTime.now(zone)

    fun Instant.inTransitZone(): ZonedDateTime = atZone(zone)
}
